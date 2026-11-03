package com.example.qimo.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public final class Numbers {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicInteger SEQUENCE = new AtomicInteger(100);

    private Numbers() {
    }

    public static String declarationNo() {
        return "SB" + LocalDateTime.now().format(STAMP) + next();
    }

    public static String paymentNo() {
        return "JK" + LocalDateTime.now().format(STAMP) + next();
    }

    public static String invoiceNo() {
        return "FP" + LocalDateTime.now().format(STAMP) + next();
    }

    public static String username(String prefix) {
        return prefix + LocalDateTime.now().format(STAMP);
    }

    private static String next() {
        int value = SEQUENCE.updateAndGet(current -> current >= 999 ? 100 : current + 1);
        return String.valueOf(value);
    }
}
