package com.pipedevliv.pipeline.service;

import com.pipedevliv.pipeline.dto.GitHubJobDTO;
import com.pipedevliv.pipeline.dto.GitHubRunDTO;
import com.pipedevliv.pipeline.exception.GitHubApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubActionsClientTest {

    private MockRestServiceServer server;
    private GitHubActionsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubActionsClient(builder.build(), "test-owner", "test-repo", "deploy.yml", "test-runner");
    }

    @Test
    void triggerWorkflow_sendsCorrectRequest() {
        server.expect(requestTo("https://api.github.com/repos/test-owner/test-repo/actions/workflows/deploy.yml/dispatches"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"ref\":\"main\",\"inputs\":{\"environment\":\"dev\",\"ticket_id\":\"5\"}}"))
                .andRespond(withNoContent());

        client.triggerWorkflow("main", Map.of("environment", "dev", "ticket_id", "5"));

        server.verify();
    }

    // Le label conditionne la machine qui exécutera le déploiement : s'il n'est pas transmis,
    // GitHub retombe sur le premier runner libre du dépôt, potentiellement celui d'un autre
    // développeur, qui redéploierait alors sa propre stack.
    @Test
    void triggerWorkflow_addsConfiguredRunnerLabelToInputs() {
        server.expect(requestTo("https://api.github.com/repos/test-owner/test-repo/actions/workflows/deploy.yml/dispatches"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"inputs\":{\"ticket_id\":\"5\",\"runner_label\":\"test-runner\"}}"))
                .andRespond(withNoContent());

        client.triggerWorkflow("main", Map.of("ticket_id", "5"));

        server.verify();
    }

    @Test
    void triggerWorkflow_serverError_throwsGitHubApiException() {
        server.expect(requestTo("https://api.github.com/repos/test-owner/test-repo/actions/workflows/deploy.yml/dispatches"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.triggerWorkflow("main", Map.of()))
                .isInstanceOf(GitHubApiException.class);
    }

    @Test
    void findLatestRunId_returnsFirstRun() {
        server.expect(requestTo("https://api.github.com/repos/test-owner/test-repo/actions/workflows/deploy.yml/runs?event=workflow_dispatch&branch=main&per_page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"total_count": 1, "workflow_runs": [{"id": 123456, "status": "queued"}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<Long> runId = client.findLatestRunId("main");

        assertThat(runId).contains(123456L);
    }

    @Test
    void findLatestRunId_noRuns_returnsEmpty() {
        server.expect(requestTo("https://api.github.com/repos/test-owner/test-repo/actions/workflows/deploy.yml/runs?event=workflow_dispatch&branch=main&per_page=1"))
                .andRespond(withSuccess("""
                        {"total_count": 0, "workflow_runs": []}
                        """, MediaType.APPLICATION_JSON));

        Optional<Long> runId = client.findLatestRunId("main");

        assertThat(runId).isEmpty();
    }

    @Test
    void findLatestRunId_apiError_returnsEmptyRatherThanThrowing() {
        server.expect(requestTo("https://api.github.com/repos/test-owner/test-repo/actions/workflows/deploy.yml/runs?event=workflow_dispatch&branch=main&per_page=1"))
                .andRespond(withServerError());

        assertThat(client.findLatestRunId("main")).isEmpty();
    }

    @Test
    void getRun_parsesResponse() {
        server.expect(requestTo("https://api.github.com/repos/test-owner/test-repo/actions/runs/123456"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id": 123456, "status": "completed", "conclusion": "success", "html_url": "https://github.com/x/y/actions/runs/123456"}
                        """, MediaType.APPLICATION_JSON));

        GitHubRunDTO run = client.getRun(123456L);

        assertThat(run.getConclusion()).isEqualTo("success");
        assertThat(run.getHtmlUrl()).isEqualTo("https://github.com/x/y/actions/runs/123456");
    }

    @Test
    void getRunJobs_parsesJobsList() {
        server.expect(requestTo("https://api.github.com/repos/test-owner/test-repo/actions/runs/123456/jobs"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"total_count": 1, "jobs": [{"id": 1, "name": "build", "status": "completed", "conclusion": "success",
                        "started_at": "2026-07-24T10:00:00Z", "completed_at": "2026-07-24T10:02:00Z"}]}
                        """, MediaType.APPLICATION_JSON));

        List<GitHubJobDTO> jobs = client.getRunJobs(123456L);

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getName()).isEqualTo("build");
    }
}
