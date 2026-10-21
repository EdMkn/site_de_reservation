# Réservation médicale

Plateforme de prise de rendez-vous médicaux (type Doctolib) : API REST Java / Spring Boot,
base PostgreSQL et frontend Angular.

## Stack

- Java 21, Spring Boot 3 (Web, Data JPA, Security, Validation), Maven
- PostgreSQL 16, migrations Flyway
- Tests : JUnit 5, Mockito, Testcontainers
- Angular 22 (composants standalone, signals), SCSS
- Docker / docker-compose (nginx pour le frontend), CI GitHub Actions

## Démarrage

Prérequis : Docker ; pour le développement, Java 21 et Node 22 (`nvm use` dans `frontend/`).

```bash
# Environnement complet (frontend + API + base)
docker compose up --build

# Ou en développement : base dans Docker, API et frontend en local
docker compose up -d db
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
cd frontend && npm ci && npm start
```

- Application : http://localhost:4200 (en développement, `ng serve` relaie `/api` vers l'API
  grâce à `proxy.conf.mjs` ; avec Docker, c'est nginx qui le fait)

- Swagger UI : http://localhost:8080/swagger-ui.html
- Santé : http://localhost:8080/actuator/health

### Lancer sans compiler (images Docker Hub)

Les images de l'API et du frontend sont publiées sur Docker Hub par la CI à chaque push sur `main`
(`edmkn/reservation-api` et `edmkn/reservation-web`, pour amd64 et arm64). Seuls Docker et le fichier
`docker-compose.yml` sont nécessaires :

```bash
docker compose pull
docker compose up
```

Étiquettes des images :

- `latest` : la dernière version de `main` ;
- `1.0.0`, `1.1.0`… : les versions publiées.

Pour lancer une version précise : `TAG=1.0.0 docker compose up`.

Publier une version : `git tag v1.0.0 && git push origin v1.0.0`. La CI relance tous les tests
puis publie les images `1.0.0`.

### Ports

Par défaut : frontend sur 4200, API sur 8080, PostgreSQL sur 5432. Si l'un est déjà pris sur la machine,
créer un fichier `.env` à la racine (ignoré par Git), lu automatiquement par docker compose :

```bash
WEB_PORT=4201
API_PORT=8081
DB_PORT=5433
```

En local, passer les mêmes variables à l'API et au serveur de développement Angular :
`API_PORT=8081 DB_PORT=5433 ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`,
puis `API_PORT=8081 npm start`.

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

cd frontend
npx ng test --watch=false   # tests unitaires (Vitest)
```

Vitest ne fonctionne pas si le chemin du projet contient un `#` (Vite le prend pour une
ancre d'URL) : cloner le dépôt dans un dossier sans ce caractère.

## Structure

```
backend/   API Spring Boot (migrations Flyway dans src/main/resources/db/migration)
frontend/  Application Angular
docs/      Modèle de données
```
