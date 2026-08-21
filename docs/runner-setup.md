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
.\config.cmd --unattended --url https://github.com/<owner>/<repo> --token <TOKEN> --labels self-hosted,<votre-label>
```

`<votre-label>` est un identifiant propre à votre machine (par ex. son nom d'hôte en minuscules).
Il n'est pas décoratif : voir [§3](#3-plusieurs-runners-sur-le-même-dépôt).

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

# Token d'analyse du SonarQube QUI TOURNE SUR CETTE MACHINE (type Global Analysis Token).
# Chaque développeur ayant son propre SonarQube sur localhost:9000, un token généré par l'un
# est refusé par l'instance de l'autre : le secret SONAR_TOKEN du dépôt, unique et partagé,
# ne peut donc être valide que pour une seule machine. ci.yml lit d'abord cette variable et
# ne retombe sur le secret du dépôt que si elle est absente.
SONAR_TOKEN=sqa_...
```

`DOCKER_HOST` suppose que l'exposition TCP est activée dans **Docker Desktop → Settings →
General → « Expose daemon on tcp://localhost:2375 without TLS »**. Vérification :

```bash
curl http://localhost:2375/version
```

> Après toute modification de `.env`, **redémarrer le runner** — le fichier n'est lu qu'au
> démarrage.

---

### Git Bash doit primer sur WSL dans le PATH

`deploy.yml` exécute plusieurs étapes en `shell: bash`. Sur Windows, le runner résout `bash` via
le PATH — et si WSL est installé, `C:\WINDOWS\system32\bash.exe` (le lanceur WSL) est trouvé
avant Git Bash. WSL reçoit alors un chemin Windows, interprète les `\` comme des échappements, et
n'exécute jamais le script :

```
shell: C:\WINDOWS\system32\bash.EXE --noprofile --norc -e -o pipefail {0}
/bin/bash: C:actions-runner_work_temp<guid>.sh: No such file or directory
```

**Le symptôme est trompeur** : les logs affichent quand même `::error::STACK_DIR n'est pas
défini`, non pas parce que le garde-fou s'est déclenché, mais parce que GitHub imprime le contenu
du script avant de le lancer. On cherche un problème de configuration alors que le shell n'a
jamais démarré. Le vrai message est la ligne `No such file or directory` juste en dessous, sur un
chemin dont les backslashes ont disparu.

Diagnostic :

```bash
where.exe bash
```

Si `C:\WINDOWS\system32\bash.exe` sort en premier, il faut placer le dossier `bin` de Git devant
lui **dans le PATH du processus qui lance le runner**. Attention : ajouter Git à votre PATH
utilisateur ne suffit pas — Windows concatène le PATH utilisateur *après* le PATH système, donc
`system32` resterait prioritaire. Il faut soit modifier le PATH machine (invite Administrateur),
soit lancer le runner via un script qui pose l'environnement, par exemple
`C:\actions-runner\start-runner.cmd` :

```bat
@echo off
set "PATH=C:\Program Files\Git\bin;%PATH%"
set "STACK_DIR=C:/chemin/vers/votre/clone"
set "SONAR_TOKEN=sqa_..."
cd /d "C:\actions-runner"
call "C:\actions-runner\run.cmd"
```

> Écrire ce fichier avec des **fins de ligne Windows (CRLF)** et des chemins absolus. Enregistré
> en LF — ce que fait un éditeur configuré pour Unix, ou une redirection depuis Git Bash —
> `cmd.exe` échoue sur `'run.cmd' n'est pas reconnu en tant que commande interne ou externe`.

Un raccourci vers ce script dans le dossier `shell:startup` le relance à chaque ouverture de
session. C'est aussi la façon la plus fiable de définir `STACK_DIR` : les fichiers `.env` et
`.path` décrits plus haut ne sont lus de manière fiable que par le runner installé **en service**,
pas par un `run.cmd` lancé à la main.

---

