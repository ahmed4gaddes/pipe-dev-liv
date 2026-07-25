package com.pipedevliv.audit.service;

import com.pipedevliv.audit.dto.UserSyncedPayload;
import com.pipedevliv.common.event.RabbitMQConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditListenerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditListener listener;

    @Test
    void onEvent_delegatesRoutingKeyAndPayloadToService() {
        UserSyncedPayload payload = UserSyncedPayload.builder().keycloakId("kc-1").build();

        listener.onEvent(RabbitMQConstants.USER_SYNCED, payload);

        verify(auditService).handleEvent(RabbitMQConstants.USER_SYNCED, payload);
    }
}
