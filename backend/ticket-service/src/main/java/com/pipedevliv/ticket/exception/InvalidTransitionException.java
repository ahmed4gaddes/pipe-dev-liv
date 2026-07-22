package com.pipedevliv.ticket.exception;

import com.pipedevliv.common.exception.BusinessException;
import com.pipedevliv.ticket.entity.TicketStatus;

public class InvalidTransitionException extends BusinessException {

    public InvalidTransitionException(TicketStatus from, TicketStatus to) {
        super("Transition invalide : " + from + " -> " + to);
    }
}
