# ADR-0006 — Une seule stack locale, pas de `docker-compose.{dev,test,prod}.yml` séparés

## Statut

Accepté (Phase 9)

## Contexte

Le document de planification initial suggérait trois fichiers Docker Compose distincts
(`docker-compose.dev.yml`, `.test.yml`, `.prod.yml`), un par environnement cible du pipeline de
déploiement (`deploy.yml` prend un input `environment` parmi `dev`/`test`/`prod`).

## Décision

Ne pas créer ces trois fichiers. Le projet, dans son état actuel, ne dispose d'aucune
infrastructure DEV/TEST/PROD réellement séparée — un seul `docker-compose.yml` fait tourner toute
la stack localement. `deploy.yml` utilise `environment` uniquement comme étiquette (logs, résumé du
run GitHub Actions), pas comme sélecteur d'un ensemble d'infrastructure différent.

## Conséquences

- **Positif** : évite trois fichiers compose quasi identiques à maintenir en synchronisation
  manuelle avec le fichier principal — une source de dérive silencieuse classique (un service
  ajouté à `docker-compose.yml` mais oublié dans `.prod.yml`).
- **Négatif** : le pipeline de déploiement ne peut pas aujourd'hui démontrer une isolation réelle
  entre environnements (même stack, même bases). Si le besoin devient réel (vraies infrastructures
  cibles séparées), ce serait une phase à part entière — pas une extension mineure de la Phase 9 —
  documenté comme tel dans [ADR-0005](0005-self-hosted-runner-and-webhook-forwarding.md) et
  `docs/runner-setup.md`, pas silencieusement oublié.
