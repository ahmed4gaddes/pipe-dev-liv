# Explication Détaillée : Phase 5 (Pipeline Service — Intégration GitHub Actions)

Cette phase construit le **Pipeline Service** : le maillon qui transforme une approbation de ticket en un vrai déploiement GitHub Actions, puis rapporte le résultat une fois le workflow terminé. C'est la première phase qui parle à un système **externe** (l'API GitHub), et la première qui reçoit un appel entrant venant de **l'extérieur du cluster** (le webhook GitHub) — les deux cassent des hypothèses que la sécurité des phases précédentes tenait pour acquises.

---

## 1. Le problème du "run ID manquant"

Quand Ticket Service approuve/déploie un ticket, il appelle (via Feign) `POST /api/pipelines/trigger` sur Pipeline Service, qui à son tour appelle l'API GitHub (`workflow_dispatch`) pour lancer le workflow. Piège : **cet appel GitHub renvoie juste un `204 No Content`, sans identifiant de run**. GitHub ne fournit aucun moyen synchrone de savoir "quel run vient d'être créé".

La solution (imparfaite mais standard) : juste après avoir déclenché, Pipeline Service refait un appel pour lister les runs récents du workflow sur cette branche (`GET .../runs?event=workflow_dispatch&branch=...&per_page=1`) et prend le plus récent. C'est du best-effort — si GitHub n'a pas encore indexé le run (latence) ou si l'appel échoue, `PipelineExecution.githubRunId` reste `null` : ce n'est pas bloquant, juste loggé (`GitHubActionsClient.findLatestRunId`).

---

## 2. Le webhook GitHub : un appelant qui ne peut prouver son identité comme les autres

Toute la sécurité posée en Phase 2 repose sur : la Gateway valide un JWT Keycloak, injecte des headers de confiance (`X-User-Id`, `X-Internal-Secret`), et `HeaderAuthenticationFilter` (`common-lib`) exige ces headers partout (`.anyRequest().authenticated()`). **GitHub ne peut présenter ni l'un ni l'autre** — ce n'est pas un utilisateur Keycloak.

GitHub prouve l'authenticité de ses webhooks autrement : une signature HMAC-SHA256 du corps brut de la requête, calculée avec un secret partagé (`GH_WEBHOOK_SECRET`), envoyée dans l'en-tête `X-Hub-Signature-256: sha256=<hex>`. Deux carve-outs ont donc été ajoutés, à deux niveaux différents :

- **`api-gateway`** (`GatewaySecurityConfig`) : `/api/webhooks/**` passe en `permitAll()`, à côté de `/actuator/health/**`. `JwtAuthFilter` continue quand même à retirer systématiquement tout header `X-User-*`/`X-Internal-Secret` que le client aurait tenté d'injecter lui-même, donc ce carve-out n'ouvre pas de trou d'usurpation d'identité.
- **`pipeline-service`** (son propre `config/SecurityConfig.java`, en plus de celui de `common-lib`) : une **deuxième** `SecurityFilterChain`, scopée uniquement à `/api/webhooks/github` via `.securityMatcher(...)`, avec `@Order(1)` pour qu'elle soit évaluée avant la chaîne générale (non ordonnée) de `common-lib`. Spring Security supporte nativement plusieurs `SecurityFilterChain` — chacune scope ses propres routes, la première qui matche gagne.

L'authenticité réelle est vérifiée **manuellement dans `WebhookController`**, via `WebhookSignatureVerifier` (même technique de comparaison en temps constant, `MessageDigest.isEqual`, que `HeaderAuthenticationFilter.isValidGatewaySecret`) — une signature absente ou invalide renvoie `401`, indépendamment de toute la mécanique Spring Security habituelle.

---

## 3. Le chemin retour : Pipeline Service → Ticket Service, sans utilisateur authentifié

Une fois le run terminé, GitHub envoie son webhook `workflow_run` (`action: "completed"`). Pipeline Service doit alors répercuter le résultat sur le ticket (`DEPLOYING_DEV` → `DEPLOYED_DEV` ou `FAILED`, etc.) via un appel Feign vers Ticket Service (`PATCH /api/tickets/{id}/pipeline-status`, nouveau endpoint ajouté en Phase 5 sur Ticket Service).

Problème : le `FeignConfig` de Ticket Service (Phase 4) forwarde l'identité de l'utilisateur *actuellement authentifié* (`SecurityContextHolder`) sur chaque appel sortant. Ici, il n'y a **aucun utilisateur authentifié** — la requête vient d'un webhook GitHub asynchrone, pas d'une action utilisateur en cours. Le `FeignConfig` de Pipeline Service affirme donc une **identité système fixe** à la place :

```java
requestTemplate.header("X-User-Id", "pipeline-service");
requestTemplate.header("X-User-Roles", "ROLE_SYSTEM");
requestTemplate.header("X-Internal-Secret", gatewaySecret);
```

Côté Ticket Service, le nouvel endpoint est gardé par `@PreAuthorize("hasRole('SYSTEM')")`. `ROLE_SYSTEM` n'a **pas** été ajouté à la `RoleHierarchy` partagée (`ADMIN > RELEASE_MANAGER > TECH_LEAD > DEVELOPER > VIEWER`) — c'est une autorité totalement à part, qu'aucun JWT Keycloak ne contiendra jamais. Résultat : même un `ADMIN` humain authentifié via la Gateway ne peut pas appeler cet endpoint interne, seul un appel Feign venant réellement de Pipeline Service le peut. Testé explicitement (`TicketControllerTest.updatePipelineStatus_asTechLeadWithoutSystemRole_forbidden`).

---

## 4. Client GitHub : `RestClient`, pas `WebClient`

