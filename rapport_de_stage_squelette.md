# Squelette — Rapport de Stage

Ceci est un **squelette**, pas le rapport : une structure section par section, avec pour chacune
ce qu'il faut couvrir et où trouver la matière réelle déjà écrite dans le projet
(`explication_phase_N.md`, `docs/adr/`, `progress.md`). Le texte, l'analyse, la réflexion
personnelle restent à écrire — c'est votre expérience de stage, pas la mienne.

---

## 1. Introduction & contexte

À écrire vous-même (contexte de l'entreprise/du stage, problématique, objectifs), mais peut
s'appuyer sur :
- Le sujet tel que formulé dans `implementation_plan stage micr.md` (§ Contexte du Projet) :
  « Mise en place d'un pipeline DevOps CI/CD sur les environnements DEV, TEST et PROD, intégrant
  des contrôles de sécurité, des tests automatisés, et une application de ticketing qui pilote le
  pipeline. »
- Le passage d'une architecture monolithique modulaire (plan initial) vers microservices — un vrai
  changement de cap à raconter, pas juste un choix de départ (voir
  [ADR-0001](docs/adr/0001-microservices-spring-cloud.md)).
- Objectifs concrets atteints : 5 microservices, frontend React, CI/CD réel (pas simulé),
  intégration GitHub Actions bout en bout.

**Points à couvrir** : contexte du stage (entreprise, équipe, durée), problématique métier (pourquoi
un pipeline CI/CD piloté par ticketing plutôt qu'un simple accès direct à GitHub Actions),
objectifs pédagogiques/techniques visés.

---

## 2. Étude de l'existant & état de l'art

**Points à couvrir** (à rédiger, matière technique disponible) :
- **Microservices vs monolithe modulaire** — le compromis réel documenté dans
  [ADR-0001](docs/adr/0001-microservices-spring-cloud.md) : gain d'indépendance de déploiement
  contre coût opérationnel réel (8 modules, 7 bases, un bus de messages).
- **Communication synchrone vs asynchrone** — Feign (à travers la Gateway) pour le synchrone,
  RabbitMQ (topic exchange) pour l'asynchrone ; le choix du pattern DTO miroir plutôt que le
  partage de classes ([ADR-0002](docs/adr/0002-rabbitmq-mirror-dto-pattern.md)) est un vrai sujet
  d'état de l'art (couplage inter-services).
- **Sécurité en microservices** — pourquoi un point d'entrée unique (Gateway) qui valide le JWT une
  fois, propage l'identité via des en-têtes internes signés (`X-Internal-Secret`), plutôt que
  chaque service qui revalide un JWT indépendamment. Comparer avec l'alternative (chaque service
  valide son propre JWT) et justifier le choix fait ici.
- **CI/CD réel vs simulé** — le webhook natif GitHub `workflow_run` plutôt qu'un endpoint de
  callback personnalisé ([ADR-0004](docs/adr/0004-native-github-webhook-over-custom-endpoint.md)) :
  un vrai sujet d'état de l'art sur les patterns d'intégration CI/CD (push vs pull, webhook natif
  vs API polling).
- **Runners self-hosted vs hébergés** — le compromis sécurité/fonctionnalité
  ([ADR-0005](docs/adr/0005-self-hosted-runner-and-webhook-forwarding.md)).

---

## 3. Conception (UML)

Aucun diagramme UML formel n'a été produit pendant le développement (l'approche a été
code-first, guidée par les vrais contrats REST/événements, pas par une modélisation UML amont) —
à produire maintenant, a posteriori, à partir du code réel :

- **Diagramme de cas d'utilisation** — acteurs = les 5 rôles de `RoleHierarchy`
  (`ADMIN`, `RELEASE_MANAGER`, `TECH_LEAD`, `DEVELOPER`, `VIEWER`) ; cas d'utilisation = les
  actions de `frontend/src/constants/ticket.js` (`TICKET_ACTIONS`) — cette table est littéralement
  déjà la liste des cas d'usage par rôle/statut, à transposer en diagramme.
- **Diagramme de séquence** — le flux le plus riche à illustrer : création de ticket → soumission →
  approbation → déclenchement `workflow_dispatch` → exécution GitHub Actions → webhook
  `workflow_run` → mise à jour du statut → notification + audit. Tous les acteurs/messages sont
  déjà décrits dans `explication_phase_5.md`, `explication_phase_9.md` et
  [ADR-0004](docs/adr/0004-native-github-webhook-over-custom-endpoint.md).
