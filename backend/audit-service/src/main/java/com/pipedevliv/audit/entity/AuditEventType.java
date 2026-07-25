package com.pipedevliv.audit.entity;

public enum AuditEventType {
    USER_SYNCED,
    TICKET_CREATED,
    TICKET_STATUS_CHANGED,
    TICKET_APPROVED,
    PIPELINE_STARTED,
    PIPELINE_COMPLETED,
    PIPELINE_FAILED
}
