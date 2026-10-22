package com.lifepedia.api.user;

import com.lifepedia.api.shared.CurrentUser;
import com.lifepedia.api.shared.image.Images;
import com.lifepedia.api.user.UserDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(service.find(CurrentUser.id(jwt)));
    }

    /** Nome e @username (obrigatório; completa o cadastro de contas antigas). */
    @PutMapping("/me")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(CurrentUser.id(jwt), request);
    }

    /** Multipart {@code file}: JPEG/PNG até 1 MB (o front envia já cortada em 512×512). */
    @PutMapping(value = "/me/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse setImage(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        return service.setImage(CurrentUser.id(jwt), Images.uploadBytes(file));
    }

    @DeleteMapping("/me/image")
    public UserResponse removeImage(@AuthenticationPrincipal Jwt jwt) {
        return service.removeImage(CurrentUser.id(jwt));
    }

    /** Qualquer usuário logado. A URL leva {@code ?v=} (versão), então pode ficar em cache. */
    @GetMapping("/{userId}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID userId) {
        return service.image(userId).response();
    }
}
