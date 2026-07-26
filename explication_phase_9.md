# Explication Détaillée : Phase 9 (CI/CD — GitHub Actions)

Cette phase n'ajoute **aucun code backend** — elle livre uniquement ce qui manquait pour que l'intégration GitHub Actions déjà construite en Phase 5 (`pipeline-service`) fonctionne réellement : les deux fichiers de workflow, la configuration Maven nécessaire à l'analyse de couverture, et — hors dépôt Git — un runner self-hosted + la réception locale du webhook GitHub.

---

## 1. Ce qui existait déjà (et pourquoi ça change le plan)

En relisant `GitHubActionsClient.java`, `WebhookController.java` et `PipelineServiceImpl.handleWorkflowRunEvent(...)` (tous écrits en Phase 5), trois choses étaient déjà entièrement construites, jamais exercées faute de `deploy.yml` :

- `GitHubActionsClient.triggerWorkflow(ref, inputs)` appelle `POST /repos/{owner}/{repo}/actions/workflows/deploy.yml/dispatches` avec `inputs = {environment, ticket_id}`. Le nom du fichier (`deploy.yml`) est en dur dans `application.yml` (`github.workflow-file`) — c'était un 404 documenté et attendu (Javadoc de la classe) tant que ce fichier n'existait pas.
- `WebhookController` (`POST /api/webhooks/github`) écoute déjà l'événement **natif** `workflow_run` de GitHub, vérifie la signature HMAC (`X-Hub-Signature-256`, secret `GH_WEBHOOK_SECRET`), et route vers `PipelineServiceImpl.handleWorkflowRunEvent`.
- Cette méthode mappe déjà `in_progress` → `RUNNING`, et à `completed`, la conclusion GitHub (`success`/`failure`/`cancelled`) → `PipelineStatus.SUCCESS`/`FAILED`/(inchangé), va chercher le détail des jobs via `getRunJobs` (`persistStages`), notifie `ticket-service`, et publie les événements RabbitMQ.

**Conséquence sur le plan** : le document maître (`implementation_plan stage micr.md`, section PHASE 9) esquissait un `deploy.yml` qui fait un `curl` manuel vers un endpoint `/api/webhooks/deployment` en fin de job. Cet endpoint n'existe pas et n'est pas nécessaire — l'implémentation réelle, plus élégante, s'appuie sur le webhook natif GitHub. Le `deploy.yml` livré ici est donc plus simple que l'esquisse d'origine : pas d'étape "Notify Backend".

---

## 2. Les deux blocages d'architecture, et la décision prise

Deux problèmes de portée réseau, indépendants du code :

1. **SonarQube** tourne sur `localhost:9000` — injoignable depuis un runner GitHub-hosted (qui tourne dans le cloud de GitHub).
2. **Le webhook `workflow_run`** est envoyé par les serveurs de GitHub eux-mêmes vers l'URL configurée sur le dépôt — quel que soit l'endroit où s'exécute le job (hosted ou self-hosted), GitHub ne peut jamais atteindre un `localhost` directement.

