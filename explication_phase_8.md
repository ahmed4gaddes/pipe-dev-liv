# Explication Détaillée : Phase 8 (Frontend — application React aux couleurs BIAT)

Cette phase remplace le scaffold Vite/React par défaut par la vraie application : authentification Keycloak, consommation des 5 microservices, et une identité visuelle réelle (pas une inspiration esthétique) — le logo fourni est celui de **BIAT Innovation & Technology**, confirmé par l'utilisateur comme son projet interne réel.

---

## 1. D'où viennent les couleurs

Le fichier fourni (`biat-logo.svg`) était en réalité un PNG (409×191) mal étiqueté — corrigé en `biat-logo.png`. Plutôt que de deviner les couleurs de marque, elles ont été **extraites directement des pixels du logo** via un échantillonnage `System.Drawing` en PowerShell (voir session) : navy `#005186` (dominant) et bleu ciel `#48B3E1` (le "swoosh"). L'or (`#D4AF37`) a été ajouté à la demande de l'utilisateur, sans code hex fourni — un or métallique classique a été choisi, utilisé **avec parcimonie** (badges de priorité/PROD, indicateur de nav actif, CTA clé) pour rester premium plutôt que criard.

---

## 2. Authentification : redirection Keycloak, pas de formulaire maison

`keycloak-js` gère le flux Authorization Code + PKCE — la seule approche correcte pour une SPA. `AuthContext` initialise avec `onLoad: 'check-sso'` (vérifie la session existante via un iframe silencieux, `public/silent-check-sso.html`), rafraîchit le token de façon proactive (`updateToken(30)`) à la fois sur expiration et avant chaque appel API (`api/client.js`, intercepteur de requête). Le formulaire de connexion réel reste celui, non modifié, hébergé par Keycloak — le reskin de ce formulaire demanderait un thème Keycloak (FTL), une stack différente, hors périmètre de cette phase. La page `Splash.jsx` (dégradé navy, logo, bouton "Se connecter" doré) est la seule vraie "page de connexion" côté app.

