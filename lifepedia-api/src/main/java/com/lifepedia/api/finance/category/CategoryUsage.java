package com.lifepedia.api.finance.category;

import java.util.UUID;

/** Lançamentos por categoria. */
public interface CategoryUsage {

    UUID getCategoryId();

    long getTotal();
}
