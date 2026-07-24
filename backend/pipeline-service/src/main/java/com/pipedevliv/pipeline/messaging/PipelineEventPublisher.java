package com.pipedevliv.pipeline.messaging;

import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.pipeline.entity.PipelineExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PipelineEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishStarted(PipelineExecution execution) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.PIPELINE_STARTED, toEvent(execution));
    }

    public void publishCompleted(PipelineExecution execution) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.PIPELINE_COMPLETED, toEvent(execution));
    }

    public void publishFailed(PipelineExecution execution) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE, RabbitMQConstants.PIPELINE_FAILED, toEvent(execution));
    }

    private PipelineEvent toEvent(PipelineExecution execution) {
        return PipelineEvent.builder()
                .executionId(execution.getId())
                .ticketId(execution.getTicketId())
                .environment(execution.getEnvironment())
                .status(execution.getStatus())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
