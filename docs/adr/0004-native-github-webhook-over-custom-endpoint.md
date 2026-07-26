# ADR-0004 — Webhook natif GitHub `workflow_run`, pas un endpoint de callback personnalisé

## Statut

Accepté (Phase 5, exploité Phase 9)

## Contexte

Quand `pipeline-service` déclenche un déploiement via `workflow_dispatch`, il a besoin de savoir
quand ce run se termine et avec quel résultat pour mettre à jour le ticket associé. Le document de
planification initial du projet esquissait un `deploy.yml` qui, à la fin du job, ferait un `curl`
manuel vers un endpoint applicatif (`/api/webhooks/deployment`) pour notifier le backend.

## Décision

Ne pas construire cet endpoint. GitHub émet nativement un événement de webhook de dépôt
`workflow_run` (statuts `queued`/`in_progress`/`completed` + conclusion) dès qu'un run change
d'état — sans qu'aucune étape du workflow n'ait besoin de le déclencher explicitement.
`WebhookController` (`POST /api/webhooks/github`) reçoit cet événement natif, vérifie sa signature
HMAC (`X-Hub-Signature-256`, secret `GH_WEBHOOK_SECRET`), et `PipelineServiceImpl.handleWorkflowRunEvent`
fait toute la logique de correspondance (recherche de la `PipelineExecution` par `githubRunId`,
mapping conclusion→statut, récupération du détail des jobs via l'API REST).

## Conséquences

- **Positif** : `deploy.yml` n'a besoin d'aucune étape de notification — un échec de job se
  traduit naturellement en conclusion `failure`, remontée automatiquement. Moins de code à
  maintenir, pas de risque qu'un job réussisse mais que son étape de notification échoue
  silencieusement (un point de défaillance en moins).
- **Négatif** : nécessite que GitHub puisse effectivement atteindre `pipe-dev-liv` — impossible en
  local sans un tunnel ou une redirection (voir
  [ADR-0005](0005-self-hosted-runner-and-webhook-forwarding.md)), contrairement à un endpoint
  personnalisé qu'on aurait pu appeler depuis le runner lui-même (qui, lui, est local).
