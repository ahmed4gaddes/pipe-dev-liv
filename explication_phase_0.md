# Comprendre la Phase 0 : Infrastructure Docker Compose

La Phase 0 consiste à préparer le terrain en installant toute l'infrastructure nécessaire au projet. Au lieu d'installer les logiciels un par un sur chaque PC (ce qui prendrait des heures), nous utilisons **Docker Compose**. Avec une seule commande (`docker compose up -d`), tout s'installe et démarre automatiquement.

Voici l'utilité de chaque service installé :

## 1. Les Bases de Données (5x PostgreSQL)
Dans une architecture microservices, la règle d'or est le **"Database per Service"** (une base par service). Si un service tombe en panne, les autres continuent de fonctionner.

*   **PostgreSQL Users (port 5433)** : Stocke les profils des utilisateurs.
*   **PostgreSQL Tickets (port 5434)** : Stocke les tickets de déploiement (titre, statut, historique...).
*   **PostgreSQL Pipelines (port 5435)** : Stocke les exécutions des pipelines CI/CD (succès, échec, logs...).
*   **PostgreSQL Notifications (port 5436)** : Stocke les notifications envoyées aux utilisateurs.
*   **PostgreSQL Audit (port 5437)** : Stocke la traçabilité complète (qui a fait quoi et quand).

## 2. Keycloak (IAM / SSO) - port 9090
Keycloak est le **gardien de l'application**. Il gère :
*   L'authentification (login / mot de passe).
*   La gestion des rôles (DEVELOPER, ADMIN, TECH_LEAD, etc.).
*   La génération de tokens sécurisés (JWT). 
Sans Keycloak, n'importe qui pourrait accéder à votre application.

## 3. RabbitMQ (Message Broker) - port 15672
RabbitMQ est le **facteur (messager)** entre vos microservices.
Dans une architecture moderne, les services ne s'appellent pas toujours directement (pour éviter le couplage fort). 
*Exemple :* Quand un ticket est approuvé, le *Ticket Service* dépose un message dans RabbitMQ. Le *Notification Service* voit ce message et envoie un email. Si le *Notification Service* est en panne, le message reste en attente dans RabbitMQ et sera traité au redémarrage.

## 4. SonarQube (Qualité du code) - port 9000
SonarQube est **l'inspecteur de code**. Il analyse automatiquement le code source pour y détecter :
*   Des bugs.
*   Des failles de sécurité (vulnérabilités).
*   Du code dupliqué (code smells).
C'est un élément obligatoire pour s'assurer que le code est propre avant d'être déployé en production.

## 5. Zipkin (Distributed Tracing) - port 9411
Zipkin est le **détective**. Dans une architecture avec plusieurs microservices, une simple requête utilisateur peut traverser 4 services différents. 
Si c'est lent, comment savoir quel service bloque ? Zipkin trace le parcours exact de la requête à travers tous les services et mesure le temps passé à chaque étape.

---

### Le Flux (Exemple concret)

1. L'utilisateur clique sur "Approuver le ticket" dans le frontend.
2. 🔐 **Keycloak** vérifie le token (JWT) pour s'assurer qu'il a le droit.
3. 🎫 **Ticket Service** met à jour la BDD **PostgreSQL Tickets**.
4. 📨 Il envoie un événement à **RabbitMQ**.
5. 🔔 **Notification Service** lit RabbitMQ et envoie la notification.
6. 📋 **Audit Service** lit RabbitMQ et enregistre l'action.
7. 🔍 Pendant ce temps, **SonarQube** s'assure que le code respecte les normes.
8. 🔭 **Zipkin** surveille toute l'opération pour vérifier qu'il n'y a pas de goulot d'étranglement.

---

## 6. Stratégie Gitflow (Les Branches)

Nous avons mis en place une organisation précise pour les branches afin de ne jamais casser le code partagé avec l'équipe.