## 3. Plusieurs runners sur le même dépôt

Chaque développeur peut enregistrer son propre runner : GitHub n'impose aucune limite. Mais ils
partagent alors **la même file de jobs**, parce qu'ils portent tous le label `self-hosted` que
demandent les workflows. GitHub attribue le job au premier runner libre, et un déploiement
déclenché depuis une machine peut donc s'exécuter sur celle de quelqu'un d'autre — qui verra sa
propre stack redéployée, tandis que l'auteur du déploiement ne verra rien bouger chez lui.

Les *runner groups*, qui servent exactement à cloisonner ça, ne sont disponibles que sur les
dépôts d'organisation, pas sur un dépôt personnel.

`deploy.yml` résout le problème avec l'input `runner_label` :

```yaml
runs-on: ${{ inputs.runner_label }}
```

`pipeline-service` y envoie la valeur de `github.runner-label`, alimentée par `GH_RUNNER_LABEL`
dans le `.env` de la stack. Renseignez-y le label propre à votre machine — le même que celui
passé à `config.cmd --labels` — et vos déploiements viseront votre runner, quel que soit le
nombre d'autres runners en ligne :

```properties
GH_RUNNER_LABEL=hunter44
```

La valeur par défaut est `self-hosted`, ce qui reproduit le comportement d'origine (premier
runner libre). Une instance qui n'envoie pas l'input — un `pipeline-service` plus ancien — reste
donc fonctionnelle sans modification.

> Pour un test réellement isolé, il reste préférable que les autres runners soient arrêtés :
> `ci.yml` se déclenche sur `push` et non par `workflow_dispatch`, il ne peut donc pas recevoir
> d'input et continue de partir sur n'importe quel runner disponible.

---

## 4. Secrets et variables du dépôt

| Secret | Contenu |
|---|---|
| `SONAR_TOKEN` | Token d'analyse SonarQube. À générer dans SonarQube → *My Account* → *Security* → type **Global Analysis Token**. Un « User Token » est refusé par le scanner. |

> Ce secret n'est qu'un **repli**. Étant unique pour tout le dépôt, il ne peut correspondre qu'à
> une seule instance SonarQube — celle d'un seul développeur. Dès que vous en faites tourner une
> sur votre machine, définissez `SONAR_TOKEN` dans l'environnement de votre runner ([§2](#2-variables-denvironnement-du-runner)) :
> `ci.yml` la préfère au secret.

---

## 5. Webhook GitHub (retour de statut des déploiements)

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

## 6. Fichier `.env` de la stack

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
| `No such file or directory` sur un `.sh` dont le chemin a perdu ses backslashes | `shell: bash` résolu vers le bash de WSL au lieu de Git Bash. Le message `STACK_DIR n'est pas défini` affiché juste avant est trompeur. Voir [§2](#git-bash-doit-primer-sur-wsl-dans-le-path). |
| `Not authorized` à l'analyse SonarQube | `SONAR_TOKEN` invalide ou de type « User Token » au lieu de « Global Analysis Token ». |
| `ports are not available: ... bind` au déploiement | Les microservices tournent déjà en natif (IDE) sur les ports 8081-8085 que `deploy.yml` veut donner aux conteneurs. Arrêter les instances natives, sauf `discovery-server` et `api-gateway`. |
| Services en crash-loop après un déploiement | `STACK_DIR` pointe sur un clone dont le `.env` contient encore des `CHANGE_ME`. |
| Déploiement exécuté sur la machine d'un autre développeur | `GH_RUNNER_LABEL` absent du `.env` de la stack, ou laissé à `self-hosted`, alors qu'un autre runner est en ligne. Voir [§3](#3-plusieurs-runners-sur-le-même-dépôt). |
| Ticket bloqué en « Déploiement ... » | Attendre une minute : la réconciliation rattrape. Si ça persiste, vérifier les logs de `pipeline-service` et l'accès à l'API GitHub. |
