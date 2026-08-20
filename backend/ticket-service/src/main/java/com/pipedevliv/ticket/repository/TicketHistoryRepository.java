package com.pipedevliv.ticket.repository;

import com.pipedevliv.ticket.entity.TicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

    List<TicketHistory> findByTicketIdOrderByChangedAtAsc(Long ticketId);

    void deleteByTicketId(Long ticketId);
}
