-- =============================================================================
-- V1 : schéma initial de la plateforme de réservation médicale
-- Référence : docs/modele_donnees_reservation.docx
--
-- Rappel Flyway : une fois appliquée sur une base, cette migration ne doit plus
-- être modifiée (Flyway vérifie son empreinte). Toute évolution passe par un V2.
-- =============================================================================

-- Permet de combiner "=" (sur un BIGINT) et "&&" (chevauchement d'intervalles)
-- dans une même contrainte d'exclusion GiST. Fournie avec PostgreSQL (contrib).
CREATE EXTENSION IF NOT EXISTS btree_gist;


-- -----------------------------------------------------------------------------
-- Types énumérés
-- La base refuse toute valeur hors liste, quel que soit le client qui écrit.
-- -----------------------------------------------------------------------------
CREATE TYPE role_utilisateur AS ENUM ('PATIENT', 'PRATICIEN');

CREATE TYPE modalite AS ENUM ('PRESENTIEL', 'DISTANCIEL');

-- Seul CONFIRME bloque l'agenda (voir les contraintes d'exclusion de rendez_vous).
CREATE TYPE statut_rdv AS ENUM ('CONFIRME', 'ANNULE_PATIENT', 'ANNULE_PRATICIEN', 'HONORE', 'ABSENT');


-- =============================================================================
-- 4.1 Comptes et identité
-- =============================================================================

-- Socle commun aux patients et aux praticiens. Le profil et le hash du mot de
-- passe sont des colonnes : relation 1–1, une table séparée n'apporterait
-- qu'une jointure.
CREATE TABLE utilisateur (
    id                BIGSERIAL        PRIMARY KEY,
    email             VARCHAR(255)     NOT NULL,
    mot_de_passe_hash VARCHAR(255)     NOT NULL,   -- bcrypt/argon2, jamais le mot de passe en clair
    role              role_utilisateur NOT NULL,
    nom               VARCHAR(100)     NOT NULL,
    prenom            VARCHAR(100)     NOT NULL,
    telephone         VARCHAR(20),
    actif             BOOLEAN          NOT NULL DEFAULT true,   -- désactivation plutôt que suppression
    cree_le           TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT uq_utilisateur_email UNIQUE (email),
    -- Redondant avec la clé primaire, mais nécessaire : une clé étrangère composée
    -- (utilisateur_id, role) ne peut viser qu'un couple de colonnes déclaré unique.
    -- Utilisé par patient et praticien pour vérifier le rôle (voir plus bas).
    CONSTRAINT uq_utilisateur_id_role UNIQUE (id, role)
);

-- 1–N : un utilisateur peut être connecté depuis plusieurs appareils.
-- On stocke le hash du jeton : une fuite de la table ne donne pas de sessions valides.
CREATE TABLE session (
    id             BIGSERIAL    PRIMARY KEY,
    utilisateur_id BIGINT       NOT NULL,
    token_hash     VARCHAR(255) NOT NULL,
    expire_le      TIMESTAMPTZ  NOT NULL,
    cree_le        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_session_token_hash UNIQUE (token_hash),
    -- Une session n'a aucun sens sans son utilisateur : suppression en cascade.
    CONSTRAINT fk_session_utilisateur
        FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE CASCADE
);

-- Héritage : la clé primaire est aussi la clé étrangère vers utilisateur
-- (côté JPA : @Inheritance(strategy = JOINED)).
--
-- Cohérence avec utilisateur.role, garantie sans trigger :
--   - la colonne "role" est figée à 'PATIENT' par le CHECK ;
--   - la clé étrangère composée (utilisateur_id, role) exige donc que
--     l'utilisateur référencé ait le rôle PATIENT.
-- Effets : un PRATICIEN ne peut pas avoir de ligne patient, un utilisateur ne
-- peut pas être les deux, et son rôle ne peut plus changer une fois spécialisé.
-- Le DEFAULT permet à Hibernate d'ignorer cette colonne : il n'a pas à la mapper.
CREATE TABLE patient (
    utilisateur_id BIGINT           PRIMARY KEY,
    role           role_utilisateur NOT NULL DEFAULT 'PATIENT',
    date_naissance DATE             NOT NULL,

    CONSTRAINT chk_patient_role CHECK (role = 'PATIENT'),
    CONSTRAINT fk_patient_utilisateur
        FOREIGN KEY (utilisateur_id, role) REFERENCES utilisateur (id, role)
);

-- Même mécanisme que patient, avec le rôle PRATICIEN.
CREATE TABLE praticien (
    utilisateur_id BIGINT           PRIMARY KEY,
    role           role_utilisateur NOT NULL DEFAULT 'PRATICIEN',
    numero_rpps    CHAR(11)         NOT NULL,   -- identifiant national, toujours 11 chiffres
    presentation   TEXT,

    CONSTRAINT uq_praticien_numero_rpps UNIQUE (numero_rpps),
    CONSTRAINT chk_praticien_role CHECK (role = 'PRATICIEN'),
    CONSTRAINT fk_praticien_utilisateur
        FOREIGN KEY (utilisateur_id, role) REFERENCES utilisateur (id, role)
);


