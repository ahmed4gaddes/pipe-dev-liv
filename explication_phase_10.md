# Explication Détaillée : Phase 10 (Tests d'intégration, tests frontend, documentation)

Le plan maître regroupait cinq chantiers sous « Phase 10 » (tests d'intégration, documentation
OpenAPI, tests frontend, README/ADR, rapport de stage personnel). Trois ont été choisis pour cette
phase : **tests d'intégration Testcontainers**, **tests automatisés frontend**, **README + ADR**.
OpenAPI/Swagger et le rapport de stage restent hors périmètre (le second est un document personnel
de l'utilisateur, pas un livrable de code).

---

## 1. Tests d'intégration backend — Testcontainers + Maven Failsafe

### Pourquoi un plugin séparé (Failsafe), pas juste plus de tests Surefire

Les tests unitaires existants (`*Test.java`) tournent sur H2 en mémoire ou des mocks — rapides,
mais ils ne prouvent rien sur le vrai mapping JPA, les vraies contraintes Postgres, ou le vrai
comportement RabbitMQ (sérialisation, routing, consommation asynchrone). Les nouveaux tests
(`*IT.java`) utilisent de vrais conteneurs Postgres/RabbitMQ (Testcontainers) — plus lents,
nécessitent Docker. Convention Maven standard : Surefire exécute `*Test.java` à la phase `test`,
**Failsafe** exécute `*IT.java` à la phase `integration-test`/`verify`. `mvn verify` (déjà la
commande utilisée partout, y compris `ci.yml`) déclenche les deux automatiquement — aucune
modification de la CI n'a été nécessaire.

`backend/pom.xml` (parent, hérité par les 8 modules) a reçu :
- `maven-failsafe-plugin`, exécutions `integration-test` + `verify`.
- Une deuxième paire d'exécutions JaCoCo (`prepare-agent-integration`/`report-integration`,
  agent séparé `failsafeArgLine`) pour ne pas mélanger couverture unitaire et couverture
  d'intégration — produit `target/site/jacoco-it/jacoco.xml`.
- `org.testcontainers:{junit-jupiter,postgresql,rabbitmq}` et `org.awaitility:awaitility` en
  dépendances de test. Aucune version explicite pour les artefacts Testcontainers — gérée par le
  BOM `testcontainers-bom`, importé transitivement via `spring-boot-dependencies` (hérité de
  `spring-boot-starter-parent`) ; vérifié directement (`dependency:resolve` réussit).

### Les 5 classes `*IntegrationIT`

- **`UserServiceIntegrationIT`** (Postgres seule) — persiste/relit un `UserProfile`, vérifie la
  contrainte unique `keycloak_id` en conditions réelles.
- **`TicketServiceIntegrationIT`** (Postgres + RabbitMQ) — le test le plus complet : crée un ticket
  via `TicketService.createTicket`, le fait transiter `DRAFT → SUBMITTED` via `changeStatus`, et
  vérifie que les deux événements réels (`ticket.created`, `ticket.status-changed`) arrivent sur
  l'exchange `pipe-dev-liv.events`, capturés par une queue de test anonyme liée avec le pattern
  `ticket.*`.
- **`PipelineServiceIntegrationIT`** (Postgres seule) — persiste/relit une `PipelineExecution`.
  Ne touche **jamais** `GitHubActionsClient` (appeler la vraie API GitHub depuis un test automatisé
  serait un vrai réseau externe, un jeton réel, et le risque de déclencher un run réel — hors de
  question ici).
- **`NotificationServiceIntegrationIT`** / **`AuditServiceIntegrationIT`** (Postgres + RabbitMQ) —
  publient un événement `ticket.status-changed` via le `RabbitTemplate` **déjà configuré** du
  service (donc avec son vrai `DefaultJackson2JavaTypeMapper`), et vérifient qu'un `@RabbitListener`
  réel consomme le message et persiste une vraie ligne (`Notification`/`AuditLog`). Le mapping
  bidirectionnel de `setIdClassMapping` (Phase 6/7) fait que publier un `TicketEventPayload` local
  produit exactement le même header `__TypeId__` qu'un vrai message de `ticket-service` — testé
  sans dupliquer manuellement les en-têtes AMQP.

### Blocage rencontré : Testcontainers ne trouve pas Docker sous Windows

Docker Desktop tourne (`docker info` fonctionne, `docker ps` liste les conteneurs), mais
Testcontainers (bibliothèque Java `docker-java`) échoue à s'y connecter depuis ce shell : essayé
`DOCKER_HOST=npipe:////./pipe/docker_engine`, `npipe:////./pipe/dockerDesktopLinuxEngine`, et
`npipe:////./pipe/docker_cli` — chacun échoue soit par timeout soit avec une réponse `/info`
quasi-vide (seul le champ `Labels` renseigné). Signature typique d'un Docker Desktop récent qui
restreint l'accès direct au pipe nommé aux processus qu'il reconnaît (CLI Docker officiel), pas à
un process Java arbitraire — pas un défaut du code de test. Solution standard : activer
`Settings → General → "Expose daemon on tcp://localhost:2375 without TLS"` dans Docker Desktop
(action GUI, ne peut pas être faite depuis ce terminal). L'utilisateur a choisi de l'activer ;
**[état à la fin de cette session : voir §7 « Ce qui reste ouvert »]**.

---

## 2. Tests automatisés frontend — Vitest + React Testing Library

