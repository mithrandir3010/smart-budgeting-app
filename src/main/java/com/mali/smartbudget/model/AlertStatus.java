package com.mali.smartbudget.model;

/**
 * Bütçe uyarı seviyeleri.
 *
 * CRITICAL → Harcama limitin ≥ %90'ına ulaşmış veya aşmış
 * WARNING  → Harcama limitin %70–89 aralığında
 * OK       → Harcama limitin %70'inin altında
 */
public enum AlertStatus {
    CRITICAL,
    WARNING,
    OK
}
