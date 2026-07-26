# ADR-0003 — Les vérifications de rôle côté frontend sont de l'UX, jamais la frontière de sécurité

## Statut

Accepté (Phase 4, reconduit Phase 8)

## Contexte

Le backend a une hiérarchie de rôles centralisée (`common-lib.RoleHierarchy` :
`ADMIN > RELEASE_MANAGER > TECH_LEAD > DEVELOPER > VIEWER`) appliquée via `@PreAuthorize` sur
chaque endpoint. Le frontend React a besoin de savoir quels boutons/pages afficher à un
utilisateur donné (ex. cacher « Approuver » à un simple DEVELOPER).

## Décision

`frontend/src/auth/AuthContext.jsx` réimplémente un `hasRole()` client, avec le même ordre que
`RoleHierarchy`, décodé depuis `keycloak.tokenParsed.realm_access.roles` (le JWT lui-même). Ce
`hasRole()` ne sert **qu'à** décider quoi afficher (`ProtectedRoute`, `TicketActionBar`, items de
navigation). Chaque appel API réel repasse par la Gateway, qui valide le JWT et transmet les
rôles ; chaque endpoint applique son propre `@PreAuthorize` indépendamment de ce que le frontend a
choisi de montrer.

## Conséquences

- **Positif** : le frontend peut avoir une UX riche et immédiate (masquer un bouton) sans jamais
  être la source de vérité — même une réponse falsifiée par un utilisateur inspectant le JS ne
  contourne rien côté serveur.
- **Négatif** : deux implémentations de la même hiérarchie de rôles existent (Java, JS) — une
  dérive entre les deux ferait afficher un bouton pour une action que le backend refuserait (UX
  dégradée, pas une faille de sécurité). `frontend/src/constants/ticket.js` (`TICKET_ACTIONS`) est
  logé au même endroit et soumis au même risque, testé en Phase 10
  (`ticket.test.js`/`TicketActionBar.test.jsx`) précisément pour détecter ce type de dérive.
