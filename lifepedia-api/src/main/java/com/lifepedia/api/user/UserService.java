package com.lifepedia.api.user;

import com.lifepedia.api.shared.BusinessException;
import com.lifepedia.api.shared.NotFoundException;
import com.lifepedia.api.shared.image.ImageFile;
import com.lifepedia.api.user.UserDtos.UpdateProfileRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository repository;
    private final UserImageStore images;

    public UserService(UserRepository repository, UserImageStore images) {
        this.repository = repository;
        this.images = images;
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

    /** Aceite dos Termos e da Política (conta criada pelo Google ou versão nova). */
    @Transactional
    public UserResponse acceptTerms(UUID id, String version) {
        if (!LegalTerms.VERSION.equals(version)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Os termos foram atualizados. Recarregue a página e leia a versão nova.");
        }
        User user = find(id);
        user.acceptTerms(version);
        return UserResponse.from(user);
    }

    /** Troca a foto; a anterior sai do disco depois do commit. */
    @Transactional
    public UserResponse setImage(UUID id, byte[] bytes) {
        User user = find(id);
        String previous = user.getImageKey();
        user.setImageKey(images.save(bytes));
        images.deleteAfterCommit(previous);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse removeImage(UUID id) {
        User user = find(id);
        images.deleteAfterCommit(user.getImageKey());
        user.setImageKey(null);
        return UserResponse.from(user);
    }

    /** Foto de qualquer usuário para quem está logado. */
    @Transactional(readOnly = true)
    public ImageFile image(UUID id) {
        User user = find(id);
        if (user.getImageKey() == null) throw new NotFoundException("O usuário não tem foto.");
        return images.file(user.getImageKey());
    }

    /** Apaga a foto do disco quando a conta for excluída (depois do commit). */
    public void deleteImageAfterCommit(User user) {
        images.deleteAfterCommit(user.getImageKey());
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
