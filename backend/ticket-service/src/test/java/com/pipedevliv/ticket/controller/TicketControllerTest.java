package com.pipedevliv.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.exception.GlobalExceptionHandler;
import com.pipedevliv.common.security.SecurityConfig;
import com.pipedevliv.ticket.dto.PipelineStatusUpdateDTO;
import com.pipedevliv.ticket.dto.TicketCommentCreateDTO;
import com.pipedevliv.ticket.dto.TicketCommentDTO;
import com.pipedevliv.ticket.dto.TicketCreateDTO;
import com.pipedevliv.ticket.dto.TicketHistoryDTO;
import com.pipedevliv.ticket.dto.TicketResponseDTO;
import com.pipedevliv.ticket.dto.TicketStatsDTO;
import com.pipedevliv.ticket.dto.TicketStatusChangeDTO;
import com.pipedevliv.ticket.dto.TicketUpdateDTO;
import com.pipedevliv.ticket.entity.TicketPriority;
import com.pipedevliv.ticket.entity.TicketStatus;
import com.pipedevliv.ticket.service.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest ne charge que les auto-configurations Spring Boot officiellement listées pour
// ce type de slice -- common-lib.SecurityConfig (@AutoConfiguration tierce) n'en fait pas
// partie, donc @EnableMethodSecurity/@PreAuthorize seraient silencieusement inertes sans cet
// import explicite (Boot substituerait sa propre config de sécurité par défaut, permissive).
@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Nom explicite : @PreAuthorize référence le bean via "@ticketService" (voir
    // TicketServiceImpl, nommé pareil en prod) ; sans le forcer ici, le nom déduit par
    // @MockBean pour un champ interface peut ne pas correspondre exactement.
    @MockBean(name = "ticketService")
    private TicketService ticketService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void defaultOwnerStub() {
        // Valeur par défaut sûre ; les tests qui veulent tester le cas "non propriétaire"
        // écrasent explicitement ce stub.
        when(ticketService.isOwner(anyLong(), anyString())).thenReturn(true);
    }

    @Test
    void createTicket_asDeveloper_succeeds() throws Exception {
        authenticateAs("dev-1", "ROLE_DEVELOPER");
        TicketResponseDTO response = ticket(1L, TicketStatus.DRAFT);
        when(ticketService.createTicket(any(), eq("dev-1"))).thenReturn(response);

        mockMvc.perform(post("/api/tickets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                TicketCreateDTO.builder().title("Add feature").priority(TicketPriority.MEDIUM).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createTicket_asViewerOnly_forbidden() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");

        mockMvc.perform(post("/api/tickets")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                TicketCreateDTO.builder().title("Add feature").priority(TicketPriority.MEDIUM).build())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listTickets_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(ticketService.listTickets(any(), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(ticket(1L, TicketStatus.DRAFT)), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    void getTicketById_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(ticketService.getTicketById(1L)).thenReturn(ticket(1L, TicketStatus.DRAFT));

        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void updateTicket_asTechLead_succeeds() throws Exception {
        authenticateAs("tl-1", "ROLE_TECH_LEAD");
        when(ticketService.updateTicket(eq(1L), any())).thenReturn(ticket(1L, TicketStatus.DRAFT));

        mockMvc.perform(put("/api/tickets/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(TicketUpdateDTO.builder().title("Updated").build())))
                .andExpect(status().isOk());
    }

    @Test
    void updateTicket_asOwnerDeveloper_succeeds() throws Exception {
        authenticateAs("dev-1", "ROLE_DEVELOPER");
        when(ticketService.isOwner(1L, "dev-1")).thenReturn(true);
        when(ticketService.updateTicket(eq(1L), any())).thenReturn(ticket(1L, TicketStatus.DRAFT));

        mockMvc.perform(put("/api/tickets/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(TicketUpdateDTO.builder().title("Updated").build())))
                .andExpect(status().isOk());
    }

    @Test
    void updateTicket_asNonOwnerDeveloper_forbidden() throws Exception {
        authenticateAs("dev-2", "ROLE_DEVELOPER");
        when(ticketService.isOwner(1L, "dev-2")).thenReturn(false);

        mockMvc.perform(put("/api/tickets/1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(TicketUpdateDTO.builder().title("Updated").build())))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeStatus_asTechLead_succeeds() throws Exception {
        authenticateAs("tl-1", "ROLE_TECH_LEAD");
        when(ticketService.changeStatus(eq(1L), any(), eq("tl-1"))).thenReturn(ticket(1L, TicketStatus.SUBMITTED));

        mockMvc.perform(patch("/api/tickets/1/status")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                TicketStatusChangeDTO.builder().newStatus(TicketStatus.SUBMITTED).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    void approve_asTechLead_succeeds() throws Exception {
        authenticateAs("tl-1", "ROLE_TECH_LEAD");
        when(ticketService.approve(1L, "tl-1")).thenReturn(ticket(1L, TicketStatus.APPROVED));

        mockMvc.perform(post("/api/tickets/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void approve_asDeveloper_forbidden() throws Exception {
        authenticateAs("dev-1", "ROLE_DEVELOPER");

        mockMvc.perform(post("/api/tickets/1/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reject_asTechLead_succeeds() throws Exception {
        authenticateAs("tl-1", "ROLE_TECH_LEAD");
        when(ticketService.reject(eq(1L), any(), eq("tl-1"))).thenReturn(ticket(1L, TicketStatus.REJECTED));

        mockMvc.perform(post("/api/tickets/1/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void deploy_asTechLead_succeeds() throws Exception {
        authenticateAs("tl-1", "ROLE_TECH_LEAD");
        when(ticketService.deploy(eq(1L), eq("DEV"), eq("tl-1"))).thenReturn(ticket(1L, TicketStatus.DEPLOYING_DEV));

        mockMvc.perform(post("/api/tickets/1/deploy/DEV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEPLOYING_DEV"));
    }

    @Test
    void updatePipelineStatus_asSystem_succeeds() throws Exception {
        authenticateAs("pipeline-service", "ROLE_SYSTEM");
        when(ticketService.updatePipelineStatus(eq(1L), any(), eq("pipeline-service")))
                .thenReturn(ticket(1L, TicketStatus.DEPLOYED_DEV));

        mockMvc.perform(patch("/api/tickets/1/pipeline-status")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                PipelineStatusUpdateDTO.builder().pipelineExecutionId(42L).environment("DEV").status("SUCCESS").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEPLOYED_DEV"));
    }

    @Test
    void updatePipelineStatus_asTechLeadWithoutSystemRole_forbidden() throws Exception {
        // Un humain, même TECH_LEAD/ADMIN, ne doit jamais pouvoir appeler cet endpoint interne :
        // ROLE_SYSTEM n'est délivré par aucun JWT Keycloak, seul le FeignConfig de pipeline-service
        // le pose. La RoleHierarchy ne fait pas non plus remonter ROLE_SYSTEM vers un rôle humain.
        authenticateAs("tl-1", "ROLE_TECH_LEAD");

        mockMvc.perform(patch("/api/tickets/1/pipeline-status")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                PipelineStatusUpdateDTO.builder().pipelineExecutionId(42L).environment("DEV").status("SUCCESS").build())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getHistory_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(ticketService.getHistory(1L)).thenReturn(List.of(
                TicketHistoryDTO.builder().id(1L).ticketId(1L).newStatus(TicketStatus.DRAFT).build()));

        mockMvc.perform(get("/api/tickets/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].newStatus").value("DRAFT"));
    }

    @Test
    void addComment_asDeveloper_succeeds() throws Exception {
        authenticateAs("dev-1", "ROLE_DEVELOPER");
        when(ticketService.addComment(eq(1L), any(), eq("dev-1"))).thenReturn(
                TicketCommentDTO.builder().id(1L).ticketId(1L).authorUserId("dev-1").content("LGTM").build());

        mockMvc.perform(post("/api/tickets/1/comments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(TicketCommentCreateDTO.builder().content("LGTM").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("LGTM"));
    }

    @Test
    void getComments_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(ticketService.getComments(1L)).thenReturn(List.of(
                TicketCommentDTO.builder().id(1L).ticketId(1L).authorUserId("dev-1").content("LGTM").build()));

        mockMvc.perform(get("/api/tickets/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("LGTM"));
    }

    @Test
    void getStats_asAdmin_succeeds() throws Exception {
        authenticateAs("admin-1", "ROLE_ADMIN");
        when(ticketService.getStats()).thenReturn(TicketStatsDTO.builder().totalTickets(5).build());

        mockMvc.perform(get("/api/tickets/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTickets").value(5));
    }

    @Test
    void getStats_asTechLead_forbidden() throws Exception {
        authenticateAs("tl-1", "ROLE_TECH_LEAD");

        mockMvc.perform(get("/api/tickets/stats"))
                .andExpect(status().isForbidden());
    }

    /**
     * Preuve que la RoleHierarchy (déclarée dans common-lib, voir SecurityConfig) est bien
     * câblée : un utilisateur n'ayant QUE ROLE_ADMIN comme autorité doit quand même passer
     * un @PreAuthorize("hasRole('DEVELOPER')"), car ADMIN > ... > DEVELOPER dans la hiérarchie.
     * Sans le câblage static des beans RoleHierarchy/MethodSecurityExpressionHandler, ce test
     * échouerait avec un 403.
     */
    @Test
    void roleHierarchy_adminOnlyAuthority_canCallDeveloperGuardedEndpoint() throws Exception {
        authenticateAs("admin-1", "ROLE_ADMIN");
        when(ticketService.addComment(eq(1L), any(), eq("admin-1"))).thenReturn(
                TicketCommentDTO.builder().id(1L).ticketId(1L).authorUserId("admin-1").content("via hierarchy").build());

        mockMvc.perform(post("/api/tickets/1/comments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(TicketCommentCreateDTO.builder().content("via hierarchy").build())))
                .andExpect(status().isOk());
    }

    private TicketResponseDTO ticket(Long id, TicketStatus status) {
        return TicketResponseDTO.builder()
                .id(id)
                .title("Add feature")
                .status(status)
                .priority(TicketPriority.MEDIUM)
                .createdByUserId("dev-1")
                .build();
    }

    private void authenticateAs(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority(role))));
    }
}
