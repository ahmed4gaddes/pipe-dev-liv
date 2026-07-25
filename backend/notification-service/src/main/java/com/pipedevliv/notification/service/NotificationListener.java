package com.pipedevliv.notification.service;

import com.pipedevliv.common.event.RabbitMQConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConstants.NOTIFICATION_QUEUE)
    public void onEvent(@Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey, Object payload) {
        notificationService.handleEvent(routingKey, payload);
    }
}
