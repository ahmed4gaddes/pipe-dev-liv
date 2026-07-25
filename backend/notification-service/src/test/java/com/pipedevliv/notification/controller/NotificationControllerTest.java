package com.pipedevliv.notification.controller;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.exception.GlobalExceptionHandler;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.common.security.SecurityConfig;
import com.pipedevliv.notification.dto.NotificationDTO;
import com.pipedevliv.notification.entity.NotificationType;
import com.pipedevliv.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Même piège/solution que TicketControllerTest/PipelineControllerTest : @WebMvcTest ne charge
// pas les @AutoConfiguration tierces (common-lib.SecurityConfig) par défaut, donc @PreAuthorize
// serait silencieusement inerte sans cet @Import explicite.
@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(notificationService.listForUser(eq("viewer-1"), eq(false), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of(notification(1L)), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    void list_unreadOnly_passedThrough() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(notificationService.listForUser(eq("viewer-1"), eq(true), any()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of())));

        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true"))
                .andExpect(status().isOk());

        verify(notificationService).listForUser(eq("viewer-1"), eq(true), any());
    }

    @Test
    void unreadCount_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        when(notificationService.unreadCount("viewer-1")).thenReturn(4L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(4));
    }

    @Test
    void markRead_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");

        mockMvc.perform(patch("/api/notifications/1/read"))
                .andExpect(status().isOk());

        verify(notificationService).markRead(1L, "viewer-1");
    }

    @Test
    void markRead_foreignNotification_returnsForbidden() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        doThrow(new AccessDeniedException("Cette notification ne vous appartient pas"))
                .when(notificationService).markRead(1L, "viewer-1");

        mockMvc.perform(patch("/api/notifications/1/read"))
                .andExpect(status().isForbidden());
    }

    @Test
    void markRead_notFound_returnsNotFound() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");
        doThrow(new ResourceNotFoundException("Notification", "id", 99L))
                .when(notificationService).markRead(99L, "viewer-1");

        mockMvc.perform(patch("/api/notifications/99/read"))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAllRead_asViewer_succeeds() throws Exception {
        authenticateAs("viewer-1", "ROLE_VIEWER");

        mockMvc.perform(patch("/api/notifications/read-all"))
                .andExpect(status().isOk());

        verify(notificationService).markAllRead("viewer-1");
    }

    private NotificationDTO notification(Long id) {
        return NotificationDTO.builder().id(id).type(NotificationType.TICKET_CREATED).title("T").read(false).build();
    }

    private void authenticateAs(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority(role))));
    }
}
