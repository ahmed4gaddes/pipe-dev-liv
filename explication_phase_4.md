# Explication Détaillée : Phase 4 (Ticket Service — Cœur Métier)

Cette phase ajoute le **Ticket Service**, le service métier central de la plateforme : c'est lui qui porte le cycle de vie complet d'un ticket de déploiement, du brouillon jusqu'à la mise en production, avec les règles d'approbation et les droits par rôle qui vont avec.

C'est aussi la première phase où deux briques d'infrastructure nouvelles apparaissent : la **sécurité par rôle** (`@PreAuthorize`) et les **appels service-à-service** (Feign), toutes deux construites pour s'intégrer proprement dans le modèle de sécurité déjà en place (Gateway = seul point de confiance).

---

## 1. Le cycle de vie d'un ticket (machine d'état)

Un ticket a **14 statuts** possibles :

```
DRAFT → SUBMITTED → APPROVED → DEPLOYING_DEV → DEPLOYED_DEV
      → DEPLOYING_TEST → DEPLOYED_TEST → PENDING_PROD_APPROVAL
      → DEPLOYING_PROD → DEPLOYED_PROD → CLOSED

+ REJECTED, CANCELLED, FAILED (états de sortie/erreur)
```

**`TicketStateMachine`** (`service/TicketStateMachine.java`) est une classe volontairement "bête" : elle sait juste répondre à la question *"le passage de tel statut à tel autre est-il structurellement autorisé ?"*, via une simple table `Map<TicketStatus, Set<TicketStatus>>`. Elle ne connaît ni rôle ni utilisateur — c'est fait exprès, pour séparer deux questions différentes :

- **"Cette transition a-t-elle un sens ?"** → `TicketStateMachine`
- **"Cet utilisateur a-t-il le droit de la déclencher ?"** → `@PreAuthorize` + règles métier dans `TicketServiceImpl`

Points particuliers du workflow :
- **`/approve`** est un seul endpoint qui se comporte différemment selon le statut courant : depuis `SUBMITTED` il approuve simplement (pas d'appel réseau) ; depuis `PENDING_PROD_APPROVAL` il approuve **et** déclenche le déploiement PROD (il n'existe pas de statut intermédiaire "PROD approuvé mais pas encore déployé").
- **`/deploy/{env}`** gère DEV et TEST ; PROD est volontairement refusé sur cette route (message clair renvoyant vers `/approve`), puisque le déploiement PROD passe uniquement par l'approbation.
- Chaque transition écrit une ligne dans `TicketHistory` (y compris la création du ticket, avec `oldStatus = null`), pour avoir une traçabilité complète.

---

## 2. Sécurité par rôle : la nouveauté `RoleHierarchy`

