package com.pipedevliv.ticket.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.exception.BusinessException;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.ticket.dto.PipelineTriggerDTO;
import com.pipedevliv.ticket.dto.TicketCommentCreateDTO;
import com.pipedevliv.ticket.dto.TicketCommentDTO;
import com.pipedevliv.ticket.dto.TicketCreateDTO;
import com.pipedevliv.ticket.dto.TicketFilterDTO;
import com.pipedevliv.ticket.dto.TicketHistoryDTO;
import com.pipedevliv.ticket.dto.TicketResponseDTO;
import com.pipedevliv.ticket.dto.TicketStatsDTO;
import com.pipedevliv.ticket.dto.TicketStatusChangeDTO;
import com.pipedevliv.ticket.dto.TicketUpdateDTO;
import com.pipedevliv.ticket.entity.Ticket;
import com.pipedevliv.ticket.entity.TicketComment;
import com.pipedevliv.ticket.entity.TicketHistory;
import com.pipedevliv.ticket.entity.TicketStatus;
import com.pipedevliv.ticket.exception.InvalidTransitionException;
import com.pipedevliv.ticket.feign.PipelineServiceClient;
import com.pipedevliv.ticket.messaging.TicketEventPublisher;
import com.pipedevliv.ticket.repository.TicketCommentRepository;
import com.pipedevliv.ticket.repository.TicketHistoryRepository;
import com.pipedevliv.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Nommé explicitement "ticketService" (plutôt que le "ticketServiceImpl" par défaut) pour
// que la référence de bean @ticketService dans les expressions @PreAuthorize du contrôleur
// résolve vers la même valeur en prod et dans les tests @WebMvcTest, où @MockBean enregistre
// le mock sous le nom du champ ("ticketService"), pas sous le nom de la classe d'implémentation.
@Service("ticketService")
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketStateMachine stateMachine;
    private final TicketEventPublisher eventPublisher;
    private final PipelineServiceClient pipelineServiceClient;
    private final RoleHierarchy roleHierarchy;

    @Override
    @Transactional
    public TicketResponseDTO createTicket(TicketCreateDTO dto, String createdByUserId) {
        Ticket ticket = Ticket.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(TicketStatus.DRAFT)
                .priority(dto.getPriority())
                .targetEnvironment(dto.getTargetEnvironment())
                .gitBranch(dto.getGitBranch())
                .gitCommitSha(dto.getGitCommitSha())
                .createdByUserId(createdByUserId)
                .build();
        ticket = ticketRepository.save(ticket);

        writeHistory(ticket, null, createdByUserId, "Création");

        TicketResponseDTO response = toDTO(ticket);
        eventPublisher.publishTicketCreated(response);
        return response;
    }

    @Override
    public PageResponse<TicketResponseDTO> listTickets(TicketFilterDTO filter, Pageable pageable) {
        Page<Ticket> page = ticketRepository.search(
                filter.getStatus(), filter.getPriority(), filter.getCreatedByUserId(), pageable);
        return PageResponse.from(page.map(this::toDTO));
    }

    @Override
    public TicketResponseDTO getTicketById(Long id) {
        return toDTO(findTicketOrThrow(id));
    }

    @Override
    @Transactional
    public TicketResponseDTO updateTicket(Long id, TicketUpdateDTO dto) {
        Ticket ticket = findTicketOrThrow(id);
        if (ticket.getStatus() != TicketStatus.DRAFT) {
            throw new BusinessException("Seul un ticket en DRAFT peut être modifié");
        }
        if (dto.getTitle() != null) {
            ticket.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            ticket.setDescription(dto.getDescription());
        }
        if (dto.getPriority() != null) {
            ticket.setPriority(dto.getPriority());
        }
        if (dto.getTargetEnvironment() != null) {
            ticket.setTargetEnvironment(dto.getTargetEnvironment());
        }
        if (dto.getGitBranch() != null) {
            ticket.setGitBranch(dto.getGitBranch());
        }
        if (dto.getGitCommitSha() != null) {
            ticket.setGitCommitSha(dto.getGitCommitSha());
        }
        if (dto.getAssignedToUserId() != null) {
            ticket.setAssignedToUserId(dto.getAssignedToUserId());
        }
        return toDTO(ticketRepository.save(ticket));
    }

    @Override
    public boolean isOwner(Long id, String userId) {
        return ticketRepository.existsByIdAndCreatedByUserId(id, userId);
    }

    @Override
    @Transactional
    public TicketResponseDTO changeStatus(Long id, TicketStatusChangeDTO dto, String actingUserId) {
        Ticket ticket = findTicketOrThrow(id);
        TicketStatus newStatus = dto.getNewStatus();

        if (!isTechLeadOrAbove() && newStatus != TicketStatus.SUBMITTED && newStatus != TicketStatus.CANCELLED) {
            throw new BusinessException("Un propriétaire non TECH_LEAD ne peut demander que SUBMITTED ou CANCELLED");
        }

        TicketStatus oldStatus = ticket.getStatus();
        stateMachine.validateTransition(oldStatus, newStatus);
        ticket.setStatus(newStatus);
        ticket = ticketRepository.save(ticket);

        writeHistory(ticket, oldStatus, actingUserId, dto.getComment());
        eventPublisher.publishStatusChanged(ticket, oldStatus, actingUserId);

        return toDTO(ticket);
    }

    @Override
    @Transactional
    public TicketResponseDTO approve(Long id, String actingUserId) {
        Ticket ticket = findTicketOrThrow(id);
        TicketStatus oldStatus = ticket.getStatus();

        TicketStatus newStatus;
        boolean triggerPipeline;
        if (oldStatus == TicketStatus.SUBMITTED) {
            newStatus = TicketStatus.APPROVED;
            triggerPipeline = false;
        } else if (oldStatus == TicketStatus.PENDING_PROD_APPROVAL) {
            newStatus = TicketStatus.DEPLOYING_PROD;
            triggerPipeline = true;
        } else {
            throw new InvalidTransitionException(oldStatus, TicketStatus.APPROVED);
        }

        stateMachine.validateTransition(oldStatus, newStatus);

        if (triggerPipeline) {
            pipelineServiceClient.triggerPipeline(PipelineTriggerDTO.builder()
                    .ticketId(ticket.getId())
                    .targetEnvironment("PROD")
                    .gitBranch(ticket.getGitBranch())
                    .gitCommitSha(ticket.getGitCommitSha())
                    .build());
        }

        ticket.setStatus(newStatus);
        ticket.setApprovedByUserId(actingUserId);
        ticket = ticketRepository.save(ticket);

        writeHistory(ticket, oldStatus, actingUserId, "Approuvé");
        eventPublisher.publishApproved(ticket, oldStatus, actingUserId);
        eventPublisher.publishStatusChanged(ticket, oldStatus, actingUserId);

        return toDTO(ticket);
    }

    @Override
    @Transactional
    public TicketResponseDTO reject(Long id, String comment, String actingUserId) {
        Ticket ticket = findTicketOrThrow(id);
        TicketStatus oldStatus = ticket.getStatus();
        stateMachine.validateTransition(oldStatus, TicketStatus.REJECTED);

        ticket.setStatus(TicketStatus.REJECTED);
        ticket = ticketRepository.save(ticket);

        writeHistory(ticket, oldStatus, actingUserId, comment != null ? comment : "Rejeté");
        eventPublisher.publishStatusChanged(ticket, oldStatus, actingUserId);

        return toDTO(ticket);
    }

    @Override
    @Transactional
    public TicketResponseDTO deploy(Long id, String env, String actingUserId) {
        Ticket ticket = findTicketOrThrow(id);
        TicketStatus oldStatus = ticket.getStatus();
        String normalizedEnv = env == null ? "" : env.toUpperCase();

        TicketStatus newStatus = switch (normalizedEnv) {
            case "DEV" -> TicketStatus.DEPLOYING_DEV;
            case "TEST" -> TicketStatus.DEPLOYING_TEST;
            case "PROD" -> throw new BusinessException("Le déploiement PROD passe par /approve, pas par /deploy");
            default -> throw new BusinessException("Environnement inconnu : " + env);
        };

        stateMachine.validateTransition(oldStatus, newStatus);

        pipelineServiceClient.triggerPipeline(PipelineTriggerDTO.builder()
                .ticketId(ticket.getId())
                .targetEnvironment(normalizedEnv)
                .gitBranch(ticket.getGitBranch())
                .gitCommitSha(ticket.getGitCommitSha())
                .build());

        ticket.setStatus(newStatus);
        ticket = ticketRepository.save(ticket);

        writeHistory(ticket, oldStatus, actingUserId, "Déploiement " + normalizedEnv + " déclenché");
        eventPublisher.publishStatusChanged(ticket, oldStatus, actingUserId);

        return toDTO(ticket);
    }

    @Override
    public List<TicketHistoryDTO> getHistory(Long id) {
        findTicketOrThrow(id);
        return ticketHistoryRepository.findByTicketIdOrderByChangedAtAsc(id).stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public TicketCommentDTO addComment(Long id, TicketCommentCreateDTO dto, String authorUserId) {
        findTicketOrThrow(id);
        TicketComment comment = TicketComment.builder()
                .ticketId(id)
                .authorUserId(authorUserId)
                .content(dto.getContent())
                .build();
        return toDTO(ticketCommentRepository.save(comment));
    }

    @Override
    public TicketStatsDTO getStats() {
        List<Ticket> all = ticketRepository.findAll();
        Map<String, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(t -> t.getStatus().name(), Collectors.counting()));
        Map<String, Long> byPriority = all.stream()
                .collect(Collectors.groupingBy(t -> t.getPriority().name(), Collectors.counting()));
        return TicketStatsDTO.builder()
                .totalTickets(all.size())
                .countByStatus(byStatus)
                .countByPriority(byPriority)
                .build();
    }

    private Ticket findTicketOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", id));
    }

    private void writeHistory(Ticket ticket, TicketStatus oldStatus, String changedByUserId, String comment) {
        ticketHistoryRepository.save(TicketHistory.builder()
                .ticketId(ticket.getId())
                .changedByUserId(changedByUserId)
                .oldStatus(oldStatus)
                .newStatus(ticket.getStatus())
                .comment(comment)
                .build());
    }

    private boolean isTechLeadOrAbove() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities()).stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TECH_LEAD"));
    }

    private TicketResponseDTO toDTO(Ticket ticket) {
        return TicketResponseDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .targetEnvironment(ticket.getTargetEnvironment())
                .gitBranch(ticket.getGitBranch())
                .gitCommitSha(ticket.getGitCommitSha())
                .createdByUserId(ticket.getCreatedByUserId())
                .assignedToUserId(ticket.getAssignedToUserId())
                .approvedByUserId(ticket.getApprovedByUserId())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    private TicketHistoryDTO toDTO(TicketHistory history) {
        return TicketHistoryDTO.builder()
                .id(history.getId())
                .ticketId(history.getTicketId())
                .changedByUserId(history.getChangedByUserId())
                .oldStatus(history.getOldStatus())
                .newStatus(history.getNewStatus())
                .comment(history.getComment())
                .changedAt(history.getChangedAt())
                .build();
    }

    private TicketCommentDTO toDTO(TicketComment comment) {
        return TicketCommentDTO.builder()
                .id(comment.getId())
                .ticketId(comment.getTicketId())
                .authorUserId(comment.getAuthorUserId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
