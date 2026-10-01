package com.lifepedia.api.finance;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.time.YearMonth;

/** Persiste {@link YearMonth} como DATE no primeiro dia do mês. */
@Converter
public class YearMonthConverter implements AttributeConverter<YearMonth, LocalDate> {

    @Override
    public LocalDate convertToDatabaseColumn(YearMonth month) {
        return month == null ? null : month.atDay(1);
    }

    @Override
    public YearMonth convertToEntityAttribute(LocalDate date) {
        return date == null ? null : YearMonth.from(date);
    }
}
