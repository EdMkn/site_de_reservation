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
cd backend && ./mvnw spring-boot:run
```

- Swagger UI : http://localhost:8080/swagger-ui.html
- Santé : http://localhost:8080/actuator/health

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
