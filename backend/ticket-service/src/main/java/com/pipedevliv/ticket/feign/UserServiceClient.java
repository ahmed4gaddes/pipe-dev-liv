package com.pipedevliv.ticket.feign;

import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.ticket.dto.UserSummaryDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "USER-SERVICE")
public interface UserServiceClient {

    @GetMapping("/api/users/by-keycloak-id/{keycloakId}")
    ApiResponse<UserSummaryDTO> getUserByKeycloakId(@PathVariable String keycloakId);
}