- **Diagramme de classes** — par service : `Ticket`/`TicketStatus`/`TicketComment`/`TicketHistory`
  (ticket-service), `PipelineExecution`/`PipelineStage` (pipeline-service), `UserProfile`
  (user-service), `Notification` (notification-service), `AuditLog` (audit-service) — toutes ces
  entités existent déjà dans `backend/*/src/main/java/.../entity/`, il s'agit de les transposer, pas
  de les concevoir.
- **Diagramme de déploiement** — reprendre et adapter le diagramme Mermaid du `README.md`
  (§ Architecture), qui reflète déjà la vraie topologie (Gateway, Eureka, 5 services, 5 Postgres,
  RabbitMQ, Keycloak, GitHub Actions).
- **Diagramme de composants** — les modules Maven (`backend/pom.xml` §`<modules>`) + le frontend +
  l'infra (`docker-compose.yml`).

---

## 4. Réalisation

**Points à couvrir** :
- Captures d'écran de l'application réelle (Dashboard, création de ticket, barre d'actions
  contextuelle, page Audit Logs, etc.) — à faire une fois l'application lancée (voir
  `explication_phase_8.md` §8 pour la séquence de démarrage complète).
- Extraits de code commentés — candidats naturels : `TicketStateMachine` (machine d'état pure),
  `GitHubActionsClient`/`WebhookController` (l'intégration CI/CD réelle), le
  `DefaultJackson2JavaTypeMapper` du pattern DTO miroir.
- L'identité visuelle du frontend (palette BIAT extraite du logo, voir `explication_phase_8.md`
  §1) — un exemple concret de décision de design, pas juste un choix esthétique arbitraire.
- Les écarts assumés par rapport au plan initial (à présenter comme des décisions réfléchies, pas
  des ratés) : webhook natif plutôt que callback custom, pas de fichiers compose par environnement
  ([ADR-0006](docs/adr/0006-no-per-environment-compose-split.md)), runner self-hosted.

---

## 5. Tests & validation

Matière déjà entièrement produite, à synthétiser :
- **Tests unitaires** — JUnit 5 + Mockito, un par couche (service/repository/controller) pour
  chacun des 5 services métier ; chiffres exacts disponibles dans les sections « Files
  modified/created » de `progress.md` par phase (ex. Phase 7 : 20 tests ; Phase 4 : 43 tests).
- **Tests d'intégration** — Testcontainers (vraie Postgres + vrai RabbitMQ) + Maven Failsafe,
  5 classes `*IntegrationIT` (Phase 10) ; **mentionner honnêtement** le blocage rencontré (couche
  de sécurité Docker Desktop empêchant la vérification locale dans cet environnement précis — voir
  `explication_phase_10.md` §1) plutôt que de prétendre que tout a été vérifié.
- **Tests frontend** — Vitest + React Testing Library, 18 tests sur les zones à plus haut risque de
  régression silencieuse (voir `explication_phase_10.md` §2).
- **CI/CD** — `ci.yml` (build/test par service, filtrage par chemin, SonarQube, Trivy, OWASP
  Dependency-Check) et `deploy.yml` (le vrai déclencheur du déploiement) — voir
  `explication_phase_9.md`.
- **Analyse de sécurité** — SonarQube (qualité de code), Trivy (vulnérabilités des dépendances),
  OWASP Dependency-Check ; noter qu'ils sont volontairement non bloquants à ce stade (jamais
  exécutés sur ce code avant), une décision à justifier, pas à cacher.

---

## 6. Conclusion & perspectives

**Points à couvrir** (réflexion personnelle à écrire, mais voici les faits bruts pour l'étayer) :
- Bilan : périmètre réellement livré (10 phases, 5 microservices + frontend + CI/CD + tests +
  documentation) vs périmètre initial du plan maître.
- Limites honnêtes à assumer, pas à dissimuler :
  - OpenAPI/Swagger jamais fait (périmètre explicitement laissé de côté en Phase 10).
  - Vérification live des tests d'intégration bloquée par l'environnement Docker Desktop local
    (documenté dans `explication_phase_10.md`).
  - Une CVE connue sur `react-router-dom`, signalée mais pas corrigée.
  - Pas de vraie séparation d'infrastructure DEV/TEST/PROD ([ADR-0006](docs/adr/0006-no-per-environment-compose-split.md)).
- Perspectives : OpenAPI/Swagger, tests E2E (Cypress), vraie infra multi-environnement, thème
  Keycloak personnalisé, gating strict des scanners de sécurité une fois les premiers rapports
  triés.

---

## Annexes suggérées

- Glossaire (JWT, OIDC, Eureka, Feign, Testcontainers, etc.)
- Références bibliographiques (documentation Spring Cloud, GitHub Actions, RabbitMQ, etc.)
- Journal de bord complet : `progress.md` (déjà tenu au jour le jour tout au long du projet — une
  base précieuse pour la chronologie du rapport).