| Branche | Utilité | Qui l'utilise ? | Durée de vie |
| :--- | :--- | :--- | :--- |
| `main` | Le code de **Production**. Il est 100% stable. On ne travaille jamais directement dessus. | Les administrateurs | Permanente |
| `develop` | Le code de **Développement**. C'est ici que l'on rassemble tout le travail de l'équipe. | Toute l'équipe | Permanente |
| `feature/nom-de-la-tache` | Un brouillon pour travailler sur **une seule tâche** (ex: `feature/user-service`). Une fois la tâche finie, on la fusionne dans `develop` via une Pull Request (PR). | Le développeur | Temporaire (supprimée après la PR) |
| `release/x.x.x` | Branche pour préparer une version avant la production (Tests). | Le Tech Lead | Temporaire |

---

## 7. Configuration de GitHub (Environnements, Variables et Secrets)

Pour que notre pipeline CI/CD sache où déployer le code et avec quels mots de passe, nous avons configuré 3 environnements sur GitHub.

### A. Les Environnements
1.  **`development` (DEV)** : Pour tester les nouveautés en continu (déploiement automatique depuis la branche `develop`).
2.  **`testing` (TEST)** : Pour valider les fonctionnalités avant la mise en production (déploiement depuis les branches `release/*`).
3.  **`production` (PROD)** : Le produit final (déploiement depuis `main`). **Particularité : ce déploiement nécessite l'approbation manuelle d'un humain.**

### B. Les Secrets (Mots de passe et Tokens cachés)
Ces valeurs sont ajoutées dans les paramètres de chaque environnement sur GitHub. Les *Tokens* sont identiques partout, mais les *mots de passe* changent selon l'environnement par sécurité.

| Nom du Secret | Description | Valeur en DEV | Valeur en TEST | Valeur en PROD |
| :--- | :--- | :--- | :--- | :--- |
| `DB_PASSWORD` | Mot de passe pour PostgreSQL | `dev_db_pass` | `test_db_pass` | `prod_db_pass_securise` |
| `KC_ADMIN_PASSWORD` | Mot de passe Admin Keycloak | `dev_admin_kc` | `test_admin_kc` | `prod_admin_kc` |
| `KC_DB_PASSWORD` | Mot de passe BDD Keycloak | `dev_db_kc` | `test_db_kc` | `prod_db_kc` |
| `RABBIT_PASSWORD` | Mot de passe RabbitMQ | `dev_rabbit` | `test_rabbit` | `prod_rabbit` |
| `GH_PAT_TOKEN` | Token API GitHub | `github_pat_xxxx` | `github_pat_xxxx` | `github_pat_xxxx` |
| `GH_WEBHOOK_SECRET` | Secret pour valider les envois GitHub | `votre_secret` | `votre_secret` | `votre_secret` |
| `SONAR_TOKEN` | Token d'analyse SonarQube | `sqa_xxxx` | `sqa_xxxx` | `sqa_xxxx` |

### C. Les Variables (Valeurs publiques)
Tout comme les secrets, elles sont définies dans GitHub par environnement.
*   **DEV** : `SPRING_PROFILES_ACTIVE = dev`
*   **TEST** : `SPRING_PROFILES_ACTIVE = test`
*   **PROD** : `SPRING_PROFILES_ACTIVE = prod`

---

## 8. Commandes de Vérification de la Phase 0

Voici les commandes pour vérifier que tout est bien en place et fonctionne.

### A. Vérifier les Branches Git
```bash
# Affiche toutes les branches (locales et distantes)
git branch -a
# Affiche l'historique des derniers commits
git log --oneline -5
```

### B. Vérifier que le fichier `.env` est bien protégé
Le fichier contenant vos mots de passe locaux ne doit jamais être envoyé sur GitHub !
```bash
# Cette commande ne doit rien renvoyer si le fichier est bien ignoré
git ls-files | Select-String "\.env$"
```

### C. Vérifier la configuration Docker Compose
```bash
# Vérifie si le fichier docker-compose.yml contient des erreurs de syntaxe
docker compose config --quiet
# Liste les 11 services définis dans le fichier
docker compose config --services
# Liste les volumes (stockage persistant)
docker compose config --volumes
```

### D. Démarrer et arrêter l'infrastructure locale
```bash
# Démarrer tous les services en arrière-plan
docker compose up -d
# Vérifier l'état des services (en cours d'exécution ou arrêtés)
docker compose ps
# Arrêter tous les services
docker compose down
# Arrêter tous les services ET supprimer les données des bases de données
docker compose down -v
```
