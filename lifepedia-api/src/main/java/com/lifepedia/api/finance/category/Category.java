package com.lifepedia.api.finance.category;

import com.lifepedia.api.finance.EntryType;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Categoria do usuário, com a cor usada nos gráficos. */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryType type;

    /** Hex minúsculo, ex.: {@code #2a78d6}. */
    @Column(nullable = false, length = 7)
    private String color;

    protected Category() {
    }

    public Category(UUID userId, String name, EntryType type, String color) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.color = color;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EntryType getType() {
        return type;
    }

    public void setType(EntryType type) {
        this.type = type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
