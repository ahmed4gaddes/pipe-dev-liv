# Configuration du runner self-hosted

Les workflows `ci.yml` et `deploy.yml` tournent sur un runner **self-hosted**, parce qu'ils ont
besoin d'accéder à des services qui n'existent que sur la machine de dev : SonarQube
(`localhost:9000`), le démon Docker local, et la stack applicative elle-même.

Ce document décrit ce qu'il faut configurer **sur chaque machine** qui héberge un runner. Ces
réglages sont propres à la machine : ils ne sont pas — et ne doivent pas être — versionnés.

---

## 1. Enregistrer le runner

```bash
# Récupérer un token d'enregistrement :
# GitHub > Settings > Actions > Runners > New self-hosted runner
cd C:\actions-runner
.\config.cmd --unattended --url https://github.com/<owner>/<repo> --token <TOKEN> --labels self-hosted
```

Puis le démarrer :

```bash
cd C:\actions-runner
.\run.cmd
```

> Le runner s'arrête dès que la fenêtre est fermée. Pour qu'il survive aux fermetures de session,
> l'installer en service Windows (**nécessite une invite Administrateur**) :
> ```
> .\svc.cmd install
> .\svc.cmd start
> ```
> Si `svc.cmd` est absent de votre installation, il est généré par `config.cmd` : il faut alors
> désenregistrer (`config.cmd remove --token <TOKEN>`) puis réenregistrer le runner.

---

## 2. Variables d'environnement du runner

Créer le fichier **`C:\actions-runner\.env`**. GitHub Actions le charge automatiquement dans
l'environnement de chaque job.

```properties
# Chemin du clone qui héberge la stack locale — celui où vous lancez `docker compose`.
# Utilisé par deploy.yml pour récupérer le vrai .env (secrets) au lieu des placeholders
# de .env.example. Adapter à votre machine.
STACK_DIR=C:/Users/<vous>/IdeaProjects/pipe-dev-liv

# Testcontainers (tests d'intégration) échoue en 400 sur les named pipes Windows avec les
# versions récentes de Docker Desktop, qui exigent l'API >= 1.40 alors que le client
# embarqué interroge en v1.24. Ces deux variables forcent un transport et une version
# d'API compatibles.
DOCKER_HOST=tcp://localhost:2375
DOCKER_API_VERSION=1.44
```

`DOCKER_HOST` suppose que l'exposition TCP est activée dans **Docker Desktop → Settings →
General → « Expose daemon on tcp://localhost:2375 without TLS »**. Vérification :

```bash
curl http://localhost:2375/version
```

> Après toute modification de `.env`, **redémarrer le runner** — le fichier n'est lu qu'au
> démarrage.

---

## 3. Secrets et variables du dépôt

| Secret | Contenu |
|---|---|
| `SONAR_TOKEN` | Token d'analyse SonarQube. À générer dans SonarQube → *My Account* → *Security* → type **Global Analysis Token**. Un « User Token » est refusé par le scanner. |

---

## 4. Webhook GitHub (retour de statut des déploiements)

`pipeline-service` déclenche les runs via `workflow_dispatch`, et GitHub renvoie leur statut via
le webhook natif `workflow_run`. Le service tournant en local, il faut un tunnel public :

```bash
ngrok http 8080
```

Puis configurer le webhook dans **Settings → Webhooks** du dépôt :

- **Payload URL** : `https://<votre-tunnel>.ngrok-free.dev/api/webhooks/github`
- **Content type** : `application/json`
- **Secret** : la valeur de `GH_WEBHOOK_SECRET` de votre `.env`
- **Events** : *Workflow runs*

> L'URL ngrok change à chaque redémarrage du tunnel (offre gratuite) : il faut alors mettre à
> jour la Payload URL du webhook.
>
> Un webhook manqué (tunnel coupé, runner arrêté, 5xx transitoire) n'est plus bloquant : un job
> de réconciliation tourne toutes les minutes dans `pipeline-service` et rattrape l'état en
> interrogeant directement l'API GitHub. Voir `PipelineServiceImpl#reconcilePendingExecutions`.

---

## 5. Fichier `.env` de la stack

À la racine du clone (celui pointé par `STACK_DIR`), créer `.env` à partir de `.env.example` et
renseigner **toutes** les valeurs `CHANGE_ME`. Une en particulier dépend de la machine :

```properties
# Adresse à laquelle les services conteneurisés s'annoncent auprès d'Eureka. Doit être l'IP LAN
# de la machine (`ipconfig`) : leur hostname Docker interne serait injoignable depuis
# discovery-server et api-gateway, qui tournent nativement sur l'hôte.
EUREKA_INSTANCE_HOSTNAME=192.168.1.x
```

---

## Dépannage

| Symptôme | Cause |
|---|---|
| `STACK_DIR n'est pas défini` | `.env` du runner absent/incomplet, ou runner non redémarré depuis sa création. |
| `Could not find a valid Docker environment` (Testcontainers) | `DOCKER_HOST`/`DOCKER_API_VERSION` absents, ou exposition TCP désactivée dans Docker Desktop. |
| `Not authorized` à l'analyse SonarQube | `SONAR_TOKEN` invalide ou de type « User Token » au lieu de « Global Analysis Token ». |
| Services en crash-loop après un déploiement | `STACK_DIR` pointe sur un clone dont le `.env` contient encore des `CHANGE_ME`. |
| Ticket bloqué en « Déploiement ... » | Attendre une minute : la réconciliation rattrape. Si ça persiste, vérifier les logs de `pipeline-service` et l'accès à l'API GitHub. |
