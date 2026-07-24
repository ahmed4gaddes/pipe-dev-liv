package com.pipedevliv.pipeline.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.exception.GlobalExceptionHandler;
import com.pipedevliv.common.security.SecurityConfig;
import com.pipedevliv.pipeline.dto.PipelineExecutionDTO;
import com.pipedevliv.pipeline.dto.PipelineStageDTO;
import com.pipedevliv.pipeline.dto.PipelineTriggerDTO;
import com.pipedevliv.pipeline.service.PipelineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Même piège/solution que TicketControllerTest : @WebMvcTest ne charge pas les
// @AutoConfiguration tierces (common-lib.SecurityConfig) par défaut, donc @PreAuthorize
// serait silencieusement inerte sans cet @Import explicite.
@WebMvcTest(PipelineController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class PipelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PipelineService pipelineService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void trigger_asTechLead_succeeds() throws Exception {
        authenticateAs("tl-1", "ROLE_TECH_LEAD");
        when(pipelineService.triggerPipeline(any(), eq("tl-1"))).thenReturn(execution(1L, "QUEUED"));

        mockMvc.perform(post("/api/pipelines/trigger")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                PipelineTriggerDTO.builder().ticketId(5L).targetEnvironment("DEV").gitBranch("main").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void trigger_asDeveloper_forbidden() throws Exception {
        authenticateAs("dev-1", "ROLE_DEVELOPER");

        mockMvc.perform(post("/api/pipelines/trigger")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                PipelineTriggerDTO.builder().ticketId(5L).targetEnvironment("DEV").gitBranch("main").build())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listExecutions_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(pipelineService.listExecutions(any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(execution(1L, "SUCCESS")), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/api/pipelines/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    void getExecution_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(pipelineService.getExecution(1L)).thenReturn(execution(1L, "SUCCESS"));

        mockMvc.perform(get("/api/pipelines/executions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getStages_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(pipelineService.getStages(1L)).thenReturn(List.of(
                PipelineStageDTO.builder().name("build").status("SUCCESS").stageOrder(1).build()));

        mockMvc.perform(get("/api/pipelines/executions/1/stages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("build"));
    }

    @Test
    void getLogsUrl_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(pipelineService.getLogsUrl(1L)).thenReturn("https://github.com/x/y/actions/runs/999");

        mockMvc.perform(get("/api/pipelines/executions/1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://github.com/x/y/actions/runs/999"));
    }

    @Test
    void listByTicket_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(pipelineService.listByTicket(5L)).thenReturn(List.of(execution(1L, "SUCCESS")));

        mockMvc.perform(get("/api/pipelines/executions/by-ticket/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ticketId").value(5));
    }

    private PipelineExecutionDTO execution(Long id, String status) {
        return PipelineExecutionDTO.builder()
                .id(id).ticketId(5L).environment("DEV").status(status).build();
    }

    private void authenticateAs(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority(role))));
    }
}
