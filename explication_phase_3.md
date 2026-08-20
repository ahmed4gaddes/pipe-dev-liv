# Phase 3 — Ce qui est nouveau par rapport à la Phase 2

En Phase 2, on a mis en place la sécurité (Keycloak, Gateway, filtres). 
En Phase 3, on a construit le **premier vrai microservice** qui fait quelque chose de concret.

---

## 1. Le User Service (notre premier microservice métier)

### C'est quoi ?
Un service Spring Boot qui tourne sur le port **8081** et qui gère les profils utilisateurs.

### Pourquoi on en a besoin ?
Keycloak gère les mots de passe et les connexions, mais il ne stocke pas les infos métier de notre application (historique, préférences, etc.). Le `user-service` a sa propre base de données PostgreSQL (`users_db`) pour stocker ces données.

### Ce qu'il fait concrètement :
- **`GET /api/users`** → Liste tous les utilisateurs (avec pagination)
- **`GET /api/users/{id}`** → Récupère un utilisateur par son ID
- **`GET /api/users/me`** → Le plus important : quand un utilisateur se connecte pour la première fois, cet endpoint crée automatiquement son profil en base. S'il existe déjà, il met à jour ses infos (email, nom, rôles).

### Les fichiers créés :
- `UserProfile.java` → La table en base de données (id, keycloakId, email, fullName, roles, createdAt, updatedAt)
- `UserProfileRepository.java` → Les requêtes vers la base (findByKeycloakId)
- `UserService.java` + `UserServiceImpl.java` → La logique métier
- `UserController.java` → Les endpoints REST
- `UserDTO.java` + `UserSyncDTO.java` → Les objets de transfert de données

---

## 2. Durcissement de la Sécurité

### Problème qu'on avait en Phase 2
En Phase 2, les microservices faisaient confiance aux headers `X-User-Id` et `X-User-Roles` sans vérification. N'importe qui pouvait appeler directement le microservice sur le port 8081 en mettant `X-User-Id: admin` et se faire passer pour un admin.

### Ce qu'on a ajouté
Un **mot de passe secret** partagé entre la Gateway et les microservices (`GATEWAY_INTERNAL_SECRET`).

Le fonctionnement :
1. La Gateway ajoute le header `X-Internal-Secret: <le-secret>` à chaque requête
2. Le microservice vérifie que ce secret est présent et correct
3. Si le secret est absent ou faux → **403 Forbidden** (accès refusé)
4. En bonus, la Gateway **supprime** d'abord tous les headers `X-User-*` de la requête du client avant d'injecter les vrais. Même si un attaquant essaie de mettre de faux headers, ils sont effacés.

---

## 3. AutoConfiguration (correction technique importante)

### Le problème
Le `user-service` est dans le package `com.pipedevliv.user`. Le `common-lib` (qui contient la sécurité) est dans `com.pipedevliv.common`. Spring Boot ne scanne que son propre package. Résultat : la sécurité de `common-lib` n'était **jamais chargée** par les microservices !

### La solution
On a remplacé `@Configuration` par `@AutoConfiguration` dans le `SecurityConfig` de `common-lib`, et on l'a déclaré dans un fichier spécial (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`). Maintenant, tout microservice qui a `common-lib` dans ses dépendances Maven récupère automatiquement la sécurité, peu importe son package.

---

## 4. RabbitMQ (Messagerie entre services)

### C'est quoi ?
RabbitMQ est un "bureau de poste" pour les microservices. Au lieu d'appeler directement un autre service (couplage fort), on publie un message (événement) et les services intéressés le reçoivent automatiquement.

### Ce qu'on a fait
Quand un utilisateur se synchronise via `/api/users/me`, le `user-service` publie un événement `user.synced` sur RabbitMQ contenant les infos du profil.

```
user-service  ──publish──▶  RabbitMQ  ──deliver──▶  notification-service (futur)
                              │
                              └──deliver──▶  audit-service (futur)
```

Les constantes pour tous les futurs événements sont déjà prêtes dans `RabbitMQConstants.java` :
- `user.synced` → Un profil a été synchronisé
- `ticket.created` → Un ticket a été créé (Phase 4)
- `ticket.approved` → Un ticket a été approuvé (Phase 4)
- `pipeline.started` / `completed` / `failed` → États du pipeline (Phase 5)

---

## 5. Tests Unitaires

### Pourquoi ?
Pour vérifier que le code fonctionne sans démarrer toute l'infrastructure (pas besoin de PostgreSQL, Keycloak, RabbitMQ en local).

### Ce qu'on a testé (3 fichiers de tests) :

**Tests du Contrôleur** (`UserControllerTest.java`) :
- Les 4 endpoints retournent les bonnes réponses JSON
- Le endpoint `/me` lit bien les headers de la Gateway

**Tests de la Logique Métier** (`UserServiceImplTest.java`) :
- Créer un nouveau profil quand l'utilisateur n'existe pas
- Mettre à jour le profil quand il existe déjà
- Lever une erreur quand on cherche un utilisateur qui n'existe pas
- Vérifier qu'un événement RabbitMQ est bien publié après la synchronisation

**Tests de la Base de Données** (`UserProfileRepositoryTest.java`) :
- `findByKeycloakId` retrouve un profil existant
- `findByKeycloakId` retourne vide si le profil n'existe pas
- Impossible de créer 2 profils avec le même `keycloakId` (contrainte d'unicité)

Les tests utilisent **H2** (une base de données en mémoire) au lieu de PostgreSQL pour être rapides et autonomes.

---

## 6. Docker Compose (Conteneurisation)

### Ce qu'on a ajouté
- Le `user-service` peut maintenant tourner dans un conteneur Docker
- Une base PostgreSQL séparée pour chaque futur microservice :
  - `postgres-users` (port 5433) → pour le `user-service`
  - `postgres-tickets` (port 5434) → pour le futur `ticket-service`
  - `postgres-pipelines` (port 5435) → pour le futur `pipeline-service`
  - `postgres-notifications` (port 5436) → pour le futur `notification-service`
  - `postgres-audit` (port 5437) → pour le futur `audit-service`

Chaque base a un **healthcheck** : le service qui en dépend attend que la base soit prête avant de démarrer.

---

## Résumé visuel

```
AVANT (fin Phase 2) :                    APRÈS (fin Phase 3) :

┌──────────────┐                         ┌──────────────┐
│   Frontend   │                         │   Frontend   │
└──────┬───────┘                         └──────┬───────┘
       │                                        │
┌──────▼───────┐                         ┌──────▼───────┐
│  API Gateway │ ← JWT Keycloak          │  API Gateway │ ← JWT + Secret
└──────┬───────┘                         └──────┬───────┘
       │                                        │
       ▼                                 ┌──────▼───────┐    ┌──────────┐
  (rien derrière)                        │ user-service │───▶│PostgreSQL│
                                         └──────┬───────┘    └──────────┘
                                                │
                                         ┌──────▼───────┐
                                         │   RabbitMQ   │
                                         └──────────────┘
```
