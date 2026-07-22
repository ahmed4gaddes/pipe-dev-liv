package com.pipedevliv.ticket.repository;

import com.pipedevliv.ticket.entity.Ticket;
import com.pipedevliv.ticket.entity.TicketPriority;
import com.pipedevliv.ticket.entity.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TicketRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TicketRepository repository;

    @Test
    void search_filtersByStatusPriorityAndOwner() {
        entityManager.persistAndFlush(ticket("Ticket A", TicketStatus.DRAFT, TicketPriority.HIGH, "dev-1"));
        entityManager.persistAndFlush(ticket("Ticket B", TicketStatus.SUBMITTED, TicketPriority.LOW, "dev-2"));

        Page<Ticket> byStatus = repository.search(TicketStatus.DRAFT, null, null, PageRequest.of(0, 10));
        assertThat(byStatus.getContent()).extracting(Ticket::getTitle).containsExactly("Ticket A");

        Page<Ticket> byOwner = repository.search(null, null, "dev-2", PageRequest.of(0, 10));
        assertThat(byOwner.getContent()).extracting(Ticket::getTitle).containsExactly("Ticket B");

        Page<Ticket> all = repository.search(null, null, null, PageRequest.of(0, 10));
        assertThat(all.getContent()).hasSize(2);
    }

    @Test
    void existsByIdAndCreatedByUserId_trueForOwner_falseOtherwise() {
        Ticket saved = entityManager.persistAndFlush(ticket("Ticket A", TicketStatus.DRAFT, TicketPriority.HIGH, "dev-1"));

        assertThat(repository.existsByIdAndCreatedByUserId(saved.getId(), "dev-1")).isTrue();
        assertThat(repository.existsByIdAndCreatedByUserId(saved.getId(), "dev-2")).isFalse();
    }

    private Ticket ticket(String title, TicketStatus status, TicketPriority priority, String createdByUserId) {
        return Ticket.builder()
                .title(title)
                .status(status)
                .priority(priority)
                .createdByUserId(createdByUserId)
                .build();
    }
}
