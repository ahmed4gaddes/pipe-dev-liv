package com.pipedevliv.notification.repository;

import com.pipedevliv.notification.entity.Notification;
import com.pipedevliv.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private NotificationRepository repository;

    @Test
    void findByRecipientUserIdOrderByCreatedAtDesc_scopesToRecipient() {
        entityManager.persistAndFlush(notification("user-1", true));
        entityManager.persistAndFlush(notification("user-1", false));
        entityManager.persistAndFlush(notification("user-2", false));

        Page<Notification> result = repository.findByRecipientUserIdOrderByCreatedAtDesc("user-1", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void findByRecipientUserIdAndReadFalseOrderByCreatedAtDesc_onlyUnread() {
        entityManager.persistAndFlush(notification("user-1", true));
        entityManager.persistAndFlush(notification("user-1", false));

        Page<Notification> result = repository.findByRecipientUserIdAndReadFalseOrderByCreatedAtDesc("user-1", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isRead()).isFalse();
    }

    @Test
    void countByRecipientUserIdAndReadFalse_countsOnlyUnread() {
        entityManager.persistAndFlush(notification("user-1", true));
        entityManager.persistAndFlush(notification("user-1", false));
        entityManager.persistAndFlush(notification("user-1", false));

        assertThat(repository.countByRecipientUserIdAndReadFalse("user-1")).isEqualTo(2);
    }

    @Test
    void findByRecipientUserIdAndReadFalse_returnsList() {
        entityManager.persistAndFlush(notification("user-1", false));
        entityManager.persistAndFlush(notification("user-2", false));

        List<Notification> result = repository.findByRecipientUserIdAndReadFalse("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecipientUserId()).isEqualTo("user-1");
    }

    private Notification notification(String recipientUserId, boolean read) {
        return Notification.builder()
                .recipientUserId(recipientUserId)
                .type(NotificationType.TICKET_CREATED)
                .title("Titre")
                .message("Message")
                .referenceType("TICKET")
                .referenceId(1L)
                .read(read)
                .build();
    }
}
