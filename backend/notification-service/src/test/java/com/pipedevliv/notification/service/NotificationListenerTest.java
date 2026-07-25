package com.pipedevliv.notification.service;

import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.notification.dto.UserSyncedPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationListener listener;

    @Test
    void onEvent_delegatesRoutingKeyAndPayloadToService() {
        UserSyncedPayload payload = UserSyncedPayload.builder().keycloakId("kc-1").build();

        listener.onEvent(RabbitMQConstants.USER_SYNCED, payload);

        verify(notificationService).handleEvent(RabbitMQConstants.USER_SYNCED, payload);
    }
}
