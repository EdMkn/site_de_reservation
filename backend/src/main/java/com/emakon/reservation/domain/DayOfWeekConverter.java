package com.emakon.reservation.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

/**
 * Convertit DayOfWeek <-> SMALLINT (colonne plage_horaire.jour_semaine).
 * La base et java.time suivent la même norme ISO 8601 : 1 = lundi ... 7 = dimanche,
 * donc getValue() / of() suffisent. Sans ce convertisseur, JPA stockerait le nom
 * ("MONDAY") ou la position dans l'enum (0 = lundi), incompatibles avec le CHECK 1..7.
 */
@Converter
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek day) {
        return day == null ? null : (short) day.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short value) {
        return value == null ? null : DayOfWeek.of(value);
    }
}
