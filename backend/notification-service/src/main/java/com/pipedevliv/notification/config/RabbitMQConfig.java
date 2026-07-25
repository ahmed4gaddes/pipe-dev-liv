package com.pipedevliv.notification.config;

import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.notification.dto.PipelineEventPayload;
import com.pipedevliv.notification.dto.TicketCreatedPayload;
import com.pipedevliv.notification.dto.TicketEventPayload;
import com.pipedevliv.notification.dto.UserSyncedPayload;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Contrairement aux autres services, notification-service ne fait que consommer (voir
 * NotificationListener) — il déclare donc en plus sa propre queue et ses bindings sur
 * l'exchange partagé, plus un {@link DefaultJackson2JavaTypeMapper} qui fait correspondre
 * le FQCN du producteur (stocké dans le header {@code __TypeId__} par Jackson2JsonMessageConverter)
 * à un DTO miroir local — notification-service n'a pas les classes de ticket-service /
 * pipeline-service / user-service sur son classpath. Voir explication_phase_6.md.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(RabbitMQConstants.EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(RabbitMQConstants.NOTIFICATION_QUEUE, true);
    }

    @Bean
    public Binding ticketCreatedBinding(Queue notificationQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventsExchange).with(RabbitMQConstants.TICKET_CREATED);
    }

    @Bean
    public Binding ticketStatusChangedBinding(Queue notificationQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventsExchange).with(RabbitMQConstants.TICKET_STATUS_CHANGED);
    }

    @Bean
    public Binding ticketApprovedBinding(Queue notificationQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventsExchange).with(RabbitMQConstants.TICKET_APPROVED);
    }

    @Bean
    public Binding pipelineStartedBinding(Queue notificationQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventsExchange).with(RabbitMQConstants.PIPELINE_STARTED);
    }

    @Bean
    public Binding pipelineCompletedBinding(Queue notificationQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventsExchange).with(RabbitMQConstants.PIPELINE_COMPLETED);
    }

    @Bean
    public Binding pipelineFailedBinding(Queue notificationQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventsExchange).with(RabbitMQConstants.PIPELINE_FAILED);
    }

    @Bean
    public Binding userSyncedBinding(Queue notificationQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationQueue).to(eventsExchange).with(RabbitMQConstants.USER_SYNCED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setIdClassMapping(Map.of(
                "com.pipedevliv.ticket.dto.TicketResponseDTO", TicketCreatedPayload.class,
                "com.pipedevliv.ticket.messaging.TicketEvent", TicketEventPayload.class,
                "com.pipedevliv.pipeline.messaging.PipelineEvent", PipelineEventPayload.class,
                "com.pipedevliv.user.dto.UserDTO", UserSyncedPayload.class
        ));

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}
