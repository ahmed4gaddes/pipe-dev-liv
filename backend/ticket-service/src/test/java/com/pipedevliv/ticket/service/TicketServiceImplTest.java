package com.pipedevliv.ticket.service;

import com.pipedevliv.common.dto.ApiResponse;
import com.pipedevliv.common.exception.BusinessException;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.ticket.dto.PipelineExecutionDTO;
import com.pipedevliv.ticket.dto.PipelineStatusUpdateDTO;
import com.pipedevliv.ticket.dto.TicketCommentCreateDTO;
import com.pipedevliv.ticket.dto.TicketCreateDTO;
import com.pipedevliv.ticket.dto.TicketResponseDTO;
import com.pipedevliv.ticket.dto.TicketStatusChangeDTO;
import com.pipedevliv.ticket.dto.TicketUpdateDTO;
import com.pipedevliv.ticket.entity.Ticket;
import com.pipedevliv.ticket.entity.TicketComment;
import com.pipedevliv.ticket.entity.TicketPriority;
import com.pipedevliv.ticket.entity.TicketStatus;
import com.pipedevliv.ticket.exception.InvalidTransitionException;
import com.pipedevliv.ticket.feign.PipelineServiceClient;
import com.pipedevliv.ticket.messaging.TicketEventPublisher;
import com.pipedevliv.ticket.repository.TicketCommentRepository;
import com.pipedevliv.ticket.repository.TicketHistoryRepository;
import com.pipedevliv.ticket.repository.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketCommentRepository ticketCommentRepository;
    @Mock
    private TicketHistoryRepository ticketHistoryRepository;
    @Mock
    private TicketEventPublisher eventPublisher;
    @Mock
    private PipelineServiceClient pipelineServiceClient;

    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        var roleHierarchy = RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_RELEASE_MANAGER
                ROLE_RELEASE_MANAGER > ROLE_TECH_LEAD
                ROLE_TECH_LEAD > ROLE_DEVELOPER
                ROLE_DEVELOPER > ROLE_VIEWER
                """);
        ticketService = new TicketServiceImpl(
                ticketRepository, ticketCommentRepository, ticketHistoryRepository,
                new TicketStateMachine(), eventPublisher, pipelineServiceClient, roleHierarchy);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTicket_startsInDraft_andPublishesTicketCreated() {
        TicketCreateDTO dto = TicketCreateDTO.builder().title("Add feature").priority(TicketPriority.MEDIUM).build();
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        TicketResponseDTO result = ticketService.createTicket(dto, "dev-1");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.DRAFT);
        assertThat(result.getCreatedByUserId()).isEqualTo("dev-1");
        verify(ticketHistoryRepository).save(any());
        verify(eventPublisher).publishTicketCreated(any(TicketResponseDTO.class));
    }

    @Test
    void getTicketById_notFound_throws() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getTicketById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTicket_whenNotDraft_throwsBusinessException() {
        Ticket ticket = existingTicket(TicketStatus.SUBMITTED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.updateTicket(1L, TicketUpdateDTO.builder().title("x").build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void changeStatus_illegalTransition_throwsInvalidTransitionException() {
        Ticket ticket = existingTicket(TicketStatus.DRAFT);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        authenticateAs("tl-1", "ROLE_TECH_LEAD");

        TicketStatusChangeDTO dto = TicketStatusChangeDTO.builder().newStatus(TicketStatus.APPROVED).build();

        assertThatThrownBy(() -> ticketService.changeStatus(1L, dto, "tl-1"))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void changeStatus_ownerRequestingSubmitted_succeeds() {
        Ticket ticket = existingTicket(TicketStatus.DRAFT);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        authenticateAs("dev-1", "ROLE_DEVELOPER");

        TicketStatusChangeDTO dto = TicketStatusChangeDTO.builder().newStatus(TicketStatus.SUBMITTED).build();

        TicketResponseDTO result = ticketService.changeStatus(1L, dto, "dev-1");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.SUBMITTED);
        verify(eventPublisher).publishStatusChanged(any(Ticket.class), eq(TicketStatus.DRAFT), eq("dev-1"));
    }

    @Test
    void changeStatus_ownerRequestingSomethingElse_rejected() {
        Ticket ticket = existingTicket(TicketStatus.SUBMITTED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        authenticateAs("dev-1", "ROLE_DEVELOPER");

        TicketStatusChangeDTO dto = TicketStatusChangeDTO.builder().newStatus(TicketStatus.APPROVED).build();

        assertThatThrownBy(() -> ticketService.changeStatus(1L, dto, "dev-1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void approve_fromSubmitted_movesToApproved_noFeignCall() {
        Ticket ticket = existingTicket(TicketStatus.SUBMITTED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketResponseDTO result = ticketService.approve(1L, "tl-1");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.APPROVED);
        assertThat(result.getApprovedByUserId()).isEqualTo("tl-1");
        verify(pipelineServiceClient, never()).triggerPipeline(any());
    }

    @Test
    void approve_fromPendingProdApproval_movesToDeployingProd_andCallsPipelineServiceClient() {
        Ticket ticket = existingTicket(TicketStatus.PENDING_PROD_APPROVAL);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipelineServiceClient.triggerPipeline(any()))
                .thenReturn(ApiResponse.success(PipelineExecutionDTO.builder().build(), "ok"));

        TicketResponseDTO result = ticketService.approve(1L, "rm-1");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.DEPLOYING_PROD);
        verify(pipelineServiceClient).triggerPipeline(any());
    }

    @Test
    void deploy_prodEnv_alwaysRejected() {
        Ticket ticket = existingTicket(TicketStatus.APPROVED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.deploy(1L, "PROD", "tl-1"))
                .isInstanceOf(BusinessException.class);
        verify(pipelineServiceClient, never()).triggerPipeline(any());
    }

    @Test
    void deploy_devEnv_fromApproved_succeeds() {
        Ticket ticket = existingTicket(TicketStatus.APPROVED);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pipelineServiceClient.triggerPipeline(any()))
                .thenReturn(ApiResponse.success(PipelineExecutionDTO.builder().build(), "ok"));

        TicketResponseDTO result = ticketService.deploy(1L, "dev", "tl-1");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.DEPLOYING_DEV);
    }

    @Test
    void updatePipelineStatus_success_movesToDeployedDev() {
        Ticket ticket = existingTicket(TicketStatus.DEPLOYING_DEV);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        PipelineStatusUpdateDTO dto = PipelineStatusUpdateDTO.builder()
                .pipelineExecutionId(42L).environment("DEV").status("SUCCESS").build();

        TicketResponseDTO result = ticketService.updatePipelineStatus(1L, dto, "pipeline-service");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.DEPLOYED_DEV);
        verify(eventPublisher).publishStatusChanged(any(Ticket.class), eq(TicketStatus.DEPLOYING_DEV), eq("pipeline-service"));
    }

    @Test
    void updatePipelineStatus_failure_movesToFailed() {
        Ticket ticket = existingTicket(TicketStatus.DEPLOYING_PROD);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        PipelineStatusUpdateDTO dto = PipelineStatusUpdateDTO.builder()
                .pipelineExecutionId(42L).environment("PROD").status("FAILED").build();

        TicketResponseDTO result = ticketService.updatePipelineStatus(1L, dto, "pipeline-service");

        assertThat(result.getStatus()).isEqualTo(TicketStatus.FAILED);
    }

    @Test
    void updatePipelineStatus_ticketNotDeploying_throwsBusinessException() {
        Ticket ticket = existingTicket(TicketStatus.DRAFT);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        PipelineStatusUpdateDTO dto = PipelineStatusUpdateDTO.builder()
                .pipelineExecutionId(42L).environment("DEV").status("SUCCESS").build();

        assertThatThrownBy(() -> ticketService.updatePipelineStatus(1L, dto, "pipeline-service"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addComment_persistsAndReturnsDTO() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(existingTicket(TicketStatus.DRAFT)));
        when(ticketCommentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ticketService.addComment(1L, TicketCommentCreateDTO.builder().content("LGTM").build(), "dev-1");

        assertThat(result.getContent()).isEqualTo("LGTM");
        assertThat(result.getAuthorUserId()).isEqualTo("dev-1");
    }

    @Test
    void getComments_returnsCommentsForTicket() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(existingTicket(TicketStatus.DRAFT)));
        when(ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(
                TicketComment.builder().id(1L).ticketId(1L).authorUserId("dev-1").content("LGTM").build()));

        var result = ticketService.getComments(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("LGTM");
    }

    @Test
    void getComments_ticketNotFound_throws() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getComments(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void isOwner_delegatesToRepository() {
        when(ticketRepository.existsByIdAndCreatedByUserId(1L, "dev-1")).thenReturn(true);

        assertThat(ticketService.isOwner(1L, "dev-1")).isTrue();
    }

    private Ticket existingTicket(TicketStatus status) {
        return Ticket.builder()
                .id(1L)
                .title("Add feature")
                .status(status)
                .priority(TicketPriority.MEDIUM)
                .createdByUserId("dev-1")
                .build();
    }

    private void authenticateAs(String userId, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(
                        Arrays.stream(roles).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new))));
    }
}
