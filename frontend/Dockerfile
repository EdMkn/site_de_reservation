# --- Étape 1 : build Angular (Node + dépendances de dev, jetées ensuite) ---
FROM node:22-alpine AS build
WORKDIR /app

# Comme pour le backend : d'abord ce qui décrit les dépendances, pour réutiliser la couche
# "npm ci" en cache tant que package-lock.json ne change pas.
COPY package.json package-lock.json ./
# npm ci : installe exactement les versions du lock, et échoue s'il n'est pas à jour.
RUN npm ci

COPY . .
RUN npm run build

# --- Étape 2 : fichiers statiques servis par nginx ---
# Variante "unprivileged" (publiée par NGINX) : tourne sans root et écoute sur 8080,
# comme l'API, qui n'est pas exécutée en root non plus.
FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Le builder "application" d'Angular place le site dans dist/<projet>/browser.
COPY --from=build /app/dist/reservation-web/browser /usr/share/nginx/html
EXPOSE 8080
