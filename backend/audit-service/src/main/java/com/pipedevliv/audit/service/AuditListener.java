package com.pipedevliv.audit.service;

import com.pipedevliv.common.event.RabbitMQConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditListener {

    private final AuditService auditService;

    @RabbitListener(queues = RabbitMQConstants.AUDIT_QUEUE)
    public void onEvent(@Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey, Object payload) {
        auditService.handleEvent(routingKey, payload);
    }
}
