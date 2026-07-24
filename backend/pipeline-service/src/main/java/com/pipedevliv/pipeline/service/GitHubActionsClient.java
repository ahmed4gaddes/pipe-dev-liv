package com.pipedevliv.pipeline.service;

import com.pipedevliv.pipeline.dto.GitHubJobDTO;
import com.pipedevliv.pipeline.dto.GitHubJobsListResponse;
import com.pipedevliv.pipeline.dto.GitHubRunDTO;
import com.pipedevliv.pipeline.dto.GitHubRunsListResponse;
import com.pipedevliv.pipeline.exception.GitHubApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client synchrone (RestClient, cohérent avec le reste du projet qui n'a aucune pile
 * réactive) pour l'API REST GitHub Actions réelle. Le fichier de workflow ciblé
 * ({@code github.workflow-file}, "deploy.yml") n'existe pas encore dans ce repo — c'est la
 * Phase 9 qui le livrera — donc un appel réel renverra un 404 tant que Phase 9 n'est pas
 * faite ; c'est un comportement attendu, remonté proprement en GitHubApiException plutôt
 * qu'une exception réseau brute.
 */
@Component
public class GitHubActionsClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubActionsClient.class);

    private final RestClient restClient;
    private final String owner;
    private final String repo;
    private final String workflowFile;

    public GitHubActionsClient(
            RestClient githubRestClient,
            @Value("${github.owner}") String owner,
            @Value("${github.repo}") String repo,
            @Value("${github.workflow-file}") String workflowFile) {
        this.restClient = githubRestClient;
        this.owner = owner;
        this.repo = repo;
        this.workflowFile = workflowFile;
    }

    public void triggerWorkflow(String ref, Map<String, String> inputs) {
        try {
            restClient.post()
                    .uri("/repos/{owner}/{repo}/actions/workflows/{workflowFile}/dispatches", owner, repo, workflowFile)
                    .body(Map.of("ref", ref, "inputs", inputs))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new GitHubApiException("Échec du déclenchement du workflow GitHub Actions : " + ex.getMessage());
        }
    }

    /**
     * workflow_dispatch ne renvoie pas d'ID de run (204 No Content) : on va chercher, juste
     * après avoir déclenché, le run le plus récent sur cette branche. Best-effort — si GitHub
     * n'a pas encore indexé le run (timing) ou si l'appel échoue, on renvoie Optional.empty()
     * plutôt que de faire échouer tout le déclenchement.
     */
    public Optional<Long> findLatestRunId(String ref) {
        try {
            GitHubRunsListResponse response = restClient.get()
                    .uri("/repos/{owner}/{repo}/actions/workflows/{workflowFile}/runs?event=workflow_dispatch&branch={branch}&per_page=1",
                            owner, repo, workflowFile, ref)
                    .retrieve()
                    .body(GitHubRunsListResponse.class);

            if (response == null || response.getWorkflowRuns() == null || response.getWorkflowRuns().isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(response.getWorkflowRuns().get(0).getId());
        } catch (RestClientException ex) {
            log.warn("Impossible de retrouver le run GitHub Actions déclenché (branche={}) : {}", ref, ex.getMessage());
            return Optional.empty();
        }
    }

    public GitHubRunDTO getRun(Long runId) {
        try {
            return restClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{runId}", owner, repo, runId)
                    .retrieve()
                    .body(GitHubRunDTO.class);
        } catch (RestClientException ex) {
            throw new GitHubApiException("Échec de la récupération du run GitHub Actions " + runId + " : " + ex.getMessage());
        }
    }

    public List<GitHubJobDTO> getRunJobs(Long runId) {
        try {
            GitHubJobsListResponse response = restClient.get()
                    .uri("/repos/{owner}/{repo}/actions/runs/{runId}/jobs", owner, repo, runId)
                    .retrieve()
                    .body(GitHubJobsListResponse.class);

            return response == null || response.getJobs() == null ? List.of() : response.getJobs();
        } catch (RestClientException ex) {
            log.warn("Impossible de récupérer les jobs du run GitHub Actions {} : {}", runId, ex.getMessage());
            return List.of();
        }
    }
}
