package com.pipedevliv.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.notification.dto.NotificationDTO;
import com.pipedevliv.notification.dto.PipelineEventPayload;
import com.pipedevliv.notification.dto.TicketCreatedPayload;
import com.pipedevliv.notification.dto.TicketEventPayload;
import com.pipedevliv.notification.dto.UserSyncedPayload;
import com.pipedevliv.notification.entity.LocalUser;
import com.pipedevliv.notification.entity.Notification;
import com.pipedevliv.notification.entity.NotificationType;
import com.pipedevliv.notification.repository.LocalUserRepository;
import com.pipedevliv.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final Set<String> APPROVER_ROLES = Set.of("ROLE_TECH_LEAD", "ROLE_RELEASE_MANAGER", "ROLE_ADMIN");

    private final NotificationRepository notificationRepository;
    private final LocalUserRepository localUserRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void handleEvent(String routingKey, Object payload) {
        try {
            // Si Spring AMQP n'a pas pu désérialiser (TypeId mismatch), on reçoit un Message brut
            Object jsonPayload = payload;
            if (payload instanceof org.springframework.amqp.core.Message msg) {
                jsonPayload = objectMapper.readValue(msg.getBody(), Object.class);
            }

            switch (routingKey) {
                case RabbitMQConstants.USER_SYNCED ->
                        handleUserSynced(objectMapper.convertValue(jsonPayload, UserSyncedPayload.class));
                case RabbitMQConstants.TICKET_CREATED ->
                        handleTicketCreated(objectMapper.convertValue(jsonPayload, TicketCreatedPayload.class));
                case RabbitMQConstants.TICKET_STATUS_CHANGED ->
                        handleTicketStatusChanged(objectMapper.convertValue(jsonPayload, TicketEventPayload.class));
                case RabbitMQConstants.TICKET_APPROVED ->
                        handleTicketApproved(objectMapper.convertValue(jsonPayload, TicketEventPayload.class));
                case RabbitMQConstants.PIPELINE_STARTED ->
                        handlePipelineEvent(objectMapper.convertValue(jsonPayload, PipelineEventPayload.class), NotificationType.PIPELINE_STARTED);
                case RabbitMQConstants.PIPELINE_COMPLETED ->
                        handlePipelineEvent(objectMapper.convertValue(jsonPayload, PipelineEventPayload.class), NotificationType.PIPELINE_COMPLETED);
                case RabbitMQConstants.PIPELINE_FAILED ->
                        handlePipelineEvent(objectMapper.convertValue(jsonPayload, PipelineEventPayload.class), NotificationType.PIPELINE_FAILED);
                default -> log.warn("Routing key non gérée par notification-service : {}", routingKey);
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement de l'événement notification [{}] : {}", routingKey, e.getMessage(), e);
        }
    }

    private void handleUserSynced(UserSyncedPayload payload) {
        LocalUser user = localUserRepository.findByKeycloakId(payload.getKeycloakId())
                .map(existing -> {
                    existing.setEmail(payload.getEmail());
                    existing.setFullName(payload.getFullName());
                    existing.setRoles(payload.getRoles());
                    return existing;
                })
                .orElseGet(() -> LocalUser.builder()
                        .keycloakId(payload.getKeycloakId())
                        .email(payload.getEmail())
                        .fullName(payload.getFullName())
                        .roles(payload.getRoles())
                        .build());
        localUserRepository.save(user);
    }

    private void handleTicketCreated(TicketCreatedPayload payload) {
        List<String> recipients = localUserRepository.findAll().stream()
                .filter(u -> isApprover(u.getRoles()))
                .map(LocalUser::getKeycloakId)
                .filter(id -> !id.equals(payload.getCreatedByUserId()))
                .distinct()
                .toList();

        String message = "Nouveau ticket à approuver : " + payload.getTitle();
        recipients.forEach(recipientId -> save(recipientId, NotificationType.TICKET_CREATED,
                "Nouveau ticket", message, "TICKET", payload.getId()));
    }

    private void handleTicketStatusChanged(TicketEventPayload payload) {
        String message = "Ticket #%d : %s → %s".formatted(payload.getTicketId(), payload.getOldStatus(), payload.getNewStatus());
        resolveOwnerAndAssignee(payload).forEach(recipientId -> save(recipientId, NotificationType.TICKET_STATUS_CHANGED,
                "Statut du ticket modifié", message, "TICKET", payload.getTicketId()));
    }

    private void handleTicketApproved(TicketEventPayload payload) {
        String message = "Votre ticket \"" + payload.getTitle() + "\" a été approuvé";
        if (payload.getCreatedByUserId() != null && !payload.getCreatedByUserId().equals(payload.getChangedByUserId())) {
            save(payload.getCreatedByUserId(), NotificationType.TICKET_APPROVED,
                    "Ticket approuvé", message, "TICKET", payload.getTicketId());
        }
    }

    private void handlePipelineEvent(PipelineEventPayload payload, NotificationType type) {
        if (payload.getTriggeredByUserId() == null) {
            return;
        }
        String message = "Pipeline %s (%s) : %s".formatted(payload.getExecutionId(), payload.getEnvironment(), payload.getStatus());
        save(payload.getTriggeredByUserId(), type, "Mise à jour du pipeline", message,
                "PIPELINE_EXECUTION", payload.getExecutionId());
    }

    private List<String> resolveOwnerAndAssignee(TicketEventPayload payload) {
        Set<String> recipients = new LinkedHashSet<>();
        if (payload.getCreatedByUserId() != null) {
            recipients.add(payload.getCreatedByUserId());
        }
        if (payload.getAssignedToUserId() != null) {
            recipients.add(payload.getAssignedToUserId());
        }
        recipients.remove(payload.getChangedByUserId());
        return new ArrayList<>(recipients);
    }

    private boolean isApprover(String roles) {
        if (roles == null) {
            return false;
        }
        return APPROVER_ROLES.stream().anyMatch(roles::contains);
    }

    private void save(String recipientUserId, NotificationType type, String title, String message,
                       String referenceType, Long referenceId) {
        notificationRepository.save(Notification.builder()
                .recipientUserId(recipientUserId)
                .type(type)
                .title(title)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build());
    }

    @Override
    public PageResponse<NotificationDTO> listForUser(String userId, boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? notificationRepository.findByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page.map(this::toDTO));
    }

    @Override
    public long unreadCount(String userId) {
        return notificationRepository.countByRecipientUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(Long notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        if (!notification.getRecipientUserId().equals(userId)) {
            throw new AccessDeniedException("Cette notification ne vous appartient pas");
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByRecipientUserIdAndReadFalse(userId);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> {
            n.setRead(true);
            n.setReadAt(now);
        });
        notificationRepository.saveAll(unread);
    }

    private NotificationDTO toDTO(Notification notification) {
        return NotificationDTO.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
