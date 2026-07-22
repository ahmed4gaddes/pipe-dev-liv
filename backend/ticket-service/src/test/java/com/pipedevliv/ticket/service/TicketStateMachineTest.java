package com.pipedevliv.ticket.service;

import com.pipedevliv.ticket.entity.TicketStatus;
import com.pipedevliv.ticket.exception.InvalidTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.pipedevliv.ticket.entity.TicketStatus.CANCELLED;
import static com.pipedevliv.ticket.entity.TicketStatus.CLOSED;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketStateMachineTest {

    private final TicketStateMachine stateMachine = new TicketStateMachine();

    @ParameterizedTest
    @CsvSource({
            "DRAFT, SUBMITTED",
            "DRAFT, CANCELLED",
            "SUBMITTED, APPROVED",
            "SUBMITTED, REJECTED",
            "SUBMITTED, CANCELLED",
            "APPROVED, DEPLOYING_DEV",
            "DEPLOYING_DEV, DEPLOYED_DEV",
            "DEPLOYING_DEV, FAILED",
            "DEPLOYED_DEV, DEPLOYING_TEST",
            "DEPLOYING_TEST, DEPLOYED_TEST",
            "DEPLOYING_TEST, FAILED",
            "DEPLOYED_TEST, PENDING_PROD_APPROVAL",
            "PENDING_PROD_APPROVAL, DEPLOYING_PROD",
            "PENDING_PROD_APPROVAL, REJECTED",
            "DEPLOYING_PROD, DEPLOYED_PROD",
            "DEPLOYING_PROD, FAILED",
            "DEPLOYED_PROD, CLOSED",
            "FAILED, SUBMITTED",
            "REJECTED, DRAFT"
    })
    void validTransition_doesNotThrow(TicketStatus from, TicketStatus to) {
        assertThatCode(() -> stateMachine.validateTransition(from, to)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, APPROVED",
            "DRAFT, DEPLOYING_DEV",
            "SUBMITTED, DEPLOYING_DEV",
            "APPROVED, DEPLOYED_DEV",
            "DEPLOYED_DEV, DEPLOYED_TEST",
            "REJECTED, SUBMITTED"
    })
    void invalidTransition_throws(TicketStatus from, TicketStatus to) {
        assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void terminalStatuses_rejectAllOutgoingTransitions() {
        for (TicketStatus target : TicketStatus.values()) {
            assertThatThrownBy(() -> stateMachine.validateTransition(CANCELLED, target))
                    .isInstanceOf(InvalidTransitionException.class);
            assertThatThrownBy(() -> stateMachine.validateTransition(CLOSED, target))
                    .isInstanceOf(InvalidTransitionException.class);
        }
    }
}