`package.json` n'avait aucun outillage de test avant cette phase. Ajout : `vitest`,
`@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`, `jsdom`,
`axios-mock-adapter`. `vite.config.js` reçoit un bloc `test` (`environment: 'jsdom'`,
`setupFiles`) — réutilise la même config que le serveur de dev/build (un seul fichier de config,
pas de duplication).

### Un blocage réel, corrigé : le JSX runtime sous Vitest

Premier `npm run test` : `ReferenceError: React is not defined`, sur des fichiers sources
(`AuthContext.jsx`, `TicketActionBar.jsx`) qui n'importent jamais `React` — et qui compilent/
fonctionnent très bien via `npm run build`/`npm run dev`. Le runtime JSX automatique (qui rend cet
import inutile) fonctionne dans le pipeline Vite normal mais pas sous Vitest avec cette combinaison
de versions (Vite 8 / `@vitejs/plugin-react` 6 / Vitest 2). Corrigé en forçant explicitement
`esbuild.jsx: 'automatic'` dans `vite.config.js` — pas en ajoutant des imports `React` dispersés
dans le code source (ça aurait été un contournement local d'un problème de configuration globale).

### Cibles de test (les trois zones désignées par l'utilisateur comme les plus susceptibles de
régresser silencieusement)

- **`constants/ticket.js`** (`TICKET_ACTIONS`) — teste les invariants métier réels : statuts
  terminaux (`CANCELLED`/`CLOSED`) sans action, statuts de déploiement en cours (`DEPLOYING_*`)
  également sans action (correction faite *dans le test*, pas dans le code produit — ces trois
  statuts n'ont jamais eu d'entrée dans `TICKET_ACTIONS`, un choix correct puisque
  `TicketActionBar` traite une clé absente comme `[]`, pas un oubli), règle du propriétaire non
  TECH_LEAD (`SUBMITTED`/`CANCELLED` uniquement), `REJECTED → DRAFT` réservé à TECH_LEAD+.
- **`auth/AuthContext.jsx`** (`hasRole`) — mock du module `keycloak` singleton, vérifie l'ordre
  `VIEWER < DEVELOPER < TECH_LEAD < RELEASE_MANAGER < ADMIN` pour plusieurs profils de rôles.
- **`api/client.js`** (intercepteurs) — via `axios-mock-adapter` : déballage de l'enveloppe
  `ApiResponse` réussie, rejet avec le message d'erreur si `success:false`, rejet exploitable sur
  échec HTTP, en-tête `Authorization` correctement attaché depuis `keycloak.token`.
- **`components/tickets/TicketActionBar.jsx`** — rendu réel (React Testing Library) pour 4
  combinaisons statut/rôle/propriétaire représentatives, y compris qu'un ticket `CLOSED` n'affiche
  rien pour personne.

18 tests, tous verts. `npm run lint` et `npm run build` restent propres après ajout (mêmes tailles
de bundle qu'en Phase 8 — pas de régression).

### Note sécurité : `npm audit`

`npm install` a signalé 8 vulnérabilités. Une (`brace-expansion`, DoS) corrigée sans risque via
`npm audit fix`. Les deux autres nécessitent des changements cassants (`vitest`→4.x pour la chaîne
`esbuild`/`vite` interne à Vitest — n'affecte que son serveur de dev, pas le code de prod ; et
`react-router-dom` a une CVE dans la plage `7.12.0-8.2.0` — c'est une dépendance de production déjà
présente depuis la Phase 8, pas ajoutée cette phase). Signalé ici plutôt que corrigé
unilatéralement (un downgrade/upgrade de routeur mi-phase-tests est une décision distincte, pas
quelque chose à trancher silencieusement en passant).

---

## 3. README + ADR

`README.md` (auparavant 3 lignes vides de contenu) réécrit : présentation, diagramme
d'architecture Mermaid (corrigé pour refléter la vraie implémentation — webhook natif, pas
l'esquisse `curl` du plan maître), tableau de stack technique, séquence de démarrage local
(réutilise — ne duplique pas — les commandes déjà détaillées dans `explication_phase_8.md` §8),
liens vers chaque `explication_phase_N.md`.

`docs/adr/` (nouveau, 6 fichiers, format Nygard léger : Statut/Contexte/Décision/Conséquences) —
documente des décisions **déjà prises** dans les phases précédentes, pas de nouvelles décisions
prises pour l'occasion : microservices vs monolithe, pattern DTO miroir RabbitMQ, vérifications de
rôle côté client comme UX uniquement, webhook natif GitHub, runner self-hosted + `gh webhook
forward`, absence de fichiers compose par environnement.

---

## 4. Ce qui reste ouvert

- **Vérification live des 5 IT Testcontainers bloquée par l'environnement Docker Desktop de cette
  machine** (voir §1) — le code est écrit, compile, suit exactement les patterns Testcontainers
  standards (`@Testcontainers`, `@DynamicPropertySource`), mais n'a pas pu tourner jusqu'au bout
  dans cette session avant que ce document ne soit rédigé. Prochaine étape : une fois le port TCP
  du démon Docker Desktop exposé, relancer `./mvnw clean verify` sur les 5 services avec
  `DOCKER_HOST=tcp://localhost:2375`.
- **`react-router-dom` a un avis de sécurité connu** dans la plage installée (voir §2) — pas corrigé
  cette phase, à trancher séparément (upgrade vs downgrade, impact sur le reste du frontend).
- **Pas de tests OpenAPI/Swagger ni de rapport de stage** — explicitement hors périmètre, pas un
  oubli (voir l'introduction de ce document).
