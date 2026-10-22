package com.lifepedia.api.user;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    /** Minúsculo e único; nulo em contas antigas até completarem o perfil. */
    @Column(unique = true, length = 30)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Arquivo da foto no storage; nulo = sem foto. */
    @Column(name = "image_key", length = 64)
    private String imageKey;

    protected User() {
    }

    public User(String name, String username, String email, String passwordHash) {
        this.name = name;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /** Cadastro com todos os campos obrigatórios. */
    public boolean isProfileComplete() {
        return username != null;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    /** URL da foto com versão (cache); nula sem foto. */
    public String imageUrl() {
        return imageKey == null ? null : "/api/users/" + getId() + "/image?v=" + imageKey;
    }
}
