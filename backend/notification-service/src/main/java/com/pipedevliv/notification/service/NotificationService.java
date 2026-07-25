package com.pipedevliv.notification.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.notification.dto.NotificationDTO;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    void handleEvent(String routingKey, Object payload);

    PageResponse<NotificationDTO> listForUser(String userId, boolean unreadOnly, Pageable pageable);

    long unreadCount(String userId);

    void markRead(Long notificationId, String userId);

    void markAllRead(String userId);
}
