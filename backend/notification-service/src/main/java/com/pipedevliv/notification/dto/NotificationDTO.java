package com.pipedevliv.notification.dto;

import com.pipedevliv.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private String referenceType;
    private Long referenceId;
    private boolean read;
    private LocalDateTime createdAt;
}
