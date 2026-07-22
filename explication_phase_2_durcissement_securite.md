# Explication Détaillée : Durcissement de la Phase 2 (Sécurité)

Après la Phase 2 (mise en place de Keycloak, de l'API Gateway et du filtrage par headers), une revue de code a été faite avant de démarrer la Phase 3. Elle a révélé **deux failles critiques** et **deux manques bloquants** pour la suite. Ce document explique ce qui n'allait pas et ce qui a été corrigé.

---

## 1. Pourquoi une deuxième passe sur la sécurité ?

L'architecture posée en Phase 2 repose sur un principe simple : *"la Gateway valide le JWT, les microservices font confiance aux headers `X-User-*` qu'elle leur transmet."*

Le problème : **ce principe n'était nulle part appliqué**. Rien n'empêchait un appel direct sur un microservice (en contournant la Gateway) de fabriquer lui-même ces headers et de se faire passer pour n'importe quel utilisateur — y compris un `ROLE_ADMIN`. Le plan d'implémentation le disait explicitement (Étape 2.3 : *"Refuse les requêtes directes sans passer par la Gateway en production"*), mais ce n'était pas codé.

En parallèle, un deuxième problème plus insidieux a été trouvé : **le mécanisme de sécurité partagé dans `common-lib` n'aurait jamais fonctionné pour les futurs microservices** (User, Ticket, Pipeline...).

---

## 2. Problème n°1 (Critique) — Usurpation d'identité par accès direct

### Le bug
Dans `backend/common-lib/.../HeaderAuthenticationFilter.java`, le filtre lisait `X-User-Id` et `X-User-Roles` et créait directement une session authentifiée avec ces valeurs — **sans jamais vérifier que la requête venait bien de la Gateway**.

Concrètement : si `user-service` était accessible sur `:8081` (même juste en local ou depuis un autre conteneur du même réseau Docker), n'importe qui pouvait envoyer :
```
GET /api/users
X-User-Id: attaquant
X-User-Roles: ROLE_ADMIN
```
... et obtenir un accès admin complet, sans JWT, sans mot de passe, sans passer par Keycloak.

### La correction
On a mis en place un **secret partagé** entre la Gateway et les microservices, une sorte de "mot de passe interne" que seule la Gateway connaît :

- **`GATEWAY_INTERNAL_SECRET`** — nouvelle variable d'environnement (`.env` / `.env.example`), une longue chaîne aléatoire.
- **`JwtAuthFilter.java`** (api-gateway) :
  1. Retire d'abord tout header `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Internal-Secret` que le client aurait pu envoyer lui-même (anti-usurpation).
  2. Si le JWT est valide, réinjecte ces headers **avec en plus** `X-Internal-Secret: <valeur secrète>`.
- **`HeaderAuthenticationFilter.java`** (common-lib) : refuse maintenant la requête avec un `403 Forbidden` si `X-User-Id` est présent mais que `X-Internal-Secret` est absent ou incorrect. La comparaison utilise `MessageDigest.isEqual()` (comparaison à temps constant) pour éviter les attaques par mesure de timing.

**Résultat** : un appel direct au microservice, sans passer par la Gateway, ne connaît pas le secret → il est rejeté avant même d'atteindre le contrôleur.

---

## 3. Problème n°2 (Critique) — La sécurité commune n'aurait jamais démarré

### Le bug
`SecurityConfig` et `HeaderAuthenticationFilter` (dans `common-lib`) étaient annotés `@Configuration` / `@Component`, ce qui les rend visibles uniquement via le **scan de composants automatique** de Spring Boot.

Or, ce scan ne regarde que le package de la classe principale de chaque service et ses sous-packages. D'après le plan, `UserServiceApplication` sera dans `com.pipedevliv.user`, `TicketServiceApplication` dans `com.pipedevliv.ticket`, etc. — des packages **frères**, pas des enfants, de `com.pipedevliv.common`.

**Conséquence concrète** : sans correction, chaque nouveau microservice de la Phase 3 aurait démarré avec **zéro filtre de sécurité actif**, tous les endpoints ouverts, silencieusement — sans aucune erreur au démarrage pour le signaler.

### La correction
On a transformé `common-lib` en véritable **module d'auto-configuration Spring Boot**, le mécanisme que Spring Boot utilise lui-même pour ses propres starters :

- `SecurityConfig` porte maintenant l'annotation `@AutoConfiguration` (au lieu de `@Configuration`).
- Un fichier `backend/common-lib/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` déclare explicitement cette classe.
- `HeaderAuthenticationFilter` n'est plus un `@Component` : il est créé via une méthode `@Bean` dans `SecurityConfig`.

Avec cette approche, **tout microservice qui dépend de `common-lib` (dans son `pom.xml`) active automatiquement la sécurité**, quel que soit son package — Spring Boot scanne ce fichier sur le classpath, indépendamment du `@ComponentScan`.

---

## 4. Problème n°3 (Haute priorité) — CORS non configuré

### Le bug
Aucune configuration CORS n'existait sur la Gateway. À partir de la Phase 8 (Frontend React sur `http://localhost:5173`), le navigateur aurait bloqué tous les appels vers la Gateway (`http://localhost:8080`) car ce sont deux origines différentes.

### La correction
Dans `GatewaySecurityConfig.java` :
- Un `CorsConfigurationSource` autorise les origines listées dans la variable `CORS_ALLOWED_ORIGINS` (par défaut `http://localhost:5173`).
- Les requêtes `OPTIONS` (pré-vérification CORS, envoyées automatiquement par le navigateur sans JWT) sont autorisées sans authentification — sinon le navigateur recevrait un `401` sur le préflight avant même d'envoyer la vraie requête.

---

## 5. Problème n°4 (Haute priorité) — Healthcheck bloqué par la sécurité

### Le bug
`GatewaySecurityConfig` et `SecurityConfig` (common-lib) exigeaient un JWT valide pour **absolument toutes** les requêtes. Le pipeline de déploiement prévu en Phase 9 (`deploy.yml`) fait pourtant :
```bash
curl -f http://localhost:8080/actuator/health || exit 1
```
Sans exception, cet appel — et tout futur healthcheck Docker — aurait toujours reçu un `401 Unauthorized`.

### La correction
- `/actuator/health/**` est maintenant autorisé sans authentification (`permitAll()`) côté Gateway **et** côté chaque microservice (via `common-lib`).
- `spring-boot-starter-actuator` a été ajouté comme dépendance commune dans `backend/pom.xml` (POM parent), pour que l'endpoint `/actuator/health` existe réellement dans tous les modules, y compris ceux à venir.

---

## 6. Nouveau flux de requête (mis à jour)

1. Le Frontend envoie `GET /api/tickets` avec `Authorization: Bearer <JWT>`.
2. La Gateway valide le JWT. `JwtAuthFilter` **retire** d'abord tout header `X-User-*` / `X-Internal-Secret` que le client aurait tenté d'envoyer.
3. `JwtAuthFilter` réinjecte les vrais headers, signés par le secret interne :
   - `X-User-Id: 1234-abcd`
   - `X-User-Roles: ROLE_DEVELOPER,ROLE_ADMIN`
   - `X-Internal-Secret: <secret partagé>`
4. `ticket-service` reçoit la requête. `HeaderAuthenticationFilter` (activé automatiquement grâce à l'auto-configuration) vérifie `X-Internal-Secret` :
   - **Correct** → authentifie l'utilisateur, la requête continue normalement.
   - **Absent ou incorrect** → `403 Forbidden` immédiat, la requête n'atteint jamais le contrôleur.

Une requête envoyée **directement** à `ticket-service:8082` sans passer par la Gateway n'a pas le secret → elle est systématiquement rejetée.

---

## 7. Nouvelles variables d'environnement

Ajoutées dans `.env` et `.env.example` :

| Variable | Rôle |
|---|---|
| `GATEWAY_INTERNAL_SECRET` | Secret partagé Gateway ↔ microservices. À régénérer par environnement (`openssl rand -base64 48`). |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées à appeler la Gateway (le Frontend). |

> [!IMPORTANT]
> Chaque futur microservice (Phase 3+) devra définir `security.internal.gateway-secret: ${GATEWAY_INTERNAL_SECRET}` dans son `application.yml`. Si cette propriété manque, le service **refuse de démarrer** (fail-fast volontaire) plutôt que de démarrer sans protection.

---

## 8. Résumé

| Problème | Sévérité | Statut |
|---|---|---|
| Usurpation d'identité par appel direct au microservice | 🔴 Critique | ✅ Corrigé (secret partagé) |
| Sécurité commune jamais activée sur les futurs services | 🔴 Critique | ✅ Corrigé (auto-configuration Spring Boot) |
| CORS bloquant le Frontend | 🟠 Haute | ✅ Corrigé |
| Healthcheck bloqué par l'authentification | 🟠 Haute | ✅ Corrigé |

🎉 La base sécurité est maintenant solide pour attaquer la Phase 3 (User Service) sans reproduire ces failles dans chaque nouveau microservice.
