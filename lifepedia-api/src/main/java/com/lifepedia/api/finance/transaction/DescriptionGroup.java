package com.lifepedia.api.finance.transaction;

import java.math.BigDecimal;

/** Soma de ocasionais com a mesma descrição. */
public interface DescriptionGroup {

    String getDescription();

    long getCount();

    BigDecimal getTotal();
}
