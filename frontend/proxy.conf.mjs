// Proxy du serveur de développement (ng serve) : /api/** est transmis à l'API Spring.
// Le navigateur ne voit qu'une origine (localhost:4200), donc pas de CORS à configurer.
// Même variable API_PORT que docker-compose.yml et application.yml (8080 par défaut).
export default {
  '/api': {
    target: `http://localhost:${process.env.API_PORT ?? 8080}`,
    secure: false,
  },
};