Jusqu'à cette phase, aucun service n'utilisait `@PreAuthorize`. Le Ticket Service en a besoin partout (`POST /api/tickets` réservé aux `DEVELOPER`, `/approve` aux `TECH_LEAD`, `/stats` aux `ADMIN`, etc.), avec une contrainte : un rôle "supérieur" doit hériter des droits des rôles "inférieurs" (un `ADMIN` doit pouvoir faire tout ce qu'un `DEVELOPER` peut faire).

Ça n'existait pas encore, donc on l'a ajouté **une fois pour toutes dans `common-lib`** (`SecurityConfig.java`), pour que tous les services actuels et futurs en profitent automatiquement :

```java
@Bean
static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("""
        ROLE_ADMIN > ROLE_RELEASE_MANAGER
        ROLE_RELEASE_MANAGER > ROLE_TECH_LEAD
        ROLE_TECH_LEAD > ROLE_DEVELOPER
        ROLE_DEVELOPER > ROLE_VIEWER
        """);
}
```

**Détail important** : ce bean (et celui qui l'utilise, `MethodSecurityExpressionHandler`) doit être déclaré `static`. Spring Security a besoin de le publier *avant* que ses propres classes de configuration de sécurité méthode ne s'initialisent — un bean non-`static` échoue à se câbler silencieusement (aucune erreur, mais la hiérarchie est juste ignorée). C'est un piège documenté de Spring Security.

On a aussi ajouté un gestionnaire pour `AccessDeniedException` dans `GlobalExceptionHandler`, pour que les refus d'accès (403) renvoient une réponse `ApiResponse` cohérente avec le reste de l'API, au lieu de la page d'erreur par défaut de Spring.

### Règle d'ownership

Certaines actions (`PUT /api/tickets/{id}`, `PATCH .../status`) sont ouvertes à un `TECH_LEAD` **ou** au `DEVELOPER` propriétaire du ticket. Ça se traduit par une expression `@PreAuthorize` qui appelle une méthode du service :

```java
@PreAuthorize("hasRole('TECH_LEAD') or (hasRole('DEVELOPER') and @ticketService.isOwner(#id, authentication.name))")
```

Un propriétaire non-`TECH_LEAD` ne peut demander que `SUBMITTED` ou `CANCELLED` comme nouveau statut (vérifié côté service, pas dans l'expression SpEL — trop complexe à exprimer proprement en SpEL).

---

## 3. Appels service-à-service (Feign) et le problème qu'ils posent

Le Ticket Service appelle deux autres services en synchrone :
- **User Service**, pour résoudre les infos d'un utilisateur (`UserServiceClient`, déjà réel).
- **Pipeline Service**, pour déclencher un déploiement (`PipelineServiceClient`) — **ce service n'existe pas encore** (c'est la Phase 5). Un `PipelineServiceFallback` répond avec un message clair ("Pipeline Service indisponible") en attendant, plutôt que de laisser une erreur réseau brute remonter au client.

**Le piège** : ces appels Feign passent directement de service à service via Eureka (`lb://USER-SERVICE`), **sans repasser par la Gateway**. Or c'est justement la Gateway qui, normalement, injecte les headers de confiance (`X-User-Id`, `X-Internal-Secret`) que chaque service exige. Sans rien faire, ces appels se seraient fait rejeter par le `HeaderAuthenticationFilter` du service cible.

La solution : `config/FeignConfig.java` réinjecte manuellement ces headers sur chaque appel sortant, à partir de l'utilisateur actuellement authentifié dans la requête en cours :

```java
public class FeignConfig {   // volontairement PAS @Configuration, voir plus bas
    @Bean
    public RequestInterceptor authForwardingInterceptor() {
        return requestTemplate -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                requestTemplate.header("X-User-Id", auth.getName());
                requestTemplate.header("X-User-Roles", /* rôles joints en CSV */);
            }
            requestTemplate.header("X-Internal-Secret", gatewaySecret);
        };
    }
}
```

`FeignConfig` n'est **pas** annotée `@Configuration` : comme elle vit dans le package scanné du service, l'annoter aurait causé son enregistrement une deuxième fois dans le contexte principal (piège documenté de Spring Cloud OpenFeign quand une config passée en `defaultConfiguration` est aussi component-scannée).

---

## 4. Événements RabbitMQ

Même schéma que le User Service (Phase 3) : `TicketEventPublisher` publie sur l'exchange partagé `pipe-dev-liv.events`.
- `TICKET_CREATED` à la création.
- `TICKET_STATUS_CHANGED` à chaque transition (y compris annulation/rejet).
- `TICKET_APPROVED` en plus, spécifiquement sur les deux transitions d'approbation — pour qu'un futur `notification-service` puisse réagir à "ticket approuvé" sans avoir à interpréter le contenu générique d'un `TICKET_STATUS_CHANGED`.

---

## 5. API REST

| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `POST` | `/api/tickets` | DEVELOPER+ | Créer (démarre en DRAFT) |
| `GET` | `/api/tickets` | VIEWER+ | Liste paginée + filtres (statut, priorité, propriétaire) |
| `GET` | `/api/tickets/{id}` | VIEWER+ | Détail |
| `PUT` | `/api/tickets/{id}` | TECH_LEAD+ ou propriétaire | Modifier (uniquement si DRAFT) |
| `PATCH` | `/api/tickets/{id}/status` | TECH_LEAD+ ou propriétaire (SUBMITTED/CANCELLED seulement) | Changer de statut |
| `POST` | `/api/tickets/{id}/approve` | TECH_LEAD+ | Approuver (contextuel) |
| `POST` | `/api/tickets/{id}/reject` | TECH_LEAD+ | Rejeter |
| `POST` | `/api/tickets/{id}/deploy/{env}` | TECH_LEAD+ | Déclencher un déploiement DEV/TEST |
| `GET` | `/api/tickets/{id}/history` | VIEWER+ | Historique des transitions |
| `POST` | `/api/tickets/{id}/comments` | DEVELOPER+ | Commenter |
| `GET` | `/api/tickets/stats` | ADMIN | Statistiques globales |

---

## 6. Tests — et un vrai piège découvert en cours de route

Trois couches de tests, comme pour le User Service : `TicketStateMachineTest` (table de transitions), `TicketServiceImplTest` (Mockito), `TicketRepositoryTest`/`TicketHistoryRepositoryTest`/`TicketCommentRepositoryTest` (`@DataJpaTest` + H2), `TicketControllerTest` (`@WebMvcTest`).

**Ce qu'on a découvert en écrivant les tests de sécurité** : `@WebMvcTest` ne charge **pas** les `@AutoConfiguration` tierces comme celle de `common-lib` — Spring Boot y substitue sa propre configuration de sécurité par défaut, permissive. Résultat : sans intervention, `@PreAuthorize` était silencieusement inactif dans les tests (aucune erreur, juste aucun contrôle d'accès). La correction :

```java
@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
```

`addFilters = false` reste nécessaire (sinon le filtre de chargement du contexte de sécurité écraserait l'authentification qu'on pousse manuellement dans les tests), mais `@Import` force le chargement de la vraie configuration de méthode-sécurité (`@EnableMethodSecurity`, `RoleHierarchy`) — indépendante des filtres servlet. Un test dédié (`roleHierarchy_adminOnlyAuthority_canCallDeveloperGuardedEndpoint`) prouve explicitement que la hiérarchie fonctionne : un utilisateur n'ayant *que* `ROLE_ADMIN` passe quand même un `@PreAuthorize("hasRole('DEVELOPER')")`.

À refaire pareil si un futur service (`pipeline-service`, etc.) teste des endpoints protégés par rôle.

---

## 7. Docker Compose

`ticket-service` a été ajouté à `docker-compose.yml`, sur le même modèle que `user-service` : port `8082`, connecté à `postgres-tickets` (déjà présent) et `rabbitmq`, avec `EUREKA_HOST` pointant vers `host.docker.internal` tant que `discovery-server`/`api-gateway` ne sont pas eux-mêmes conteneurisés.

---

## 8. Ce qui reste ouvert pour la suite

- **Phase 5 (Pipeline Service)** est la suite logique directe : `PipelineServiceClient` l'attend déjà côté Ticket Service, avec un contrat (`PipelineTriggerDTO`/`PipelineExecutionDTO`) qui devra être confirmé/ajusté une fois Pipeline Service réellement construit.
- Les modules `notification-service` et `audit-service` restent des stubs vides — ce sont eux qui, plus tard, s'abonneront à `TICKET_STATUS_CHANGED`/`TICKET_APPROVED` sur RabbitMQ.
