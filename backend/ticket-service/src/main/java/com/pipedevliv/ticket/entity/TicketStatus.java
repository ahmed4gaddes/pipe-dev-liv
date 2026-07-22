package com.pipedevliv.ticket.entity;

public enum TicketStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    DEPLOYING_DEV,
    DEPLOYED_DEV,
    DEPLOYING_TEST,
    DEPLOYED_TEST,
    PENDING_PROD_APPROVAL,
    DEPLOYING_PROD,
    DEPLOYED_PROD,
    FAILED,
    CLOSED
}
