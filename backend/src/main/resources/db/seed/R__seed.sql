-- =============================================================================
-- Données de démonstration — chargées UNIQUEMENT avec le profil Spring "demo"
-- (voir application-demo.yml, qui ajoute db/seed aux emplacements Flyway).
--
-- Migration répétable (préfixe R__) : Flyway la rejoue chaque fois que ce fichier
-- change. Elle commence donc par tout vider, pour repartir d'un état connu :
-- ne jamais activer le profil demo sur une base contenant de vraies données.
--
-- Comptes : mot de passe "demo1234" pour tous (hash bcrypt ci-dessous).
-- =============================================================================

TRUNCATE rendez_vous, absence, plage_horaire, motif, praticien_specialite,
         specialite, lieu, session, patient, praticien, utilisateur
    RESTART IDENTITY;

-- Rendez-vous placés la semaine suivant le chargement du seed, pour qu'ils soient
-- dans le futur. Fonction temporaire (schéma pg_temp) : elle disparaît à la fin de
-- la connexion. day_offset : 0 = lundi ... 6 = dimanche ; heure locale de Paris.
CREATE FUNCTION pg_temp.next_week(day_offset int, local_time time) RETURNS timestamptz AS $$
    SELECT (date_trunc('week', now() AT TIME ZONE 'Europe/Paris')
            + make_interval(days => 7 + day_offset)
            + local_time::interval) AT TIME ZONE 'Europe/Paris'
$$ LANGUAGE sql;


-- -----------------------------------------------------------------------------
-- Référentiels
-- -----------------------------------------------------------------------------
INSERT INTO specialite (libelle) VALUES
    ('Médecine générale'), ('Dermatologie'), ('Kinésithérapie'), ('Pédiatrie');

INSERT INTO lieu (nom, adresse, code_postal, ville) VALUES
    ('Cabinet médical des Ternes', '12 rue Bayen', '75017', 'Paris'),        -- partagé
    ('Centre de kinésithérapie Bastille', '8 rue de la Roquette', '75011', 'Paris');


-- -----------------------------------------------------------------------------
-- Utilisateurs (les lignes patient / praticien vérifient le rôle par clé composée)
-- -----------------------------------------------------------------------------
INSERT INTO utilisateur (email, mot_de_passe_hash, role, nom, prenom, telephone) VALUES
    ('claire.martin@example.com', '$2a$10$Qm//gA283XhZQMdK2uW1yedhdysBoF6gQgvm1XUOXEOJhQMvNMvPO', 'PRATICIEN', 'Martin', 'Claire', '0142000001'),
    ('karim.benali@example.com',  '$2a$10$Qm//gA283XhZQMdK2uW1yedhdysBoF6gQgvm1XUOXEOJhQMvNMvPO', 'PRATICIEN', 'Benali', 'Karim',  '0142000002'),
    ('sophie.leroy@example.com',  '$2a$10$Qm//gA283XhZQMdK2uW1yedhdysBoF6gQgvm1XUOXEOJhQMvNMvPO', 'PRATICIEN', 'Leroy',  'Sophie', '0143000003'),
    ('jean.dupont@example.com',   '$2a$10$Qm//gA283XhZQMdK2uW1yedhdysBoF6gQgvm1XUOXEOJhQMvNMvPO', 'PATIENT',   'Dupont', 'Jean',   '0600000001'),
    ('amina.diallo@example.com',  '$2a$10$Qm//gA283XhZQMdK2uW1yedhdysBoF6gQgvm1XUOXEOJhQMvNMvPO', 'PATIENT',   'Diallo', 'Amina',  '0600000002');

INSERT INTO praticien (utilisateur_id, numero_rpps, presentation)
SELECT id, rpps, presentation
FROM utilisateur
JOIN (VALUES
    ('claire.martin@example.com', '10000000001', 'Médecin généraliste, suivi adulte et enfant, téléconsultation possible.'),
    ('karim.benali@example.com',  '10000000002', 'Dermatologue : dépistage, acné, suivi des grains de beauté.'),
    ('sophie.leroy@example.com',  '10000000003', 'Masseur-kinésithérapeute, rééducation du sport et post-opératoire.')
) AS p(email, rpps, presentation) USING (email);

INSERT INTO patient (utilisateur_id, date_naissance)
SELECT id, naissance::date
FROM utilisateur
JOIN (VALUES
    ('jean.dupont@example.com',  '1988-04-12'),
    ('amina.diallo@example.com', '1995-09-30')
) AS p(email, naissance) USING (email);

INSERT INTO praticien_specialite (praticien_id, specialite_id)
SELECT u.id, s.id
FROM (VALUES
    ('claire.martin@example.com', 'Médecine générale'),
    ('karim.benali@example.com',  'Dermatologie'),
    ('sophie.leroy@example.com',  'Kinésithérapie')
) AS ps(email, libelle)
JOIN utilisateur u ON u.email = ps.email
JOIN specialite s ON s.libelle = ps.libelle;


