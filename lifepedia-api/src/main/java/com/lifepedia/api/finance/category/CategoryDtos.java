package com.lifepedia.api.finance.category;

import com.lifepedia.api.finance.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 60, message = "Máximo de 60 caracteres.") String name,
            @NotNull(message = "Informe o tipo.") EntryType type,
            @NotBlank(message = "Informe a cor.")
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor inválida (use o formato #RRGGBB).") String color) {
    }

    /** @param usageCount lançamentos (fixos + ocasionais) na categoria */
    public record CategoryResponse(UUID id, String name, EntryType type, String color, long usageCount) {

        public static CategoryResponse from(Category c, long usageCount) {
            return new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getColor(), usageCount);
        }
    }
}
