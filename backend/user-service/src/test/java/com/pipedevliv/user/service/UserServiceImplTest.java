package com.pipedevliv.user.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.event.RabbitMQConstants;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.user.dto.UserDTO;
import com.pipedevliv.user.dto.UserSyncDTO;
import com.pipedevliv.user.entity.UserProfile;
import com.pipedevliv.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userProfileRepository, rabbitTemplate);
    }

    @Test
    void getAllUsers_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userProfileRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(existingProfile()), pageable, 1));

        PageResponse<UserDTO> result = userService.getAllUsers(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getKeycloakId()).isEqualTo("kc-1");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getUserById_found_returnsDto() {
        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(existingProfile()));

        UserDTO result = userService.getUserById(1L);

        assertThat(result.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void getUserById_notFound_throws() {
        when(userProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getUserByKeycloakId_notFound_throws() {
        when(userProfileRepository.findByKeycloakId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByKeycloakId("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void syncCurrentUser_whenNewKeycloakId_createsProfileAndPublishesEvent() {
        UserSyncDTO syncDTO = UserSyncDTO.builder()
                .keycloakId("kc-2").email("new@example.com").fullName("New User").roles("ROLE_DEVELOPER")
                .build();
        when(userProfileRepository.findByKeycloakId("kc-2")).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(0);
            profile.setId(10L);
            return profile;
        });

        UserDTO result = userService.syncCurrentUser(syncDTO);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getEmail()).isEqualTo("new@example.com");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getKeycloakId()).isEqualTo("kc-2");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConstants.EXCHANGE), eq(RabbitMQConstants.USER_SYNCED), any(UserDTO.class));
    }

    @Test
    void syncCurrentUser_whenExistingKeycloakId_updatesProfile() {
        UserProfile existing = existingProfile();
        UserSyncDTO syncDTO = UserSyncDTO.builder()
                .keycloakId("kc-1").email("updated@example.com").fullName("John Updated").roles("ROLE_ADMIN")
                .build();
        when(userProfileRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserDTO result = userService.syncCurrentUser(syncDTO);

        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(result.getEmail()).isEqualTo("updated@example.com");
        assertThat(result.getRoles()).isEqualTo("ROLE_ADMIN");
    }

    private UserProfile existingProfile() {
        return UserProfile.builder()
                .id(1L).keycloakId("kc-1").email("john@example.com").fullName("John Doe").roles("ROLE_DEVELOPER")
                .build();
    }
}
