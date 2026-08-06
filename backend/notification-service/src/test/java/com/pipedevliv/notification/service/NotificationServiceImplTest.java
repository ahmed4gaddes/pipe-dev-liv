package com.pipedevliv.notification.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.notification.dto.PipelineEventPayload;
import com.pipedevliv.notification.dto.TicketCreatedPayload;
import com.pipedevliv.notification.dto.TicketEventPayload;
import com.pipedevliv.notification.dto.UserSyncedPayload;
import com.pipedevliv.notification.entity.LocalUser;
import com.pipedevliv.notification.entity.Notification;
import com.pipedevliv.notification.entity.NotificationType;
import com.pipedevliv.notification.repository.LocalUserRepository;
import com.pipedevliv.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private LocalUserRepository localUserRepository;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, localUserRepository, new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    }

    @Test
    void handleEvent_userSynced_insertsNewLocalUser() {
        when(localUserRepository.findByKeycloakId("kc-1")).thenReturn(Optional.empty());
        UserSyncedPayload payload = UserSyncedPayload.builder()
                .keycloakId("kc-1").email("a@x.com").fullName("A B").roles("ROLE_DEVELOPER").build();

        service.handleEvent(RabbitMQConstants.USER_SYNCED, payload);

        ArgumentCaptor<LocalUser> captor = ArgumentCaptor.forClass(LocalUser.class);
        verify(localUserRepository).save(captor.capture());
        assertThat(captor.getValue().getKeycloakId()).isEqualTo("kc-1");
        assertThat(captor.getValue().getEmail()).isEqualTo("a@x.com");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void handleEvent_userSynced_updatesExistingLocalUser() {
        LocalUser existing = LocalUser.builder().id(1L).keycloakId("kc-1").email("old@x.com").roles("ROLE_VIEWER").build();
        when(localUserRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(existing));
        UserSyncedPayload payload = UserSyncedPayload.builder()
                .keycloakId("kc-1").email("new@x.com").fullName("A B").roles("ROLE_ADMIN").build();

        service.handleEvent(RabbitMQConstants.USER_SYNCED, payload);

        ArgumentCaptor<LocalUser> captor = ArgumentCaptor.forClass(LocalUser.class);
        verify(localUserRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getEmail()).isEqualTo("new@x.com");
        assertThat(captor.getValue().getRoles()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void handleEvent_ticketCreated_notifiesApproversExcludingCreator() {
        when(localUserRepository.findAll()).thenReturn(List.of(
                LocalUser.builder().keycloakId("tl-1").roles("ROLE_TECH_LEAD").build(),
                LocalUser.builder().keycloakId("rm-1").roles("ROLE_RELEASE_MANAGER").build(),
                LocalUser.builder().keycloakId("dev-2").roles("ROLE_DEVELOPER").build(),
                LocalUser.builder().keycloakId("dev-1").roles("ROLE_DEVELOPER,ROLE_TECH_LEAD").build()
        ));
        TicketCreatedPayload payload = TicketCreatedPayload.builder().id(5L).title("Nouveau ticket").createdByUserId("dev-1").build();

        service.handleEvent(RabbitMQConstants.TICKET_CREATED, payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<String> recipients = captor.getAllValues().stream().map(Notification::getRecipientUserId).toList();
        assertThat(recipients).containsExactlyInAnyOrder("tl-1", "rm-1");
        assertThat(captor.getAllValues()).allMatch(n -> n.getType() == NotificationType.TICKET_CREATED
                && "TICKET".equals(n.getReferenceType()) && n.getReferenceId().equals(5L));
    }

    @Test
    void handleEvent_ticketStatusChanged_notifiesOwnerAndAssigneeExcludingActor() {
        TicketEventPayload payload = TicketEventPayload.builder()
                .ticketId(5L).title("T").oldStatus("SUBMITTED").newStatus("APPROVED")
                .changedByUserId("tl-1").createdByUserId("dev-1").assignedToUserId("dev-2").build();

        service.handleEvent(RabbitMQConstants.TICKET_STATUS_CHANGED, payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<String> recipients = captor.getAllValues().stream().map(Notification::getRecipientUserId).toList();
        assertThat(recipients).containsExactlyInAnyOrder("dev-1", "dev-2");
    }

    @Test
    void handleEvent_ticketStatusChanged_excludesActorEvenIfOwner() {
        TicketEventPayload payload = TicketEventPayload.builder()
                .ticketId(5L).title("T").oldStatus("SUBMITTED").newStatus("CANCELLED")
                .changedByUserId("dev-1").createdByUserId("dev-1").assignedToUserId(null).build();

        service.handleEvent(RabbitMQConstants.TICKET_STATUS_CHANGED, payload);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void handleEvent_ticketApproved_notifiesCreator() {
        TicketEventPayload payload = TicketEventPayload.builder()
                .ticketId(5L).title("T").changedByUserId("tl-1").createdByUserId("dev-1").build();

        service.handleEvent(RabbitMQConstants.TICKET_APPROVED, payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientUserId()).isEqualTo("dev-1");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.TICKET_APPROVED);
    }

    @Test
    void handleEvent_ticketApproved_selfApproval_noNotification() {
        TicketEventPayload payload = TicketEventPayload.builder()
                .ticketId(5L).title("T").changedByUserId("dev-1").createdByUserId("dev-1").build();

        service.handleEvent(RabbitMQConstants.TICKET_APPROVED, payload);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void handleEvent_pipelineStarted_notifiesTriggeringUser() {
        PipelineEventPayload payload = PipelineEventPayload.builder()
                .executionId(1L).ticketId(5L).environment("DEV").status("QUEUED").triggeredByUserId("tl-1").build();

        service.handleEvent(RabbitMQConstants.PIPELINE_STARTED, payload);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientUserId()).isEqualTo("tl-1");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.PIPELINE_STARTED);
        assertThat(captor.getValue().getReferenceType()).isEqualTo("PIPELINE_EXECUTION");
        assertThat(captor.getValue().getReferenceId()).isEqualTo(1L);
    }

    @Test
    void handleEvent_pipelineCompleted_nullTriggeredBy_noOp() {
        PipelineEventPayload payload = PipelineEventPayload.builder()
                .executionId(1L).ticketId(5L).environment("DEV").status("SUCCESS").triggeredByUserId(null).build();

        service.handleEvent(RabbitMQConstants.PIPELINE_COMPLETED, payload);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void handleEvent_unknownRoutingKey_doesNothing() {
        service.handleEvent("some.other.key", new Object());

        verify(notificationRepository, never()).save(any());
        verify(localUserRepository, never()).save(any());
    }

    @Test
    void markRead_ownNotification_marksRead() {
        Notification notification = Notification.builder().id(1L).recipientUserId("user-1").read(false)
                .type(NotificationType.TICKET_CREATED).title("T").build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        service.markRead(1L, "user-1");

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_foreignNotification_throwsAccessDenied() {
        Notification notification = Notification.builder().id(1L).recipientUserId("user-1").read(false)
                .type(NotificationType.TICKET_CREATED).title("T").build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markRead(1L, "user-2"))
                .isInstanceOf(AccessDeniedException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_notFound_throws() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(99L, "user-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllRead_marksOnlyCallersUnread() {
        Notification n1 = Notification.builder().id(1L).recipientUserId("user-1").read(false).type(NotificationType.TICKET_CREATED).title("T").build();
        Notification n2 = Notification.builder().id(2L).recipientUserId("user-1").read(false).type(NotificationType.TICKET_CREATED).title("T").build();
        when(notificationRepository.findByRecipientUserIdAndReadFalse("user-1")).thenReturn(List.of(n1, n2));

        service.markAllRead("user-1");

        assertThat(n1.isRead()).isTrue();
        assertThat(n2.isRead()).isTrue();
        verify(notificationRepository).saveAll(List.of(n1, n2));
    }

    @Test
    void unreadCount_delegatesToRepository() {
        when(notificationRepository.countByRecipientUserIdAndReadFalse("user-1")).thenReturn(3L);

        assertThat(service.unreadCount("user-1")).isEqualTo(3L);
    }

    @Test
    void listForUser_unreadOnlyFalse_usesFullList() {
        Notification n = Notification.builder().id(1L).recipientUserId("user-1").type(NotificationType.TICKET_CREATED).title("T").build();
        when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(n)));

        PageResponse<?> result = service.listForUser("user-1", false, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        verify(notificationRepository, never()).findByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(anyString(), any());
    }

    @Test
    void listForUser_unreadOnlyTrue_usesUnreadList() {
        when(notificationRepository.findByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listForUser("user-1", true, PageRequest.of(0, 20));

        verify(notificationRepository).findByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(anyString(), any());
        verify(notificationRepository, never()).findByRecipientUserIdOrderByCreatedAtDesc(anyString(), any());
    }
}
