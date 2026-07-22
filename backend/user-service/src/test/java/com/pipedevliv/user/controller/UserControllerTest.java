package com.pipedevliv.user.controller;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.user.dto.UserDTO;
import com.pipedevliv.user.dto.UserSyncDTO;
import com.pipedevliv.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllUsers_returnsPagedList() throws Exception {
        UserDTO dto = UserDTO.builder().id(1L).keycloakId("kc-1").email("john@example.com").build();
        PageResponse<UserDTO> page = PageResponse.from(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        when(userService.getAllUsers(any())).thenReturn(page);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].email").value("john@example.com"));
    }

    @Test
    void getUserById_returnsUser() throws Exception {
        when(userService.getUserById(1L)).thenReturn(
                UserDTO.builder().id(1L).keycloakId("kc-1").email("john@example.com").build());

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keycloakId").value("kc-1"));
    }

    @Test
    void getUserByKeycloakId_returnsUser() throws Exception {
        when(userService.getUserByKeycloakId("kc-1")).thenReturn(
                UserDTO.builder().id(1L).keycloakId("kc-1").email("john@example.com").build());

        mockMvc.perform(get("/api/users/by-keycloak-id/kc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("john@example.com"));
    }

    @Test
    void getCurrentUser_syncsFromGatewayHeadersAndAuthenticatedPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("kc-1", null, List.of()));
        when(userService.syncCurrentUser(any(UserSyncDTO.class))).thenReturn(
                UserDTO.builder().id(1L).keycloakId("kc-1").email("john@example.com").fullName("John Doe")
                        .roles("ROLE_DEVELOPER").build());

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Email", "john@example.com")
                        .header("X-User-Name", "John Doe")
                        .header("X-User-Roles", "ROLE_DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keycloakId").value("kc-1"));

        verify(userService).syncCurrentUser(argThat(sync ->
                sync.getKeycloakId().equals("kc-1")
                        && sync.getEmail().equals("john@example.com")
                        && sync.getRoles().equals("ROLE_DEVELOPER")));
    }
}
