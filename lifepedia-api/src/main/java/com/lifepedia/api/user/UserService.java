package com.lifepedia.api.user;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.user.UserDtos.UpdateProfileRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public User find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        User user = find(id);
        String username = Usernames.require(request.username());
        requireAvailable(username, id);
        user.setName(request.name().trim());
        user.setUsername(username);
        return UserResponse.from(user);
    }

    /** 409 se outro usuário já usa o username. */
    public void requireAvailable(String username, UUID ignoreId) {
        repository.findByUsername(username)
                .filter(other -> !other.getId().equals(ignoreId))
                .ifPresent(other -> {
                    throw new BusinessException(HttpStatus.CONFLICT, "O nome de usuário @" + username + " já está em uso.");
                });
    }
}