-- =============================================================================
-- 4.2 Référentiels du praticien
-- =============================================================================

-- Référentiel partagé : évite les variantes d'écriture et permet la recherche.
CREATE TABLE specialite (
    id      SERIAL       PRIMARY KEY,
    libelle VARCHAR(100) NOT NULL,

    CONSTRAINT uq_specialite_libelle UNIQUE (libelle)
);

-- Table de liaison N–N. La clé primaire composée interdit les doublons.
CREATE TABLE praticien_specialite (
    praticien_id  BIGINT NOT NULL,
    specialite_id INT    NOT NULL,

    CONSTRAINT pk_praticien_specialite PRIMARY KEY (praticien_id, specialite_id),
    CONSTRAINT fk_praticien_specialite_praticien
        FOREIGN KEY (praticien_id) REFERENCES praticien (utilisateur_id) ON DELETE CASCADE,
    CONSTRAINT fk_praticien_specialite_specialite
        FOREIGN KEY (specialite_id) REFERENCES specialite (id)
);

-- Cabinet ou centre de santé, potentiellement partagé par plusieurs praticiens.
CREATE TABLE lieu (
    id          BIGSERIAL    PRIMARY KEY,
    nom         VARCHAR(150) NOT NULL,
    adresse     VARCHAR(255) NOT NULL,
    code_postal CHAR(5)      NOT NULL,
    ville       VARCHAR(100) NOT NULL
);

-- Propre à chaque praticien. Sa durée détermine la taille des créneaux.
-- "actif" permet de retirer un motif sans casser les rendez-vous qui le référencent.
CREATE TABLE motif (
    id                  BIGSERIAL    PRIMARY KEY,
    praticien_id        BIGINT       NOT NULL,
    libelle             VARCHAR(150) NOT NULL,
    duree_minutes       SMALLINT     NOT NULL,
    distanciel_possible BOOLEAN      NOT NULL DEFAULT false,
    actif               BOOLEAN      NOT NULL DEFAULT true,

    CONSTRAINT chk_motif_duree_positive CHECK (duree_minutes > 0),
    -- Cible de la clé étrangère composée de rendez_vous : garantit qu'un
    -- rendez-vous utilise un motif du même praticien.
    CONSTRAINT uq_motif_id_praticien UNIQUE (id, praticien_id),
    CONSTRAINT fk_motif_praticien
        FOREIGN KEY (praticien_id) REFERENCES praticien (utilisateur_id)
);


-- =============================================================================
-- 4.3 Agenda
-- =============================================================================

-- Planning récurrent. Ex. : le mardi de 9 h à 12 h au cabinet des Ternes.
-- Les créneaux libres n'existent pas en base : ils sont calculés à partir
-- de cette table, des absences et des rendez-vous.
CREATE TABLE plage_horaire (
    id           BIGSERIAL PRIMARY KEY,
    praticien_id BIGINT    NOT NULL,
    jour_semaine SMALLINT  NOT NULL,   -- ISO 8601 : 1 = lundi ... 7 = dimanche (= DayOfWeek.getValue())
    heure_debut  TIME      NOT NULL,
    heure_fin    TIME      NOT NULL,
    modalite     modalite  NOT NULL,
    lieu_id      BIGINT,
    valide_du    DATE      NOT NULL,
    valide_au    DATE,                 -- NULL = sans limite

    CONSTRAINT chk_plage_horaire_jour CHECK (jour_semaine BETWEEN 1 AND 7),
    CONSTRAINT chk_plage_horaire_heures CHECK (heure_fin > heure_debut),
    -- Une plage peut ne durer qu'un jour (valide_du = valide_au).
    CONSTRAINT chk_plage_horaire_validite CHECK (valide_au IS NULL OR valide_au >= valide_du),
    -- Un lieu en présentiel, aucun en distanciel.
    CONSTRAINT chk_plage_horaire_modalite_lieu CHECK (
        (modalite = 'PRESENTIEL' AND lieu_id IS NOT NULL)
        OR (modalite = 'DISTANCIEL' AND lieu_id IS NULL)
    ),
    CONSTRAINT fk_plage_horaire_praticien
        FOREIGN KEY (praticien_id) REFERENCES praticien (utilisateur_id),
    CONSTRAINT fk_plage_horaire_lieu
        FOREIGN KEY (lieu_id) REFERENCES lieu (id)
);

-- Exceptions au planning : congés, formation, fermeture ponctuelle.
CREATE TABLE absence (
    id           BIGSERIAL    PRIMARY KEY,
    praticien_id BIGINT       NOT NULL,
    debut        TIMESTAMPTZ  NOT NULL,
    fin          TIMESTAMPTZ  NOT NULL,
    raison       VARCHAR(255),          -- usage interne, non visible du patient

    CONSTRAINT chk_absence_intervalle CHECK (fin > debut),
    CONSTRAINT fk_absence_praticien
        FOREIGN KEY (praticien_id) REFERENCES praticien (utilisateur_id)
);

