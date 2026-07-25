# Explication Détaillée : Phase 6 (Notification Service — Notifications in-app)

Cette phase construit le **Notification Service** : le premier service qui ne fait que **consommer** des événements (aucun autre service ne l'appelle en synchrone, et lui-même n'appelle personne en synchrone). Chaque service précédent publie déjà sur l'exchange RabbitMQ partagé depuis les Phases 3/4/5 — personne ne consommait encore rien. Cette phase ferme cette boucle.

**Périmètre confirmé avec l'utilisateur** : notifications in-app uniquement (persistées, listables, marquables comme lues). Ni email ni WebSocket — le `pom.xml` du stub portait déjà le commentaire `// Email, Websockets etc à rajouter plus tard`, et contrairement à GitHub (Phase 5), aucune variable SMTP n'était pré-réservée dans `.env`. Ces deux canaux restent des extensions futures explicitement documentées, pas des trous béants.

---

## 1. Le problème du `__TypeId__` : pourquoi pas de DTOs partagés

`Jackson2JsonMessageConverter` (Spring AMQP) tague chaque message d'un header `__TypeId__` contenant le nom de classe complet de l'objet **côté producteur** — par exemple `com.pipedevliv.ticket.messaging.TicketEvent`. Par défaut, le consommateur essaie de faire `Class.forName(...)` sur cette chaîne pour désérialiser. Ça casse immédiatement ici : `notification-service` n'a ni la classe `TicketEvent` de ticket-service, ni `PipelineEvent` de pipeline-service, ni `UserDTO` de user-service sur son classpath — et ce n'est pas souhaitable qu'il les ait (coupler un service consommateur à toutes les classes internes de tous les producteurs romprait l'indépendance des microservices).

La solution : `notification-service` déclare ses **propres DTOs miroirs**, minimalistes (seulement les champs dont il a besoin, `@JsonIgnoreProperties(ignoreUnknown = true)` pour ignorer le reste — même technique que `GitHubWebhookPayload` en Phase 5), et configure un `DefaultJackson2JavaTypeMapper` avec `setIdClassMapping(...)` (`config/RabbitMQConfig.java`) qui fait correspondre chaque FQCN producteur à son DTO miroir local :

```java
typeMapper.setIdClassMapping(Map.of(
    "com.pipedevliv.ticket.dto.TicketResponseDTO", TicketCreatedPayload.class,
    "com.pipedevliv.ticket.messaging.TicketEvent", TicketEventPayload.class,
    "com.pipedevliv.pipeline.messaging.PipelineEvent", PipelineEventPayload.class,
    "com.pipedevliv.user.dto.UserDTO", UserSyncedPayload.class
));
```

Le couplage reste à sens unique : le consommateur connaît la forme des événements amont, les producteurs ignorent totalement l'existence de `notification-service`. Aucun changement à `common-lib`.

---

## 2. Deux champs manquants découverts en explorant les événements existants

Les événements déjà publiés en Phase 4/5 ne portaient pas toujours l'information nécessaire pour savoir **qui notifier** :

- `TicketEvent` (utilisé par `ticket.status-changed` et `ticket.approved`) n'avait que `changedByUserId` — pas le propriétaire (`createdByUserId`) ni l'assigné (`assignedToUserId`) du ticket, pourtant déjà présents sur l'entité `Ticket`. Corrigé en une ligne dans `TicketEventPublisher.toEvent()`.
- `PipelineEvent` n'avait pas `triggeredByUserId`, pourtant déjà stocké sur `PipelineExecution`. Même correction dans `PipelineEventPublisher.toEvent()`.

Ce sont des ajouts strictement additifs (nouveaux champs sur des DTOs existants) — aucun consommateur de ces événements n'existait avant cette phase, donc rien ne casse.

`ticket.created`, lui, n'avait pas ce problème : il publie déjà `TicketResponseDTO` en entier, qui contient `createdByUserId`.

---

## 3. `LocalUser` : un annuaire local, pas une source de vérité

Pour savoir qui prévenir quand un ticket est créé (les approbateurs TECH_LEAD+), il faut une liste des utilisateurs par rôle. Plutôt que d'ajouter un appel Feign synchrone vers user-service (ce qui casserait le principe "ce service ne fait que consommer, pas d'appel sortant"), `notification-service` consomme aussi `user.synced` (déjà publié par `UserServiceImpl.syncCurrentUser` à chaque connexion) dans une table locale `local_users` (`keycloakId`, `email`, `fullName`, `roles`).

**Limite acceptée, pas corrigée cette phase** : un utilisateur qui ne s'est jamais connecté n'apparaît pas encore dans `local_users`, donc ne recevra pas de notification "nouveau ticket à approuver" tant qu'il ne s'est pas connecté au moins une fois. Même esprit que la limite de test live de GitHub Actions en Phase 5 — documentée, pas backfillée.

---

## 4. Règles de destinataires (`NotificationServiceImpl.handleEvent`)

| Routing key | Destinataires | Notification |
|---|---|---|
| `user.synced` | *(aucun — met juste à jour `local_users`)* | — |
| `ticket.created` | tout `LocalUser` avec un rôle TECH_LEAD/RELEASE_MANAGER/ADMIN, sauf le créateur | `TICKET_CREATED` |
| `ticket.status-changed` | propriétaire + assigné, sauf l'auteur du changement | `TICKET_STATUS_CHANGED` |
| `ticket.approved` | propriétaire, sauf s'il s'est auto-approuvé | `TICKET_APPROVED` |
| `pipeline.started`/`.completed`/`.failed` | l'utilisateur qui a déclenché le déploiement | notification correspondante |

Règle transversale : on ne notifie jamais l'acteur pour sa propre action (`... sauf l'auteur du changement`).

---

## 5. API REST (`NotificationController`)

| Méthode | Endpoint | Comportement |
|---|---|---|
| `GET` | `/api/notifications?unreadOnly=&page=&size=` | Page des notifications de l'appelant, plus récentes d'abord |
| `GET` | `/api/notifications/unread-count` | `{ "count": n }` |
| `PATCH` | `/api/notifications/{id}/read` | Marque comme lue ; `403` si elle appartient à quelqu'un d'autre, `404` si elle n'existe pas |
| `PATCH` | `/api/notifications/read-all` | Marque toutes les notifications non lues de l'appelant comme lues |

Tous protégés par `hasRole('VIEWER')` (n'importe quel utilisateur authentifié) — c'est la **propriété**, pas le rôle, qui restreint réellement l'accès (comparer avec les vérifications d'appartenance déjà en place sur les tickets en Phase 4). Aucune modification nécessaire à `GatewaySecurityConfig` — contrairement au webhook GitHub de la Phase 5, chaque appel passe par le flux JWT normal.

---

## 6. Docker Compose

`notification-service` ajouté sur le même modèle que `pipeline-service` : port `8084`, connecté à `postgres-notifications` (déjà présent depuis le début, jamais utilisé avant cette phase) et `rabbitmq`. Pas de variables `GH_*` — ce service ne parle à aucune API externe.

---

## 7. Ce qui reste ouvert pour la suite

- Un test de bout en bout live (démarrer la stack complète, se connecter avec deux utilisateurs différents, créer/approuver/déployer un ticket, vérifier que `GET /api/notifications` renvoie les bonnes lignes pour les bons utilisateurs) reste à faire interactivement — même limite que le smoke test de `pipeline-service` reporté en Phase 5.
- Email et WebSocket restent des extensions futures explicitement hors périmètre.
- `audit-service` reste un stub — futur consommateur des mêmes événements, avec un besoin différent (traçabilité complète, pas de notion de "lu/non lu").
