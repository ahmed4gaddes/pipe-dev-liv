package com.pipedevliv.common.event;

public final class RabbitMQConstants {
    private RabbitMQConstants() {
        // Utils class
    }

    public static final String EXCHANGE = "pipe-dev-liv.events";
    
    // Routing keys
    public static final String USER_SYNCED         = "user.synced";
    public static final String TICKET_CREATED      = "ticket.created";
    public static final String TICKET_STATUS_CHANGED = "ticket.status-changed";
    public static final String TICKET_APPROVED     = "ticket.approved";
    public static final String PIPELINE_STARTED    = "pipeline.started";
    public static final String PIPELINE_COMPLETED  = "pipeline.completed";
    public static final String PIPELINE_FAILED     = "pipeline.failed";
    
    // Queues
    public static final String NOTIFICATION_QUEUE  = "notification.queue";
    public static final String AUDIT_QUEUE         = "audit.queue";
    public static final String PIPELINE_QUEUE      = "pipeline.queue";
}
