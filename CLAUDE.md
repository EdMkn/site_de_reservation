# CLAUDE.md — Plateforme de réservation médicale (type Doctolib)

## Contexte du projet

Projet portfolio d'un étudiant ingénieur (ESIEA, dernière année) en recherche de stage de fin d'études
orienté web / fullstack. Objectif : démontrer la conception d'un backend Java/Spring Boot complet
(modèle de données, règles métier, concurrence, tests, déploiement) consommé par un frontend Angular.

**Le développeur doit pouvoir expliquer chaque ligne en entretien.** Ce principe prime sur la vitesse :
en cas de doute, explique plutôt que de livrer du code.

## Stack

- Java 21, Spring Boot 3, build Maven (wrapper `./mvnw`)
- Spring Web, Spring Data JPA, Spring Security, Bean Validation
- PostgreSQL, migrations versionnées avec Flyway (extension `btree_gist` requise)
- Tests : JUnit 5, Mockito, Testcontainers (PostgreSQL réel pour les tests d'intégration)
- Documentation API : springdoc-openapi (Swagger UI)
- Conteneurisation : Docker + docker-compose (API + PostgreSQL)
- CI : GitHub Actions (build, tests unitaires et d'intégration)
- Frontend : Angular, dans `frontend/`

## Structure du dépôt

```
backend/
  src/main/java/.../
    domain/          # Entités JPA et logique métier
    availability/    # Calcul des disponibilités : Java pur, sans Spring ni JPA
    service/         # Cas d'usage, transactions (@Transactional)
    repository/      # Interfaces Spring Data JPA
    api/             # Contrôleurs REST, DTO (records), gestion des erreurs
    security/        # Configuration Spring Security
  src/main/resources/db/migration/   # Scripts Flyway (V1__init.sql, ...)
  src/test/java/
  pom.xml
frontend/
docs/
  modele_donnees_reservation.docx    # Référence du modèle de données
docker-compose.yml
```

Règle d'architecture : le package `availability/` ne dépend ni de Spring ni de JPA.
Il reçoit des données simples (plages, absences, rendez-vous) et renvoie des créneaux,
ce qui le rend testable unitairement sans contexte Spring ni base.

## Modèle de données (résumé)

Référence complète : `docs/modele_donnees_reservation.docx`.

- `utilisateur` (email, hash du mot de passe, rôle PATIENT/PRATICIEN, identité)
- `patient` et `praticien` : clé primaire = clé étrangère vers `utilisateur` (héritage, `@Inheritance(strategy = JOINED)`)
- `session` : jetons de connexion (1–N avec utilisateur)
- `specialite` + `praticien_specialite` (N–N)
- `lieu` : cabinets, potentiellement partagés
- `motif` : propre à chaque praticien, porte la durée de consultation
- `plage_horaire` : planning récurrent (jour, heures, modalité, lieu, période de validité)
- `absence` : exceptions au planning
- `rendez_vous` : patient, praticien, motif, modalité, lieu, début, fin, statut

Principes à ne jamais enfreindre :
- Le schéma est défini par les migrations Flyway, jamais généré par Hibernate
  (`spring.jpa.hibernate.ddl-auto=validate`).
- Aucun tableau de références en base : clés étrangères ou tables de liaison.
- **Les disponibilités ne sont jamais stockées**, elles sont calculées par `availability/`.
- Pas de suppression physique de rendez-vous : on change le statut.
- La double réservation est empêchée par une contrainte d'exclusion PostgreSQL
  (déclarée en SQL dans Flyway, JPA ne sait pas l'exprimer) ; un conflit à l'insertion
  est traduit en HTTP 409.

## Répartition des rôles

### Zones réservées au développeur

Dans ces zones, **ne pas écrire l'implémentation**. Rôle attendu : poser des questions,
donner des pistes, relire le code proposé, signaler les bugs et les cas limites oubliés.
Si on te demande explicitement le code d'une de ces zones, rappelle cette règle une fois,
puis respecte le choix du développeur.

- Migrations SQL de structure (tables, contraintes, index)
- Mapping JPA des relations (héritage, N–N, chargement lazy/eager)
- Package `availability/` : calcul des créneaux et détection de chevauchements
- Stratégie de concurrence : transaction de réservation, traduction de l'erreur de contrainte en 409
- Règles d'autorisation (qui peut lire ou modifier quel rendez-vous)
- Choix des cas de test (Claude peut aider à la syntaxe JUnit, Mockito, Testcontainers)
- Premier diagnostic des erreurs (stack trace, logs SQL) avant toute demande d'aide

### Zones où Claude peut implémenter

- `pom.xml`, configuration Spring (`application.yml`, profils), Dockerfile, docker-compose, workflow GitHub Actions
- Squelette des contrôleurs, DTO et leur mapping
- Gestionnaire global d'erreurs (`@RestControllerAdvice`) une fois les cas d'erreur définis par le développeur
- Configuration springdoc / Swagger
- Données de démonstration (migration `R__seed.sql` ou profil dédié)
- Frontend : style, mise en page, composants d'affichage
- README et documentation

Même dans ces zones : expliquer brièvement ce qui a été fait et pourquoi,
pour que le développeur puisse le présenter.

## Conventions Java / Spring

- Injection par constructeur uniquement (pas de `@Autowired` sur les champs)
- Les entités JPA ne sortent jamais de la couche service : l'API expose des DTO (`record`)
- `@Transactional` sur les méthodes de service, pas sur les contrôleurs
- Dates et heures : `java.time` (`Instant` / `OffsetDateTime` pour les rendez-vous, `LocalTime` / `DayOfWeek` pour le planning)
- Validation des entrées avec Bean Validation (`@Valid`, `@NotNull`, ...)
- Pas de Lombok : code explicite, plus simple à expliquer
- Erreurs métier : exceptions dédiées (`CreneauIndisponibleException`, ...) traduites en codes HTTP par l'advice global
- Noms de classes et méthodes en anglais ou en français, mais de façon cohérente dans tout le projet

## Commandes

```bash
# Lancer la base seule
docker compose up -d db

# Lancer l'API en local
cd backend && ./mvnw spring-boot:run

# Tests unitaires
./mvnw test

# Tests unitaires + intégration (Testcontainers, Docker requis)
./mvnw verify

# Environnement complet
docker compose up --build
```

Swagger UI : http://localhost:8080/swagger-ui.html

## Façon de travailler

- Avancer par petites étapes vérifiables : une fonctionnalité, ses tests, puis la suivante.
- Avant tout changement non trivial, proposer le plan et attendre validation.
- Ne pas ajouter de dépendance sans l'avoir proposée et justifiée.
- Après chaque étape importante, suggérer une question d'entretien possible sur ce qui vient d'être fait.
