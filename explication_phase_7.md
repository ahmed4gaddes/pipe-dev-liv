# Explication Détaillée : Phase 7 (Audit Service — Journal d'audit immuable)

Cette phase construit le **Audit Service** : un deuxième consommateur pur des événements RabbitMQ, à côté de `notification-service` (Phase 6). Choix confirmé directement avec l'utilisateur (parmi trois options : Audit Service / Frontend / CI-CD) pour terminer le travail sur les microservices backend avant de passer au frontend ou au pipeline CI/CD.

Contrairement à `notification-service`, ce service ne réinvente rien côté mécanique de consommation — il réutilise telle quelle la solution trouvée en Phase 6 pour le problème du `__TypeId__` (voir `explication_phase_6.md` §1). Ce document se concentre donc sur ce qui **diffère**.

---

## 1. Même mécanisme de type-mapping, copie indépendante

`audit-service` déclare ses propres DTOs miroirs (`dto/TicketCreatedPayload`, `TicketEventPayload`, `PipelineEventPayload`, `UserSyncedPayload`) et son propre `DefaultJackson2JavaTypeMapper` (`config/RabbitMQConfig.java`), avec la même table de correspondance FQCN → classe locale que `notification-service`. Ce n'est **pas** une dépendance vers `notification-service` — chaque service consommateur garde sa propre vue, indépendante, des formes d'événements amont (même raisonnement de couplage à sens unique qu'en Phase 6).

Différence notable : les miroirs de cette phase sont **plus complets** que ceux de Phase 6. Notification n'avait besoin que des champs nécessaires à la résolution de destinataires et à un message court ; un journal d'audit a pour but la traçabilité complète, donc chaque DTO miroir reprend (quasiment) tous les champs du payload d'origine, et l'objet entier est sérialisé en JSON et stocké tel quel dans la colonne `details` — pas seulement les champs utilisés pour construire la `description` lisible.

---

## 2. Pas de résolution de destinataires : une ligne par événement, toujours

`NotificationServiceImpl` devait décider *qui* prévenir (fan-out par rôle, exclusion de l'acteur, etc. — voir Phase 6 §4). Un journal d'audit n'a pas cette question : chacun des 7 routing keys réservés produit **exactement une ligne** `AuditLog`, sans exclusion ni destinataire à calculer. Résultat : pas de table `LocalUser`, pas de logique de fan-out, `AuditServiceImpl` est un simple `switch` qui extrait `(entityType, entityId, actorUserId, description)` par type d'événement et insère.

Autre différence : `user.synced` était consommé par `notification-service` uniquement pour construire son annuaire local, jamais transformé en notification visible. Ici, `user.synced` **est** un événement audité comme les autres (`entityType=USER`, `actorUserId=keycloakId`) — cohérent avec le principe "tout ce qui se passe dans le système laisse une trace".

| Routing key | entityType | entityId | actorUserId |
|---|---|---|---|
| `user.synced` | `USER` | id du profil | keycloakId |
| `ticket.created` | `TICKET` | id du ticket | créateur |
| `ticket.status-changed` | `TICKET` | id du ticket | auteur du changement |
| `ticket.approved` | `TICKET` | id du ticket | auteur du changement |
| `pipeline.started`/`.completed`/`.failed` | `PIPELINE_EXECUTION` | id de l'exécution | déclencheur |

---

## 3. Aucun champ producteur n'a dû changer

Contrairement à la Phase 6 (qui avait dû ajouter `createdByUserId`/`assignedToUserId` à `TicketEvent` et `triggeredByUserId` à `PipelineEvent`), cette phase n'a touché **aucun** service déjà construit. Tous les champs nécessaires existaient déjà après l'enrichissement de Phase 6. Phase 7 est un ajout pur : un nouveau module, une route Gateway, un bloc `docker-compose.yml`.

---

## 4. Immuabilité : aucune route de mutation

`AuditLog` n'a pas de champ `updatedAt` (contrairement à `Notification` qui a `read`/`readAt`), et `AuditLogController` n'expose **aucun** `PATCH`/`DELETE` — seules deux routes `GET` existent. La seule façon d'insérer une ligne est de consommer un événement (`AuditListener`). L'immuabilité est donc garantie par absence de mécanisme de mutation, pas par un contrôle applicatif.

Accès : `hasRole('ADMIN')` sur les deux endpoints — un journal d'audit est une préoccupation d'administration, pas personnelle (contrairement aux notifications de Phase 6, accessibles à tout utilisateur authentifié sur ses propres entrées). Même précédent que `TicketController.getStats()`, seul autre endpoint "vue d'ensemble sensible" du projet.

---

## 5. API REST (`AuditLogController`)

| Méthode | Endpoint | Comportement |
|---|---|---|
| `GET` | `/api/audit-logs?eventType=&entityType=&entityId=&actorUserId=&page=&size=` | Liste filtrée et paginée, plus récent d'abord |
| `GET` | `/api/audit-logs/{id}` | Une entrée, avec son `details` complet |

Le filtrage réutilise le pattern JPQL déjà établi par `TicketRepository.search` (`(:param IS NULL OR ...)`), appliqué ici sur `eventType`/`entityType`/`entityId`/`actorUserId`.

---

## 6. Docker Compose

`audit-service` ajouté sur le même modèle que `notification-service` : port `8085`, connecté à `postgres-audit` (déjà présent depuis le début, jamais utilisé avant cette phase) et `rabbitmq`. Pas de variables `GH_*` — ce service ne parle à aucune API externe.

---

## 7. Ce qui reste ouvert pour la suite

- Un test de bout en bout live (démarrer la stack complète, effectuer quelques actions ticket/pipeline, vérifier que `GET /api/audit-logs` en tant qu'ADMIN montre les bonnes lignes) reste à faire interactivement — même limite que les smoke tests reportés en Phases 5-6.
- Le frontend (React) reste à construire — juste le scaffold Vite pour l'instant.
- CI/CD (`deploy.yml`) reste la Phase 9 explicitement réservée depuis la Phase 5.
