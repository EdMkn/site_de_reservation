# --- Étape 1 : compilation (JDK complet + Maven via le wrapper) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# On copie d'abord uniquement ce qui décrit les dépendances : tant que pom.xml ne change pas,
# Docker réutilise la couche en cache et ne retélécharge pas les dépendances.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Les tests tournent en CI ; ici on ne fait que produire le jar.
RUN ./mvnw -B -q package -DskipTests

# --- Étape 2 : exécution (JRE seul, image plus légère, pas d'outils de build) ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Ne pas exécuter l'application en root dans le conteneur
RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
