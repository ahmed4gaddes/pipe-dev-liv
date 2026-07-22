package com.pipedevliv.ticket.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.ticket.dto.TicketCommentCreateDTO;
import com.pipedevliv.ticket.dto.TicketCommentDTO;
import com.pipedevliv.ticket.dto.TicketCreateDTO;
import com.pipedevliv.ticket.dto.TicketFilterDTO;
import com.pipedevliv.ticket.dto.TicketHistoryDTO;
import com.pipedevliv.ticket.dto.TicketResponseDTO;
import com.pipedevliv.ticket.dto.TicketStatsDTO;
import com.pipedevliv.ticket.dto.TicketStatusChangeDTO;
import com.pipedevliv.ticket.dto.TicketUpdateDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TicketService {

    TicketResponseDTO createTicket(TicketCreateDTO dto, String createdByUserId);

    PageResponse<TicketResponseDTO> listTickets(TicketFilterDTO filter, Pageable pageable);

    TicketResponseDTO getTicketById(Long id);

    TicketResponseDTO updateTicket(Long id, TicketUpdateDTO dto);

    boolean isOwner(Long id, String userId);

    TicketResponseDTO changeStatus(Long id, TicketStatusChangeDTO dto, String actingUserId);

    TicketResponseDTO approve(Long id, String actingUserId);

    TicketResponseDTO reject(Long id, String comment, String actingUserId);

    TicketResponseDTO deploy(Long id, String env, String actingUserId);

    List<TicketHistoryDTO> getHistory(Long id);

    TicketCommentDTO addComment(Long id, TicketCommentCreateDTO dto, String authorUserId);

    TicketStatsDTO getStats();
}