Le reste du projet est entièrement synchrone (JPA bloquant, Feign bloquant, MVC classique) — aucune pile réactive n'existe nulle part. Plutôt que d'introduire `WebClient`/Reactor pour ce seul appel externe (ce qui aurait nécessité des `.block()` un peu partout, un anti-pattern classique), `GitHubActionsClient` utilise `RestClient` (Spring Framework 6.1+, synchrone, déjà inclus dans `spring-boot-starter-web`) :

- `triggerWorkflow(ref, inputs)` → `POST .../dispatches`
- `findLatestRunId(ref)` → `GET .../runs?...` (best-effort, voir §1)
- `getRun(runId)` → `GET .../runs/{id}` (utilisé pour `/logs`, qui renvoie le `html_url` GitHub plutôt que de télécharger/décompresser l'archive zip de logs bruts — simplification volontaire, peu de valeur pratique en local pour la complexité que ça ajouterait)
- `getRunJobs(runId)` → `GET .../runs/{id}/jobs`, mappé vers des lignes `PipelineStage` (une par job GitHub) à chaque fin de run

---

## 5. Événements RabbitMQ

Même schéma que les phases précédentes : `PipelineEventPublisher` publie sur l'exchange partagé `pipe-dev-liv.events`, en réutilisant les routing keys déjà réservées en Phase 3/4 (`pipeline.started`, `pipeline.completed`, `pipeline.failed` — présentes dans `RabbitMQConstants` depuis le début, jamais utilisées avant cette phase).

---

## 6. API REST

| Méthode | Endpoint | Rôle |
|---|---|---|
| `POST` | `/api/pipelines/trigger` | TECH_LEAD+ (défense en profondeur — seul Ticket Service l'appelle aujourd'hui) |
| `GET` | `/api/pipelines/executions` | VIEWER+ |
| `GET` | `/api/pipelines/executions/{id}` | VIEWER+ |
| `GET` | `/api/pipelines/executions/{id}/stages` | VIEWER+ |
| `GET` | `/api/pipelines/executions/{id}/logs` | VIEWER+ (renvoie `{ "url": "<lien GitHub>" }`) |
| `GET` | `/api/pipelines/executions/by-ticket/{ticketId}` | VIEWER+ |
| `POST` | `/api/webhooks/github` | *(aucun — signature HMAC à la place, voir §2)* |
| `PATCH` | `/api/tickets/{id}/pipeline-status` *(Ticket Service)* | `ROLE_SYSTEM` uniquement (voir §3) |

---

## 7. Tests — un test volontairement à l'envers

Trois couches habituelles (`GitHubActionsClientTest` avec `MockRestServiceServer`, `PipelineServiceImplTest` en Mockito, `PipelineExecutionRepositoryTest`/`PipelineStageRepositoryTest` en `@DataJpaTest`+H2, `PipelineControllerTest` en `@WebMvcTest` avec le pattern `@Import({SecurityConfig.class, GlobalExceptionHandler.class})` déjà établi en Phase 4).

`WebhookControllerTest` fait l'inverse de toutes les autres : `@AutoConfigureMockMvc(addFilters = true)` au lieu de `false`. C'est volontaire — le but de ce test est justement de prouver que la **vraie** chaîne de filtres (les deux `SecurityFilterChain`, celle de `common-lib` et celle locale à `pipeline-service`) laisse passer une requête sans JWT ni `X-User-Id`, et que c'est bien la vérification manuelle de signature dans le contrôleur (pas Spring Security) qui rejette une requête non authentique.

---

## 8. Docker Compose

`pipeline-service` ajouté sur le même modèle que `user-service`/`ticket-service` : port `8083`, connecté à `postgres-pipelines` (déjà présent depuis le début, jamais utilisé avant cette phase) et `rabbitmq`, plus les quatre variables `GH_*` déjà réservées dans `.env`/`.env.example` depuis le début du projet — confirmant que cette intégration GitHub réelle était prévue dès la conception, pas ajoutée après coup.

---

## 9. Limite connue : pas encore de test de bout en bout avec un vrai GitHub Actions

Deux choses bloquent un test réellement live pour l'instant :

1. **`.github/workflows/deploy.yml` n'existe pas encore** — c'est un livrable explicite de la Phase 9. Un vrai déclenchement renverra un `404` GitHub tant que ce fichier n'est pas poussé sur le repo.
2. **Un runner GitHub hébergé ne peut pas atteindre `localhost`** — sans tunnel (ngrok ou équivalent), GitHub ne pourra jamais appeler le webhook d'une instance qui tourne uniquement sur la machine du développeur.

En attendant, la vérification s'est faite par : la suite de tests automatisés (38 tests sur `pipeline-service`, mocks GitHub via `MockRestServiceServer`), la validation `docker compose config`, et un appel webhook manuel signé à la main (HMAC calculé avec `openssl dgst -sha256 -hmac`) pour prouver que le chemin de bout en bout côté code (signature → parsing → mise à jour de `PipelineExecution` → notification de Ticket Service) fonctionne réellement — seule la partie "vrai GitHub Actions qui tourne" reste à brancher en Phase 9.

---

## 10. Ce qui reste ouvert pour la suite

- **Phase 9** doit livrer `.github/workflows/deploy.yml` pour permettre un premier déclenchement réel.
- Une fois Phase 9 faite, un test de bout en bout complet (créer ticket → soumettre → approuver → déployer DEV → webhook réel → ticket passe à `DEPLOYED_DEV`) devient possible, à travers la stack complète.
- `notification-service`/`audit-service` restent des stubs — ce sont eux qui consommeront `pipeline.started`/`pipeline.completed`/`pipeline.failed` plus tard.
