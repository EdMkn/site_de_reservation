# Réservation médicale

Plateforme de prise de rendez-vous médicaux (type Doctolib) : API REST Java / Spring Boot,
base PostgreSQL et frontend Angular.

## Stack

- Java 21, Spring Boot 3 (Web, Data JPA, Security, Validation), Maven
- PostgreSQL 16, migrations Flyway
- Tests : JUnit 5, Mockito, Testcontainers
- Docker / docker-compose, CI GitHub Actions

## Démarrage

Prérequis : Java 21 et Docker.

```bash
# Environnement complet (API + base)
docker compose up --build

# Ou en développement : base dans Docker, API en local
docker compose up -d db
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

- Swagger UI : http://localhost:8080/swagger-ui.html
- Santé : http://localhost:8080/actuator/health

### Ports

Par défaut : API sur 8080, PostgreSQL sur 5432. Si l'un est déjà pris sur la machine,
créer un fichier `.env` à la racine (ignoré par Git), lu automatiquement par docker compose :

```bash
API_PORT=8081
DB_PORT=5433
```

Pour l'API lancée en local, passer les mêmes variables :
`API_PORT=8081 DB_PORT=5433 ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`.

### Données de démonstration

Le profil Spring `demo` (activé par docker-compose) charge `db/seed/R__seed.sql` :
3 praticiens, 2 patients, leurs motifs, plannings et quelques rendez-vous la semaine suivante.

| Compte | Rôle |
|---|---|
| `claire.martin@example.com` | Praticienne, médecine générale |
| `karim.benali@example.com` | Praticien, dermatologie |
| `sophie.leroy@example.com` | Praticienne, kinésithérapie |
| `jean.dupont@example.com` | Patient |
| `amina.diallo@example.com` | Patiente |

Mot de passe commun : `demo1234`.

Le seed **vide toutes les tables** avant de les remplir : ne jamais activer le profil `demo`
sur une base contenant de vraies données. Les rendez-vous de démo sont placés la semaine
suivant le chargement ; pour les régénérer : `docker compose down -v && docker compose up --build`.

## Tests

```bash
cd backend
./mvnw test     # tests unitaires
./mvnw verify   # + tests d'intégration (Docker requis)
```

## Structure

```
backend/   API Spring Boot (migrations Flyway dans src/main/resources/db/migration)
frontend/  Application Angular
docs/      Modèle de données
```
