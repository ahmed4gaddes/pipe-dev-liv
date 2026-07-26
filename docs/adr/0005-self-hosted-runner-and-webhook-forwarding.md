# ADR-0005 — Runner GitHub Actions self-hosted + `gh webhook forward`, plutôt que des runners hébergés

## Statut

Accepté (Phase 9)

## Contexte

Deux besoins de connectivité pour que `ci.yml`/`deploy.yml` fonctionnent réellement :
1. `ci.yml` doit analyser le code avec SonarQube, qui tourne en local sur `localhost:9000` —
   injoignable depuis un runner GitHub-hosted (exécuté dans le cloud de GitHub).
2. Le webhook natif `workflow_run` (voir [ADR-0004](0004-native-github-webhook-over-custom-endpoint.md))
   est envoyé par les serveurs de GitHub — injoignable vers un `localhost` local, quel que soit
   l'endroit où le job s'exécute.

Alternative envisagée : runners GitHub-hosted partout, garder l'analyse SonarQube comme commande
manuelle locale uniquement, et vérifier le webhook via le curl signé manuel déjà utilisé en Phase 5
plutôt que de le faire fonctionner en direct.

## Décision

Runner **self-hosted**, enregistré sur la machine de développement (service Windows), pour que
`ci.yml`/`deploy.yml` puissent atteindre `localhost:9000` et le reste de la stack locale
directement — ceci résout le point 1. Pour le point 2, **`gh webhook forward`** (fonctionnalité
officielle du GitHub CLI) crée un webhook éphémère le temps d'une session de démo et relaie les
événements vers `http://localhost:8080/api/webhooks/github`, sans configuration permanente dans les
réglages du dépôt.

## Conséquences

- **Positif** : le pipeline est réellement live de bout en bout (créer un ticket → l'approuver →
  voir le vrai run GitHub Actions → statut mis à jour en direct), cohérent avec le principe du
  projet de ne rien simuler quand une vraie implémentation est possible.
- **Négatif** : un runner self-hosted exécute le code du workflow avec les privilèges de la
  machine qui l'héberge — acceptable seulement parce que c'est un dépôt privé, mono-contributeur,
  jamais de PR externe non fiable (documenté explicitement en commentaire dans `ci.yml`). Nécessite
  aussi un enregistrement manuel unique (jeton interactif) et de se souvenir de lancer
  `gh webhook forward` avant toute démo live — sans ça, `deploy.yml` s'exécute réellement sur
  GitHub mais son statut ne remonte jamais côté application.
