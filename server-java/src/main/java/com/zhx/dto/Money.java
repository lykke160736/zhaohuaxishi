package com.zhx.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {
    }

    public static int cents(Object value) {
        if (value == null) return 0;
        BigDecimal yuan;
        if (value instanceof BigDecimal d) yuan = d;
        else yuan = new BigDecimal(String.valueOf(value));
        return yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    public static BigDecimal yuan(int cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    public static BigDecimal yuan(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
