package com.mali.smartbudget.exception;

import java.time.LocalDate;

/**
 * Mükerrer ekstre yükleme girişiminde fırlatılır.
 *
 * <p>İki farklı durumda ortaya çıkabilir:
 * <ul>
 *   <li><b>Hash mükerrer</b> — Aynı PDF dosyası (byte byte özdeş) daha önce yüklendi.</li>
 *   <li><b>Dönem mükerrer</b> — Farklı bir dosya olsa bile kapsadığı tarih aralığı
 *       daha önce kaydedilmiş bir ekstre ile örtüşüyor.</li>
 * </ul>
 *
 * <p>{@link GlobalExceptionHandler} bu exception'ı HTTP 409 Conflict'e çevirir.
 */
public class DuplicateStatementException extends RuntimeException {

    /** Mükerrerlik tipi — frontend'in mesajı özelleştirmesi için. */
    public enum Type { HASH, PERIOD, CARD_PERIOD }

    private final Type duplicateType;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;

    public DuplicateStatementException(String message, Type type,
                                       LocalDate periodStart, LocalDate periodEnd) {
        super(message);
        this.duplicateType = type;
        this.periodStart   = periodStart;
        this.periodEnd     = periodEnd;
    }

    public Type getDuplicateType() { return duplicateType; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd()   { return periodEnd;   }
}