-- Entité centrale. Jamais supprimée : une annulation change le statut.
-- "fin" est copiée à la réservation (début + durée du motif) : si le praticien
-- modifie ensuite la durée du motif, les rendez-vous déjà pris ne bougent pas,
-- et c'est ce qui rend possible la contrainte d'exclusion ci-dessous.
CREATE TABLE rendez_vous (
    id           BIGSERIAL   PRIMARY KEY,
    patient_id   BIGINT      NOT NULL,
    praticien_id BIGINT      NOT NULL,
    motif_id     BIGINT      NOT NULL,
    modalite     modalite    NOT NULL,
    lieu_id      BIGINT,
    debut        TIMESTAMPTZ NOT NULL,
    fin          TIMESTAMPTZ NOT NULL,
    statut       statut_rdv  NOT NULL DEFAULT 'CONFIRME',
    note_patient TEXT,
    cree_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
    annule_le    TIMESTAMPTZ,

    CONSTRAINT chk_rendez_vous_intervalle CHECK (fin > debut),
    CONSTRAINT chk_rendez_vous_modalite_lieu CHECK (
        (modalite = 'PRESENTIEL' AND lieu_id IS NOT NULL)
        OR (modalite = 'DISTANCIEL' AND lieu_id IS NULL)
    ),
    -- annule_le est renseigné si et seulement si le statut est une annulation.
    CONSTRAINT chk_rendez_vous_annulation CHECK (
        (statut IN ('ANNULE_PATIENT', 'ANNULE_PRATICIEN')) = (annule_le IS NOT NULL)
    ),

    -- Pas de double réservation pour un praticien : deux rendez-vous CONFIRME
    -- d'un même praticien ne peuvent pas se chevaucher.
    -- '[)' : début inclus, fin exclue -> 10:00–10:30 et 10:30–11:00 sont compatibles.
    -- Une violation lève le SQLSTATE 23P01 (exclusion_violation) avec le nom de
    -- la contrainte : c'est ce que l'API traduira en HTTP 409.
    CONSTRAINT excl_rendez_vous_praticien_chevauchement
        EXCLUDE USING gist (
            praticien_id                WITH =,
            tstzrange(debut, fin, '[)') WITH &&
        ) WHERE (statut = 'CONFIRME'),

    -- Même principe côté patient : il ne peut pas être à deux rendez-vous à la fois.
    CONSTRAINT excl_rendez_vous_patient_chevauchement
        EXCLUDE USING gist (
            patient_id                  WITH =,
            tstzrange(debut, fin, '[)') WITH &&
        ) WHERE (statut = 'CONFIRME'),

    -- Pas de ON DELETE CASCADE : supprimer un patient, un praticien ou un motif
    -- ne doit jamais effacer l'historique des rendez-vous.
    CONSTRAINT fk_rendez_vous_patient
        FOREIGN KEY (patient_id) REFERENCES patient (utilisateur_id),
    CONSTRAINT fk_rendez_vous_praticien
        FOREIGN KEY (praticien_id) REFERENCES praticien (utilisateur_id),
    -- Clé composée : le motif doit appartenir au praticien du rendez-vous.
    CONSTRAINT fk_rendez_vous_motif
        FOREIGN KEY (motif_id, praticien_id) REFERENCES motif (id, praticien_id),
    CONSTRAINT fk_rendez_vous_lieu
        FOREIGN KEY (lieu_id) REFERENCES lieu (id)
);


-- =============================================================================
-- Index
-- PostgreSQL indexe automatiquement les PRIMARY KEY, UNIQUE et EXCLUDE,
-- mais PAS les clés étrangères : on ajoute ceux qui servent aux requêtes courantes.
-- =============================================================================

-- Agenda d'un praticien / historique d'un patient, triés par date.
CREATE INDEX idx_rendez_vous_praticien_debut ON rendez_vous (praticien_id, debut);
CREATE INDEX idx_rendez_vous_patient_debut   ON rendez_vous (patient_id, debut);

-- Chargement du planning d'un praticien pour le calcul des disponibilités.
CREATE INDEX idx_plage_horaire_praticien_jour ON plage_horaire (praticien_id, jour_semaine);
CREATE INDEX idx_absence_praticien_debut      ON absence (praticien_id, debut);

-- Motifs proposés par un praticien.
CREATE INDEX idx_motif_praticien ON motif (praticien_id);

-- Sessions d'un utilisateur (déconnexion de tous les appareils).
CREATE INDEX idx_session_utilisateur ON session (utilisateur_id);

-- Recherche "praticiens de telle spécialité" : la clé primaire (praticien_id, specialite_id)
-- ne sert pas quand on filtre sur specialite_id seul.
CREATE INDEX idx_praticien_specialite_specialite ON praticien_specialite (specialite_id);

-- Recherche géographique.
CREATE INDEX idx_lieu_code_postal ON lieu (code_postal);
