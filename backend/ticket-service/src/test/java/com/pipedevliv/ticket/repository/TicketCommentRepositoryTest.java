package com.pipedevliv.ticket.repository;

import com.pipedevliv.ticket.entity.TicketComment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TicketCommentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TicketCommentRepository repository;

    @Test
    void findByTicketId_scopedToTicket() {
        entityManager.persistAndFlush(comment(1L, "dev-1", "First"));
        entityManager.persistAndFlush(comment(1L, "dev-2", "Second"));
        entityManager.persistAndFlush(comment(2L, "dev-1", "Other ticket"));

        var comments = repository.findByTicketIdOrderByCreatedAtAsc(1L);

        assertThat(comments).hasSize(2);
        assertThat(comments).extracting(TicketComment::getContent).containsExactlyInAnyOrder("First", "Second");
    }

    private TicketComment comment(Long ticketId, String authorUserId, String content) {
        return TicketComment.builder()
                .ticketId(ticketId)
                .authorUserId(authorUserId)
                .content(content)
                .build();
    }
}
