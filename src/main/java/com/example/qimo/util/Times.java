package com.example.qimo.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class Times {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private Times() {
    }

    public static String now() {
        return LocalDateTime.now().format(DATE_TIME);
    }

    public static String today() {
        return LocalDate.now().format(DATE);
    }

    public static String currentMonth() {
        return YearMonth.now().format(MONTH);
    }

    public static String monthOf(String date) {
        if (date == null || date.length() < 7) {
            return "";
        }
        return date.substring(0, 7);
    }

    public static String lastMonths(int count) {
        StringBuilder builder = new StringBuilder();
        YearMonth current = YearMonth.now();
        for (int i = count - 1; i >= 0; i--) {
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append("'").append(current.minusMonths(i).format(MONTH)).append("'");
        }
        return builder.toString();
    }
}
