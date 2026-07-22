package com.pipedevliv.ticket.service;

import com.pipedevliv.ticket.entity.TicketStatus;
import com.pipedevliv.ticket.exception.InvalidTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.pipedevliv.ticket.entity.TicketStatus.APPROVED;
import static com.pipedevliv.ticket.entity.TicketStatus.CANCELLED;
import static com.pipedevliv.ticket.entity.TicketStatus.DEPLOYED_DEV;
import static com.pipedevliv.ticket.entity.TicketStatus.DEPLOYED_PROD;
import static com.pipedevliv.ticket.entity.TicketStatus.DEPLOYED_TEST;
import static com.pipedevliv.ticket.entity.TicketStatus.DEPLOYING_DEV;
import static com.pipedevliv.ticket.entity.TicketStatus.DEPLOYING_PROD;
import static com.pipedevliv.ticket.entity.TicketStatus.DEPLOYING_TEST;
import static com.pipedevliv.ticket.entity.TicketStatus.DRAFT;
import static com.pipedevliv.ticket.entity.TicketStatus.FAILED;
import static com.pipedevliv.ticket.entity.TicketStatus.PENDING_PROD_APPROVAL;
import static com.pipedevliv.ticket.entity.TicketStatus.REJECTED;
import static com.pipedevliv.ticket.entity.TicketStatus.SUBMITTED;
import static java.util.Map.entry;

/**
 * Valide uniquement si une transition FROM -> TO est structurellement légale. Ne connaît
 * ni rôle ni utilisateur : ce contrôle est fait séparément (@PreAuthorize + règles métier
 * dans TicketServiceImpl), pour séparer "la transition est-elle possible" de "cet
 * utilisateur a-t-il le droit de la déclencher".
 */
@Component
public class TicketStateMachine {

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = Map.ofEntries(
            entry(DRAFT, Set.of(SUBMITTED, CANCELLED)),
            entry(SUBMITTED, Set.of(APPROVED, REJECTED, CANCELLED)),
            entry(APPROVED, Set.of(DEPLOYING_DEV)),
            entry(DEPLOYING_DEV, Set.of(DEPLOYED_DEV, FAILED)),
            entry(DEPLOYED_DEV, Set.of(DEPLOYING_TEST)),
            entry(DEPLOYING_TEST, Set.of(DEPLOYED_TEST, FAILED)),
            entry(DEPLOYED_TEST, Set.of(PENDING_PROD_APPROVAL)),
            entry(PENDING_PROD_APPROVAL, Set.of(DEPLOYING_PROD, REJECTED)),
            entry(DEPLOYING_PROD, Set.of(DEPLOYED_PROD, FAILED)),
            entry(DEPLOYED_PROD, Set.of(TicketStatus.CLOSED)),
            entry(FAILED, Set.of(SUBMITTED)),
            entry(REJECTED, Set.of(DRAFT))
            // CANCELLED et CLOSED sont absents => terminaux (aucune transition sortante)
    );

    public void validateTransition(TicketStatus from, TicketStatus to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidTransitionException(from, to);
        }
    }
}
