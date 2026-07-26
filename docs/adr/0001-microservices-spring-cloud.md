# ADR-0001 — Microservices via Spring Cloud plutôt qu'un monolithe modulaire

## Statut

Accepté (Phase 0-1)

## Contexte

Le projet devait modéliser un pipeline CI/CD (tickets → approbation → déploiement multi-environnement)
avec des responsabilités nettement séparables : gestion des utilisateurs, cycle de vie des tickets,
déclenchement/suivi de pipelines externes (GitHub Actions), notifications, audit. Deux options
raisonnables : un monolithe modulaire (un seul déployable, modules Java séparés) ou des
microservices indépendants.

## Décision

Architecture microservices avec Spring Cloud : `discovery-server` (Eureka) pour l'enregistrement/
découverte, `api-gateway` (Spring Cloud Gateway) comme unique point d'entrée validant les JWT
Keycloak et traduisant les claims en en-têtes internes (`X-User-Id`/`X-User-Roles`), cinq services
métier indépendants (`user`, `ticket`, `pipeline`, `notification`, `audit`), chacun avec sa propre
base PostgreSQL, communiquant en synchrone via Feign (à travers la Gateway) et en asynchrone via
RabbitMQ.

## Conséquences

- **Positif** : chaque service est déployable/testable indépendamment, `pipeline-service` peut
  évoluer (nouvelles intégrations CI) sans toucher `ticket-service`, panne d'un service non
  critique (ex. `notification-service`) n'empêche pas le cœur métier de fonctionner.
- **Négatif** : complexité opérationnelle réelle — 8 modules Maven, 7 bases Postgres, un bus de
  messages, un service de découverte, de la configuration dupliquée (chaque service a son propre
  `.env.properties`, ses propres bindings RabbitMQ). Nécessite une discipline explicite pour éviter
  la duplication incontrôlée (voir [ADR-0002](0002-rabbitmq-mirror-dto-pattern.md) sur comment les
  DTO d'événements sont dupliqués *délibérément*, pas par accident).
- `discovery-server`/`api-gateway` tournent hors Docker en local (voir
  [ADR-0006](0006-no-per-environment-compose-split.md)) alors que les 5 services métier sont
  conteneurisés — une asymétrie assumée du workflow de dev, pas de la prod.
