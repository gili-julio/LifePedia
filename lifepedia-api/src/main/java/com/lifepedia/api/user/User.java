package com.lifepedia.api.user;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

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

    /** Nulo em conta criada pelo Google (entra só pelo Google). */
    @Column(name = "password_hash")
    private String passwordHash;

    /** {@code sub} da conta Google vinculada; nulo = sem vínculo. */
    @Column(name = "google_sub", unique = true)
    private String googleSub;

    /** Arquivo da foto no storage; nulo = sem foto. */
    @Column(name = "image_key", length = 64)
    private String imageKey;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private OffsetDateTime termsAcceptedAt;

    protected User() {
    }

    public User(String name, String username, String email, String passwordHash) {
        this.name = name;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    /** Conta criada pelo Google: sem senha e sem @username (completa no perfil). */
    public static User fromGoogle(String name, String email, String googleSub) {
        User user = new User(name, null, email, null);
        user.googleSub = googleSub;
        return user;
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

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public void setGoogleSub(String googleSub) {
        this.googleSub = googleSub;
    }

    public String getImageKey() {
        return imageKey;
    }

    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    /** Registra o aceite dos Termos e da Política na versão informada. */
    public void acceptTerms(String version) {
        termsVersion = version;
        termsAcceptedAt = OffsetDateTime.now();
    }

    public boolean hasAcceptedTerms() {
        return LegalTerms.VERSION.equals(termsVersion);
    }

    public OffsetDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    /** URL da foto com versão (cache); nula sem foto. */
    public String imageUrl() {
        return imageKey == null ? null : "/api/users/" + getId() + "/image?v=" + imageKey;
    }
}
