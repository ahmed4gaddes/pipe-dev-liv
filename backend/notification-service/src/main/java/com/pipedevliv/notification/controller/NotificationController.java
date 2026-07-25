package com.pipedevliv.notification.controller;

import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.notification.dto.NotificationDTO;
import com.pipedevliv.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<PageResponse<NotificationDTO>> list(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                notificationService.listForUser(currentUserId(), unreadOnly, pageable), "Notifications récupérées");
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("count", notificationService.unreadCount(currentUserId())), "Compteur récupéré");
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, currentUserId());
        return ApiResponse.success(null, "Notification marquée comme lue");
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead(currentUserId());
        return ApiResponse.success(null, "Toutes les notifications ont été marquées comme lues");
    }

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
