package com.lifepedia.api.financing;

import com.lifepedia.api.finance.YearMonthConverter;
import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Variação de um índice de correção no mês (%). */
@Entity
@Table(name = "financing_index_values")
public class IndexValue extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "index_name", nullable = false, length = 10)
    private CorrectionIndex index;

    @Convert(converter = YearMonthConverter.class)
    @Column(nullable = false)
    private YearMonth month;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal rate;

    protected IndexValue() {
    }

    public IndexValue(CorrectionIndex index, YearMonth month, BigDecimal rate) {
        this.index = index;
        this.month = month;
        this.rate = rate;
    }

    public CorrectionIndex getIndex() {
        return index;
    }

    public YearMonth getMonth() {
        return month;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }
}
