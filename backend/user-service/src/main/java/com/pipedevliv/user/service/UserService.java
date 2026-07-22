package com.pipedevliv.user.service;

import com.pipedevliv.common.dto.PageResponse;
import com.pipedevliv.user.dto.UserDTO;
import com.pipedevliv.user.dto.UserSyncDTO;
import org.springframework.data.domain.Pageable;

public interface UserService {

    PageResponse<UserDTO> getAllUsers(Pageable pageable);

    UserDTO getUserById(Long id);

    UserDTO getUserByKeycloakId(String keycloakId);

    UserDTO syncCurrentUser(UserSyncDTO syncDTO);
}