Décision (validée avec l'utilisateur, option **"Fully live"**) : un **runner self-hosted** enregistré sur cette machine résout le problème (1) — le runner peut faire un `curl localhost:9000` ou `localhost:8080` comme n'importe quel processus local. Pour (2), pas de webhook permanent configuré dans les réglages du dépôt : **`gh webhook forward`** (fonctionnalité officielle du GitHub CLI, conçue exactement pour ce cas — recevoir des webhooks de dépôt en local pendant le développement) crée un webhook **éphémère** pour la durée de la commande et relaie chaque événement vers `http://localhost:8080/api/webhooks/github`. C'est plus simple que ce que le plan initial envisageait (un webhook permanent dans Settings → Webhooks) : rien à créer/supprimer manuellement dans les réglages GitHub, la commande gère elle-même l'enregistrement et la désinscription du webhook.

---

## 3. `.github/workflows/ci.yml`

Reprend la structure à filtres de chemin du plan maître, adaptée aux vrais chemins du dépôt (`backend/<module>/**`, `frontend/**`, alors que le plan maître supposait une racine plate) :

- **`detect-changes`** (`dorny/paths-filter@v3`) — un output booléen par module (`common`, `discovery`, `gateway`, `user`, `ticket`, `pipeline`, `notification`, `audit`, `frontend`).
- **`build-<module>`** — un job par module Maven (8 au total), chacun `cd backend && ./mvnw clean verify -pl common-lib,<module>`. Un changement dans `common-lib` reconstruit les 7 autres (`if: always() && (outputs.<x> == 'true' || outputs.common == 'true')`, même idiome que le plan maître). `build-common` lui-même ne tourne que si `common-lib` a changé (`install -DskipTests`, juste pour publier l'artefact dans le repo Maven local du runner). Chaque job applicatif (user/ticket/pipeline/notification/audit) publie son `jacoco.xml` en artefact de build.
- **`build-frontend`** — `npm ci && npm run lint && npm run build`, les mêmes commandes déjà vérifiées manuellement en Phase 8.
- **`sonar-analysis`** — utilise le **plugin Maven Sonar** (`sonar:sonar` sur le reactor complet), pas l'action générique `SonarSource/sonarqube-scan-action` du plan maître (celle-ci est pensée pour des projets non-Java à fichier `sonar-project.properties` unique ; le plugin Maven analyse les 6 modules applicatifs comme un seul projet multi-module avec la couverture JaCoCo). Cible `http://localhost:9000`, jeton via `secrets.SONAR_TOKEN` — ne fonctionne que parce que le runner est self-hosted.
- **`dependency-check`** (OWASP) et **`trivy-scan`** (matrice sur les 6 services backend + `frontend`, scan filesystem) — tous deux **non bloquants** (`continue-on-error: true` / `exit-code: '0'`) : ces scanners n'ont jamais tourné sur ce code, un backlog de CVE non trié bloquerait immédiatement toute future PR. Rapports publiés en artefacts, à trier plus tard — un vrai gating (`exit-code: '1'` sur CRITICAL/HIGH) est une extension future raisonnable, pas un manque silencieux.

**Note sécurité documentée directement en commentaire dans `ci.yml`** : un runner self-hosted exécute le code du workflow avec les privilèges de la machine qui l'héberge — acceptable ici uniquement parce que c'est un dépôt privé, mono-contributeur, jamais de PR externe non fiable. Ne pas réutiliser cette configuration telle quelle sur un dépôt public acceptant des PR externes sans les garde-fous habituels (règles de protection d'environnement, revue obligatoire).

---

## 4. `.github/workflows/deploy.yml`

Doit s'appeler exactement `deploy.yml` (`github.workflow-file` en dur côté `pipeline-service`) et déclarer des `inputs` qui correspondent à ce que `PipelineServiceImpl` envoie réellement (`Map.of("environment", ..., "ticket_id", ...)`) :

```yaml
on:
  workflow_dispatch:
    inputs:
      environment: { type: choice, options: [dev, test, prod], default: dev }
      ticket_id: { required: true, type: string }
```

Jobs : build des images Docker des 5 microservices applicatifs → `docker compose up -d` → health check (poll `http://localhost:8080/actuator/health`, 6 tentatives) → résumé dans `$GITHUB_STEP_SUMMARY`. Pas d'étape de notification manuelle (voir §1) — un échec de job se traduit naturellement en conclusion `failure`, que le webhook natif remonte à `handleWorkflowRunEvent` sans plomberie supplémentaire.

**Décision de portée explicite** : pas de `docker-compose.dev.yml` / `.test.yml` / `.prod.yml` séparés (contrairement au plan maître). Ce projet tourne comme une seule stack locale — il n'existe pas de vraies infrastructures DEV/TEST/PROD séparées. Créer trois fichiers compose quasi-identiques pour des environnements qui n'existent pas réellement aurait été du théâtre, pas une vraie capacité. `environment` reste une étiquette (logs, résumé du run) plutôt qu'un déclencheur d'infrastructure différente.

---

## 5. `backend/pom.xml` — `jacoco-maven-plugin`

Absent du projet jusqu'ici (vérifié par grep). Ajouté dans `<build><plugins>` (pas `<pluginManagement>`) du POM parent pour être hérité automatiquement par les 8 modules sans qu'aucun n'ait à le redéclarer — `prepare-agent` instrumente les tests, `report` (lié à la phase `test`) génère `target/site/jacoco/jacoco.xml`, consommé par `sonar:sonar`. Vérifié en conditions réelles cette session : `./mvnw -pl common-lib,audit-service test` a bien produit `audit-service/target/site/jacoco/jacoco.xml` (23 Ko, 20 tests) sans casser le build de `common-lib` (qui n'a pas de tests — le plugin le détecte proprement, "Skipping JaCoCo execution due to missing execution data file", pas une erreur).

---

## 6. Ce que je n'ai pas fait moi-même — étapes manuelles

Deux catégories d'actions volontairement laissées à l'utilisateur : elles mutent soit un service GitHub externe avec des identifiants qui ne doivent pas transiter par le chat, soit nécessitent un jeton à usage unique généré de façon interactive.

### 6.1 Enregistrer le runner self-hosted (une fois)

1. Sur GitHub : `Settings` → `Actions` → `Runners` → `New self-hosted runner`, choisir `Windows` / `x64`. GitHub affiche alors les commandes exactes (URL de téléchargement versionnée + jeton d'enregistrement à usage unique, générés à la volée — impossibles à connaître à l'avance).
2. Dans un dossier dédié (ex. `C:\actions-runner`), exécuter les commandes affichées, de la forme :
   ```powershell
   mkdir C:\actions-runner ; cd C:\actions-runner
   Invoke-WebRequest -Uri <URL fournie par GitHub> -OutFile actions-runner.zip
   Expand-Archive -Path actions-runner.zip -DestinationPath .
   ./config.cmd --url https://github.com/<owner>/<repo> --token <TOKEN fourni par GitHub>
   ```
3. L'installer comme service Windows pour qu'il soit toujours disponible :
   ```powershell
   ./svc.cmd install
   ./svc.cmd start
   ```
4. Vérifier dans `Settings` → `Actions` → `Runners` qu'il apparaît "Idle" avec le label par défaut `self-hosted` (celui utilisé par `runs-on: self-hosted` dans les deux workflows).

### 6.2 Ajouter le secret `SONAR_TOKEN`

Généré côté SonarQube local (`http://localhost:9000` → mon compte → `My Account` → `Security` → générer un jeton), puis :
```powershell
gh secret set SONAR_TOKEN --repo <owner>/<repo>
```
(colle le jeton quand `gh` le demande — ne le partage pas dans le chat). Le projet Sonar `pipe-dev-liv` se crée automatiquement à la première analyse (comportement par défaut de la Community Edition).

### 6.3 Recevoir le webhook en local pendant une démo

Pas de configuration permanente à faire dans `Settings → Webhooks` (voir §2) — à chaque session où l'on veut un flux bout en bout réellement live :
```powershell
gh webhook forward --repo <owner>/<repo> --events workflow_run --secret <valeur de GH_WEBHOOK_SECRET dans .env> --url http://localhost:8080/api/webhooks/github
```
À laisser tourner dans un terminal pendant la démo (Ctrl+C l'arrête et désinscrit le webhook éphémère).

---

## 7. Ce qui reste ouvert

- **Pas d'exécution réelle cette session** — aucun de les 3 étapes manuelles ci-dessus (runner, secret, `gh webhook forward`) n'a été fait ; ce document donne la marche à suivre exacte, mais le flux "créer ticket → approuver → déployer → statut mis à jour en live" n'a pas encore été vérifié de bout en bout sur ce projet.
- **`ci.yml` n'a jamais tourné** — la syntaxe YAML est validée (parsée sans erreur), mais la première exécution réelle sur le runner self-hosted (une fois enregistré) est le seul vrai test ; des ajustements sont possibles (versions d'actions, chemins).
- **Scanners non bloquants par choix** (§3) — resterait à décider, une fois les premiers rapports vus, quels seuils faire réellement échouer une PR.
- **Pas de matrice de vrais environnements DEV/TEST/PROD** (§4, décision assumée, pas un oubli) — si ce besoin devient réel, ce serait une phase à part entière (vraies infrastructures cibles), pas une extension mineure de celle-ci.
