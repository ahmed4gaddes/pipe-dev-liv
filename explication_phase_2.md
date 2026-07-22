# Explication Détaillée : Phase 2 (Sécurité Centralisée avec Keycloak)

Cette phase visait à mettre en place l'architecture de sécurité globale de la plateforme `pipe-dev-liv` en utilisant le standard **OAuth2 / OIDC** via **Keycloak**.

---

## 1. Pourquoi Keycloak et une Sécurité Centralisée ?
Dans une architecture Microservices, si chaque service doit vérifier le mot de passe de l'utilisateur ou gérer ses propres tokens de connexion, cela devient très vite incontrôlable et non sécurisé.

**La solution :**
- On délègue toute la partie "Authentification" (Vérification des mots de passe, gestion des sessions, génération des jetons) à **Keycloak** (notre Serveur d'Identité).
- On met l'**API Gateway** en "douane". Elle intercepte toutes les requêtes venant de l'extérieur et vérifie le jeton (JWT) avec Keycloak.
- Si le jeton est valide, l'API Gateway le transforme en simples entêtes HTTP (Headers) très faciles à lire pour les microservices (ex: `X-User-Id: ahmed`).

---

## 2. Ce que nous avons fait manuellement (Configuration Keycloak)
Keycloak tourne localement sur le port `9090` via Docker. Nous avons configuré :

1. **Realm (`pipe-dev-liv`)** : Un "royaume" isolé qui contient toute notre configuration (utilisateurs, rôles, clients).
2. **Client Public (`ticketing-app`)** : Pour notre Frontend React. Il n'a pas de secret car le code frontend est visible dans le navigateur. Il permet au frontend de rediriger l'utilisateur vers la page de login Keycloak.
3. **Client Confidentiel (`ticketing-api`)** : Pour notre Backend. Il autorise l'API Gateway à interroger Keycloak pour valider les tokens.
4. **Rôles (RBAC - Role Based Access Control)** : Création des rôles métier (`ROLE_DEVELOPER`, `ROLE_ADMIN`, etc.) pour définir qui a le droit de faire quoi.
5. **Utilisateur (`ahmed`)** : Un utilisateur de test avec ses identifiants et ses rôles assignés.

---

## 3. Ce que nous avons fait dans le code (Backend)

### A. Dans l'API Gateway (`backend/api-gateway`)
La Gateway est le point d'entrée. Elle a été configurée comme un **OAuth2 Resource Server**.
- **`SecurityConfig.java`** : Bloque toutes les requêtes entrantes qui ne contiennent pas de jeton JWT valide (sauf certaines routes comme `/actuator`).
- **`JwtAuthFilter.java`** (Filtre Global) :
  - Il intercepte chaque requête qui passe.
  - Il extrait le token JWT.
  - Il "ouvre" le token JWT pour y lire les **Claims** (les données à l'intérieur, comme l'ID de l'utilisateur, son email, et ses rôles issus de `realm_access`).
  - Il modifie la requête HTTP en cours de vol pour y injecter ces informations sous forme de Headers (`X-User-Id`, `X-User-Email`, `X-User-Roles`).
  - Il transmet la requête modifiée au microservice de destination (ex: `user-service`).

### B. Dans la Bibliothèque Commune (`backend/common-lib`)
Les microservices (User, Ticket, Pipeline) ne doivent plus avoir besoin de valider eux-mêmes des tokens cryptographiques complexes. Ils doivent juste faire confiance à l'API Gateway.
- **`HeaderAuthenticationFilter.java`** : Un filtre Spring Security classique (OncePerRequestFilter). 
  - Son seul travail est de lire les Headers HTTP (`X-User-Id`, `X-User-Roles`) insérés par la Gateway.
  - S'il les trouve, il crée un objet `UsernamePasswordAuthenticationToken` et le place dans le `SecurityContextHolder` de Spring.
  - Cela permet à Spring de savoir "qui" fait la requête.
- **`SecurityConfig.java`** : 
  - Désactive les sessions (car on est en mode "Stateless").
  - Oblige l'utilisateur à être authentifié.
  - Permet d'utiliser des annotations comme `@PreAuthorize("hasRole('ROLE_DEVELOPER')")` dans nos futurs contrôleurs, grâce à l'annotation `@EnableMethodSecurity`.

---

## 4. Résumé du Flux d'une Requête (Workflow)
1. Le Frontend envoie une requête : `GET /api/users/me` avec le token JWT dans l'entête `Authorization: Bearer <token>`.
2. L'**API Gateway** reçoit la requête. Elle valide la signature du token JWT.
3. Le **JwtAuthFilter** extrait les rôles et l'ID, puis transfère la requête au `user-service` avec les entêtes :
   - `X-User-Id: 1234-abcd`
   - `X-User-Roles: DEVELOPER,ADMIN`
4. Le **user-service** reçoit la requête. Son **HeaderAuthenticationFilter** lit ces entêtes.
5. Spring Security autorise l'accès et le code du `UserController` peut s'exécuter.

🎉 La Phase 2 est un succès ! L'architecture est sécurisée et prête pour développer les vrais services métier.
