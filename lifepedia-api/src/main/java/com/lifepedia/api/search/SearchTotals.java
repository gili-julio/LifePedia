package com.lifepedia.api.search;

import java.math.BigDecimal;

/** Quantos itens a busca acha e a soma de entradas e saídas. */
public record SearchTotals(long count, BigDecimal income, BigDecimal expense) {
}
