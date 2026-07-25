package com.pipedevliv.audit.controller;

import com.pipedevliv.audit.dto.AuditLogDTO;
import com.pipedevliv.audit.entity.AuditEventType;
import com.pipedevliv.audit.service.AuditService;
import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.exception.GlobalExceptionHandler;
import com.pipedevliv.common.security.SecurityConfig;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Même piège/solution que les autres @WebMvcTest du projet : @WebMvcTest ne charge pas les
// @AutoConfiguration tierces (common-lib.SecurityConfig) par défaut, donc @PreAuthorize serait
// silencieusement inerte sans cet @Import explicite.
@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_asAdmin_succeeds() throws Exception {
        authenticateAs("admin-1", "ROLE_ADMIN");
        when(auditService.listLogs(any(), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(entry(1L)), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    void list_asTechLead_forbidden() throws Exception {
        authenticateAs("tl-1", "ROLE_TECH_LEAD");

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getById_asAdmin_succeeds() throws Exception {
        authenticateAs("admin-1", "ROLE_ADMIN");
        when(auditService.getLogById(1L)).thenReturn(entry(1L));

        mockMvc.perform(get("/api/audit-logs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getById_asTechLead_forbidden() throws Exception {
        authenticateAs("tl-1", "ROLE_TECH_LEAD");

        mockMvc.perform(get("/api/audit-logs/1"))
                .andExpect(status().isForbidden());
    }

    private AuditLogDTO entry(Long id) {
        return AuditLogDTO.builder().id(id).eventType(AuditEventType.TICKET_CREATED)
                .entityType("TICKET").entityId(5L).actorUserId("dev-1").description("d").build();
    }

    private void authenticateAs(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority(role))));
    }
}
