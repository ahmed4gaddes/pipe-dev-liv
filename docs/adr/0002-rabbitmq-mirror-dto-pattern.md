# ADR-0002 — DTO miroirs par consommateur RabbitMQ, pas de partage via common-lib

## Statut

Accepté (Phase 6, reconduit Phase 7)

## Contexte

`notification-service` et `audit-service` consomment les mêmes événements RabbitMQ que produisent
`user-service`, `ticket-service` et `pipeline-service` (`user.synced`, `ticket.created`,
`ticket.status-changed`, `ticket.approved`, `pipeline.started`, `pipeline.completed`,
`pipeline.failed`). Le problème : `Jackson2JsonMessageConverter` sérialise avec un header
`__TypeId__` contenant le FQCN de la classe productrice (ex.
`com.pipedevliv.ticket.messaging.TicketEvent`) — une classe qui n'existe pas sur le classpath du
consommateur, puisque `ticket-service` et `notification-service` ne partagent pas leurs classes de
domaine.

Deux options : (a) déplacer ces DTO d'événements dans `common-lib`, partagés par tous les
producteurs et consommateurs ; (b) chaque consommateur déclare ses propres classes miroirs
minimales et un `DefaultJackson2JavaTypeMapper.setIdClassMapping(...)` qui fait correspondre le
FQCN du producteur à sa classe locale.

## Décision

Option (b). Chaque service consommateur (`notification-service`, `audit-service`) définit sa
propre copie indépendante des DTO (`TicketEventPayload`, `TicketCreatedPayload`,
`PipelineEventPayload`, `UserSyncedPayload`, tous `@JsonIgnoreProperties(ignoreUnknown = true)`) et
son propre `RabbitMQConfig` avec le mapping FQCN→classe locale.

## Conséquences

- **Positif** : aucun couplage de compilation entre `ticket-service`/`pipeline-service`/
  `user-service` et leurs consommateurs — `common-lib` reste un module d'infrastructure partagée
  (sécurité, exceptions), jamais un lieu où les schémas d'événements de tous les services
  convergent et se rigidifient ensemble. Un producteur peut ajouter un champ à son event sans
  recompiler ses consommateurs (`ignoreUnknown = true`).
- **Négatif** : duplication réelle — le même DTO existe en 2 ou 3 copies légèrement différentes
  selon ce que chaque consommateur a besoin de lire. Une renommée de champ côté producteur (ex.
  `ticketId` → `id`) ne casse rien à la compilation côté consommateur (silencieux) — seul un test
  d'intégration bout en bout (voir Phase 10, `NotificationServiceIntegrationIT`/
  `AuditServiceIntegrationIT`) peut détecter ce genre de dérive.