-- -----------------------------------------------------------------------------
-- Motifs
-- -----------------------------------------------------------------------------
INSERT INTO motif (praticien_id, libelle, duree_minutes, distanciel_possible)
SELECT u.id, m.libelle, m.duree, m.distanciel
FROM (VALUES
    ('claire.martin@example.com', 'Consultation',                 20, true),
    ('claire.martin@example.com', 'Première consultation',        30, false),
    ('claire.martin@example.com', 'Vaccination',                  15, false),
    ('karim.benali@example.com',  'Consultation dermatologique',  30, false),
    ('karim.benali@example.com',  'Contrôle des grains de beauté', 20, false),
    ('karim.benali@example.com',  'Suivi de traitement',          15, true),
    ('sophie.leroy@example.com',  'Séance de rééducation',        30, false),
    ('sophie.leroy@example.com',  'Bilan kinésithérapique',       45, false)
) AS m(email, libelle, duree, distanciel)
JOIN utilisateur u ON u.email = m.email;


-- -----------------------------------------------------------------------------
-- Planning (jour_semaine ISO : 1 = lundi)
-- -----------------------------------------------------------------------------
INSERT INTO plage_horaire (praticien_id, jour_semaine, heure_debut, heure_fin, modalite, lieu_id, valide_du)
SELECT u.id, p.jour, p.debut::time, p.fin::time, p.modalite::modalite, l.id, DATE '2026-01-01'
FROM (VALUES
    -- Dr Martin : présentiel lundi et jeudi matin, téléconsultation mardi après-midi
    ('claire.martin@example.com', 1, '09:00', '12:00', 'PRESENTIEL', 'Cabinet médical des Ternes'),
    ('claire.martin@example.com', 4, '09:00', '12:00', 'PRESENTIEL', 'Cabinet médical des Ternes'),
    ('claire.martin@example.com', 2, '14:00', '17:00', 'DISTANCIEL', NULL),
    -- Dr Benali : même cabinet (lieu partagé), mercredi et vendredi
    ('karim.benali@example.com',  3, '09:00', '13:00', 'PRESENTIEL', 'Cabinet médical des Ternes'),
    ('karim.benali@example.com',  5, '09:00', '12:00', 'PRESENTIEL', 'Cabinet médical des Ternes'),
    ('karim.benali@example.com',  5, '14:00', '16:00', 'DISTANCIEL', NULL),
    -- S. Leroy : journées complètes lundi et mercredi
    ('sophie.leroy@example.com',  1, '08:00', '12:00', 'PRESENTIEL', 'Centre de kinésithérapie Bastille'),
    ('sophie.leroy@example.com',  1, '14:00', '18:00', 'PRESENTIEL', 'Centre de kinésithérapie Bastille'),
    ('sophie.leroy@example.com',  3, '08:00', '12:00', 'PRESENTIEL', 'Centre de kinésithérapie Bastille'),
    ('sophie.leroy@example.com',  3, '14:00', '18:00', 'PRESENTIEL', 'Centre de kinésithérapie Bastille')
) AS p(email, jour, debut, fin, modalite, lieu)
JOIN utilisateur u ON u.email = p.email
LEFT JOIN lieu l ON l.nom = p.lieu;


-- -----------------------------------------------------------------------------
-- Absence : Dr Benali en formation le mercredi de la semaine prochaine
-- -----------------------------------------------------------------------------
INSERT INTO absence (praticien_id, debut, fin, raison)
SELECT id, pg_temp.next_week(2, '00:00'), pg_temp.next_week(3, '00:00'), 'Formation'
FROM utilisateur WHERE email = 'karim.benali@example.com';


-- -----------------------------------------------------------------------------
-- Rendez-vous de la semaine prochaine (fin = début + durée du motif)
-- -----------------------------------------------------------------------------
INSERT INTO rendez_vous (patient_id, praticien_id, motif_id, modalite, lieu_id, debut, fin, statut, annule_le)
SELECT pat.id, m.praticien_id, m.id, r.modalite::modalite, l.id,
       pg_temp.next_week(r.jour, r.heure::time),
       pg_temp.next_week(r.jour, r.heure::time) + make_interval(mins => m.duree_minutes),
       r.statut::statut_rdv,
       CASE WHEN r.statut LIKE 'ANNULE%' THEN now() END
FROM (VALUES
    -- patient,                  praticien,                  motif,                    j, heure,  modalité,     lieu,                          statut
    ('jean.dupont@example.com',  'claire.martin@example.com', 'Consultation',           0, '09:20', 'PRESENTIEL', 'Cabinet médical des Ternes',  'CONFIRME'),
    ('amina.diallo@example.com', 'claire.martin@example.com', 'Première consultation',  0, '10:00', 'PRESENTIEL', 'Cabinet médical des Ternes',  'CONFIRME'),
    ('amina.diallo@example.com', 'claire.martin@example.com', 'Consultation',           1, '14:00', 'DISTANCIEL', NULL,                          'ANNULE_PATIENT'),
    ('jean.dupont@example.com',  'karim.benali@example.com',  'Contrôle des grains de beauté', 4, '09:00', 'PRESENTIEL', 'Cabinet médical des Ternes', 'CONFIRME'),
    ('jean.dupont@example.com',  'sophie.leroy@example.com',  'Séance de rééducation',  2, '08:00', 'PRESENTIEL', 'Centre de kinésithérapie Bastille', 'CONFIRME')
) AS r(patient, praticien, motif, jour, heure, modalite, lieu, statut)
JOIN utilisateur pat ON pat.email = r.patient
JOIN utilisateur pra ON pra.email = r.praticien
JOIN motif m ON m.praticien_id = pra.id AND m.libelle = r.motif
LEFT JOIN lieu l ON l.nom = r.lieu;
