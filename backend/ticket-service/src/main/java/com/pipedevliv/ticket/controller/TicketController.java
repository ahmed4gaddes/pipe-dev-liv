package com.pipedevliv.ticket.controller;

import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.ticket.dto.PipelineStatusUpdateDTO;
import com.pipedevliv.ticket.dto.TicketActionCommentDTO;
import com.pipedevliv.ticket.dto.TicketCommentCreateDTO;
import com.pipedevliv.ticket.dto.TicketCommentDTO;
import com.pipedevliv.ticket.dto.TicketCreateDTO;
import com.pipedevliv.ticket.dto.TicketFilterDTO;
import com.pipedevliv.ticket.dto.TicketHistoryDTO;
import com.pipedevliv.ticket.dto.TicketResponseDTO;
import com.pipedevliv.ticket.dto.TicketStatsDTO;
import com.pipedevliv.ticket.dto.TicketStatusChangeDTO;
import com.pipedevliv.ticket.dto.TicketUpdateDTO;
import com.pipedevliv.ticket.entity.TicketPriority;
import com.pipedevliv.ticket.entity.TicketStatus;
import com.pipedevliv.ticket.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @PreAuthorize("hasRole('DEVELOPER')")
    public ApiResponse<TicketResponseDTO> createTicket(@Valid @RequestBody TicketCreateDTO dto) {
        return ApiResponse.success(ticketService.createTicket(dto, currentUserId()), "Ticket créé");
    }

    @GetMapping
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<PageResponse<TicketResponseDTO>> listTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String createdByUserId,
            @PageableDefault(size = 20) Pageable pageable) {
        TicketFilterDTO filter = TicketFilterDTO.builder()
                .status(status)
                .priority(priority)
                .createdByUserId(createdByUserId)
                .build();
        return ApiResponse.success(ticketService.listTickets(filter, pageable), "Tickets récupérés");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<TicketResponseDTO> getTicketById(@PathVariable Long id) {
        return ApiResponse.success(ticketService.getTicketById(id), "Ticket récupéré");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TECH_LEAD') or (hasRole('DEVELOPER') and @ticketService.isOwner(#id, authentication.name))")
    public ApiResponse<TicketResponseDTO> updateTicket(@PathVariable Long id, @Valid @RequestBody TicketUpdateDTO dto) {
        return ApiResponse.success(ticketService.updateTicket(id, dto), "Ticket mis à jour");
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('TECH_LEAD') or (hasRole('DEVELOPER') and @ticketService.isOwner(#id, authentication.name))")
    public ApiResponse<TicketResponseDTO> changeStatus(@PathVariable Long id, @Valid @RequestBody TicketStatusChangeDTO dto) {
        return ApiResponse.success(ticketService.changeStatus(id, dto, currentUserId()), "Statut modifié");
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('TECH_LEAD')")
    public ApiResponse<TicketResponseDTO> approve(@PathVariable Long id) {
        return ApiResponse.success(ticketService.approve(id, currentUserId()), "Ticket approuvé");
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('TECH_LEAD')")
    public ApiResponse<TicketResponseDTO> reject(@PathVariable Long id, @RequestBody(required = false) TicketActionCommentDTO body) {
        String comment = body != null ? body.getComment() : null;
        return ApiResponse.success(ticketService.reject(id, comment, currentUserId()), "Ticket rejeté");
    }

    @PostMapping("/{id}/deploy/{env}")
    @PreAuthorize("hasRole('TECH_LEAD')")
    public ApiResponse<TicketResponseDTO> deploy(@PathVariable Long id, @PathVariable String env) {
        return ApiResponse.success(ticketService.deploy(id, env, currentUserId()), "Déploiement déclenché");
    }

    @PatchMapping("/{id}/pipeline-status")
    @PreAuthorize("hasRole('SYSTEM')")
    public ApiResponse<TicketResponseDTO> updatePipelineStatus(@PathVariable Long id, @RequestBody PipelineStatusUpdateDTO dto) {
        return ApiResponse.success(ticketService.updatePipelineStatus(id, dto, currentUserId()), "Statut de déploiement mis à jour");
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('VIEWER')")
    public ApiResponse<List<TicketHistoryDTO>> getHistory(@PathVariable Long id) {
        return ApiResponse.success(ticketService.getHistory(id), "Historique récupéré");
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasRole('DEVELOPER')")
    public ApiResponse<TicketCommentDTO> addComment(@PathVariable Long id, @Valid @RequestBody TicketCommentCreateDTO dto) {
        return ApiResponse.success(ticketService.addComment(id, dto, currentUserId()), "Commentaire ajouté");
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TicketStatsDTO> getStats() {
        return ApiResponse.success(ticketService.getStats(), "Statistiques récupérées");
    }

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
