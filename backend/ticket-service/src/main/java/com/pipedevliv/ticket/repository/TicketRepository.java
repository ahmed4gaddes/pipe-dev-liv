package com.pipedevliv.ticket.repository;

import com.pipedevliv.ticket.entity.Ticket;
import com.pipedevliv.ticket.entity.TicketPriority;
import com.pipedevliv.ticket.entity.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("SELECT t FROM Ticket t WHERE (:status IS NULL OR t.status = :status) "
            + "AND (:priority IS NULL OR t.priority = :priority) "
            + "AND (:createdByUserId IS NULL OR t.createdByUserId = :createdByUserId)")
    Page<Ticket> search(@Param("status") TicketStatus status,
                         @Param("priority") TicketPriority priority,
                         @Param("createdByUserId") String createdByUserId,
                         Pageable pageable);

    boolean existsByIdAndCreatedByUserId(Long id, String createdByUserId);
}
