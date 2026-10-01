package com.lifepedia.api.finance.category;

import com.lifepedia.api.finance.EntryType;

import java.util.List;

/** Categorias de todo usuário novo (a migration V3 tem a mesma lista). */
public final class DefaultCategories {

    public record DefaultCategory(String name, EntryType type, String color) {
    }

    public static final List<DefaultCategory> ALL = List.of(
            new DefaultCategory("Moradia", EntryType.EXPENSE, "#2a78d6"),
            new DefaultCategory("Contas da casa", EntryType.EXPENSE, "#1baf7a"),
            new DefaultCategory("Alimentação", EntryType.EXPENSE, "#eb6834"),
            new DefaultCategory("Transporte", EntryType.EXPENSE, "#eda100"),
            new DefaultCategory("Saúde", EntryType.EXPENSE, "#e34948"),
            new DefaultCategory("Educação", EntryType.EXPENSE, "#4a3aa7"),
            new DefaultCategory("Lazer", EntryType.EXPENSE, "#e87ba4"),
            new DefaultCategory("Assinaturas", EntryType.EXPENSE, "#9b51c9"),
            new DefaultCategory("Compras", EntryType.EXPENSE, "#0e9aa7"),
            new DefaultCategory("Impostos e taxas", EntryType.EXPENSE, "#9c6b3c"),
            new DefaultCategory("Dívidas e parcelas", EntryType.EXPENSE, "#a61e4d"),
            new DefaultCategory("Presentes", EntryType.EXPENSE, "#008300"),
            new DefaultCategory("Outros", EntryType.EXPENSE, "#64748b"),
            new DefaultCategory("Salário", EntryType.INCOME, "#2a78d6"),
            new DefaultCategory("Freelance / extra", EntryType.INCOME, "#1baf7a"),
            new DefaultCategory("Investimentos", EntryType.INCOME, "#008300"),
            new DefaultCategory("Benefícios", EntryType.INCOME, "#eda100"),
            new DefaultCategory("Presentes", EntryType.INCOME, "#e87ba4"),
            new DefaultCategory("Outros", EntryType.INCOME, "#64748b"));

    private DefaultCategories() {
    }
}
