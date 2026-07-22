package com.pipedevliv.user.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.common.exception.ResourceNotFoundException;
import com.pipedevliv.user.dto.UserDTO;
import com.pipedevliv.user.dto.UserSyncDTO;
import com.pipedevliv.user.entity.UserProfile;
import com.pipedevliv.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserProfileRepository userProfileRepository;

    @Override
    public PageResponse<UserDTO> getAllUsers(Pageable pageable) {
        return PageResponse.from(userProfileRepository.findAll(pageable).map(this::toDTO));
    }

    @Override
    public UserDTO getUserById(Long id) {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "id", id));
        return toDTO(profile);
    }

    @Override
    public UserDTO getUserByKeycloakId(String keycloakId) {
        UserProfile profile = userProfileRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", "keycloakId", keycloakId));
        return toDTO(profile);
    }

    @Override
    @Transactional
    public UserDTO syncCurrentUser(UserSyncDTO syncDTO) {
        UserProfile profile = userProfileRepository.findByKeycloakId(syncDTO.getKeycloakId())
                .map(existing -> {
                    existing.setEmail(syncDTO.getEmail());
                    existing.setFullName(syncDTO.getFullName());
                    existing.setRoles(syncDTO.getRoles());
                    return existing;
                })
                .orElseGet(() -> UserProfile.builder()
                        .keycloakId(syncDTO.getKeycloakId())
                        .email(syncDTO.getEmail())
                        .fullName(syncDTO.getFullName())
                        .roles(syncDTO.getRoles())
                        .build());

        return toDTO(userProfileRepository.save(profile));
    }

    private UserDTO toDTO(UserProfile profile) {
        return UserDTO.builder()
                .id(profile.getId())
                .keycloakId(profile.getKeycloakId())
                .email(profile.getEmail())
                .fullName(profile.getFullName())
                .roles(profile.getRoles())
                .createdAt(profile.getCreatedAt())
                .build();
    }
}