Les vérifications de rôle côté client (`hasRole()` dans `AuthContext`, qui reproduit l'ordre de `common-lib.RoleHierarchy`) ne servent qu'à l'UX (cacher un bouton) — chaque endpoint reste protégé par son propre `@PreAuthorize` côté backend, seule vraie frontière de sécurité.

---

## 3. Un trou découvert en cours de route : les commentaires de ticket

En câblant la couche API, `ticket-service` s'est révélé pouvoir **créer** un commentaire (`POST /{id}/comments`, Phase 4) sans jamais l'avoir exposé en lecture — `TicketCommentRepository.findByTicketIdOrderByCreatedAtAsc` existait déjà, jamais appelé par un contrôleur. Question posée à l'utilisateur : ajouter le petit endpoint manquant, ou abandonner la section Commentaires. Réponse : ajouter `GET /api/tickets/{id}/comments` (+ tests service et contrôleur, tous verts). C'est le seul endroit où cette phase a dû toucher un service backend déjà construit.

---

## 4. La barre d'actions du ticket reflète exactement `TicketStateMachine`

`constants/ticket.js` encode, statut par statut, les actions possibles — un miroir texte de `TicketStateMachine` (Phase 4) et des règles métier de `TicketServiceImpl` (ex. : un propriétaire non TECH_LEAD ne peut demander que `SUBMITTED` ou `CANCELLED`, `REJECTED → DRAFT` nécessite TECH_LEAD+ même pour le propriétaire). Ce n'est qu'un guide d'affichage : chaque clic déclenche le vrai endpoint (`approve`, `reject`, `deploy/{env}`, `PATCH /status`), et le backend revalide tout indépendamment. Les actions à fort impact (rejeter, annuler, déployer en PROD) passent par une modale de confirmation.

---

## 5. Le graphique du dashboard suit la méthode `dataviz`

La répartition des tickets par statut est un **bar chart horizontal**, pas un donut — la tâche est une comparaison de magnitude, pas une identité de série (voir `references/choosing-a-form.md` de la compétence `dataviz`, chargée avant d'écrire ce composant). Chaque barre porte la couleur de son "statut" (famille de couleur déjà utilisée par `StatusPill`) plutôt que huit teintes catégorielles arbitraires — cohérent avec la règle de la compétence "quand une série porte un sens bon/mauvais, elle porte un token de statut, pas une couleur catégorielle".

Le contraste des couleurs a été **vérifié par script**, pas à l'œil : `node scripts/validate_palette.js` (fonction `contrast()`) donne navy `8.33:1`, danger `4.38:1`, gris `4.62:1` — tous largement au-dessus du seuil — mais sky (`2.38:1`) et or (`2.10:1`) tombent sous 3:1. La compétence est explicite sur ce cas : un WARN de contraste n'est pas ignorable, il exige un canal de secours. Chaque barre porte donc une **étiquette directe** (le nombre, en texte, jamais juste la couleur) — c'est ce canal de secours, pas un changement de couleur qui aurait trahi les vraies teintes de la marque.

---

## 6. Découpage du bundle

Le premier build de production pesait 855 Ko (avertissement Vite) — `recharts`, utilisé uniquement par le Dashboard, en était le principal contributeur, et était chargé même par un visiteur non connecté qui ne voit que `Splash`. Passage à `React.lazy`/`Suspense` par page (`App.jsx`) : le bundle initial est descendu à 254 Ko, `recharts` n'arrive qu'avec le chunk `Dashboard` (353 Ko), chargé à la demande.

---

## 7. Ce qui reste ouvert

- **Pas de test de bout en bout live cette session** — aucun outil de navigateur n'était disponible (l'utilisateur a décliné l'extension Claude in Chrome). Vérifié : `npm run lint` propre, `npm run build` propre (aucun import cassé, aucune erreur), le serveur de dev démarre et répond `200`. Le rendu visuel réel et le flux de connexion complet contre la vraie stack (Keycloak + les 5 microservices) restent à vérifier interactivement — le serveur de dev (`npm run dev`, port 5173) a été laissé actif pour une inspection manuelle immédiate.
- **Pas de thème Keycloak** — la page de connexion réelle garde l'apparence par défaut de Keycloak.
- **Pas de tests automatisés frontend** — `package.json` n'avait aucun outillage de test avant cette phase (ni Vitest ni RTL) ; resterait une extension future raisonnable, pas un manque silencieux.
- **`frontend` n'est pas dans `docker-compose.yml`** — `discovery-server`/`api-gateway` tournent déjà hors Docker dans ce projet, et Vite fige les variables `VITE_*` au moment du *build*, ce qui ne s'articule pas proprement avec le modèle "variables d'environnement au runtime" de `docker-compose` sans plomberie supplémentaire (build args). Workflow de dev : `npm run dev`.

---

## 8. Comment démarrer et ouvrir l'application en local

Toutes les commandes ci-dessous s'exécutent depuis la racine du projet, `C:\Users\user\IdeaProjects\pipe-dev-liv`.

### 8.1 Infrastructure (Postgres, Keycloak, RabbitMQ)

```
docker compose up -d postgres-users postgres-tickets postgres-pipelines postgres-notifications postgres-audit postgres-keycloak keycloak rabbitmq
```

### 8.2 `discovery-server` et `api-gateway` (tournent hors Docker dans ce projet)

Dans deux terminaux séparés, attendre que `discovery-server` soit up avant de lancer `api-gateway` :

```
cd backend
./mvnw.cmd -pl discovery-server spring-boot:run
```

```
cd backend
./mvnw.cmd -pl api-gateway spring-boot:run
```

(ou via les run configs IntelliJ équivalentes.)

### 8.3 `user-service`

Tourne en local lui aussi (hors Docker), comme `discovery-server`/`api-gateway` :

```
cd backend
./mvnw.cmd -pl user-service spring-boot:run
```

### 8.4 Les 4 autres microservices (Docker)

```
docker compose up -d ticket-service pipeline-service notification-service audit-service
```

### 8.5 Frontend

```
cd frontend
npm run dev
```

→ disponible sur `http://localhost:5173`.

### 8.6 Vérification que tout répond

```
curl http://localhost:8761                    # discovery-server
curl http://localhost:8080/actuator/health     # api-gateway
curl http://localhost:8081/actuator/health     # user-service
curl http://localhost:8082/actuator/health     # ticket-service
curl http://localhost:8083/actuator/health     # pipeline-service
curl http://localhost:8084/actuator/health     # notification-service
curl http://localhost:8085/actuator/health     # audit-service
curl http://localhost:5173                     # frontend
```

### 8.7 Se connecter dans le navigateur

1. Ouvrir `http://localhost:5173`.
2. Sur la page d'accueil (Splash), cliquer sur le bouton doré **"Se connecter"**.
3. Redirection automatique vers la vraie page de login Keycloak (`localhost:9090/...`).
4. Se connecter avec l'utilisateur de test `ahmed` (créé en Phase 2) et son mot de passe.
   - Mot de passe oublié / jamais connu : aller sur `http://localhost:9090/admin`, se connecter avec les identifiants admin (`KC_ADMIN_USER`/`KC_ADMIN_PASSWORD` du fichier `.env` racine), puis `Users` → `ahmed` → onglet `Credentials` → `Reset password` (décocher "Temporary"). Vérifier aussi l'onglet `Role mapping` pour confirmer qu'un rôle (`ROLE_DEVELOPER`, `ROLE_ADMIN`, etc.) est bien attribué.
5. Après authentification, Keycloak renvoie sur `localhost:5173` avec le token — arrivée sur le **Dashboard**.
6. Navigation via la sidebar : Tickets, Pipelines, Notifications, Team (TECH_LEAD+), Audit Logs (ADMIN uniquement), Profile.
