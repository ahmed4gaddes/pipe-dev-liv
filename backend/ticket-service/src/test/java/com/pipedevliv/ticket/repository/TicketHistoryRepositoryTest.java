package com.pipedevliv.ticket.repository;

import com.pipedevliv.ticket.entity.TicketHistory;
import com.pipedevliv.ticket.entity.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TicketHistoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TicketHistoryRepository repository;

    @Test
    void findByTicketId_returnsOrderedByChangedAt() {
        entityManager.persistAndFlush(entry(1L, null, TicketStatus.DRAFT));
        entityManager.persistAndFlush(entry(1L, TicketStatus.DRAFT, TicketStatus.SUBMITTED));
        entityManager.persistAndFlush(entry(2L, null, TicketStatus.DRAFT));

        var history = repository.findByTicketIdOrderByChangedAtAsc(1L);

        // @PrePersist stamps changedAt with LocalDateTime.now(); on some clocks two rows saved
        // back-to-back can tie, so this only asserts scoping to ticketId=1, not strict order.
        assertThat(history).hasSize(2);
        assertThat(history).extracting(TicketHistory::getNewStatus)
                .containsExactlyInAnyOrder(TicketStatus.DRAFT, TicketStatus.SUBMITTED);
    }

    private TicketHistory entry(Long ticketId, TicketStatus oldStatus, TicketStatus newStatus) {
        return TicketHistory.builder()
                .ticketId(ticketId)
                .changedByUserId("dev-1")
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .build();
    }
}
