package com.example.qimo.util;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class SqlUtil {

    private SqlUtil() {
    }

    public static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
    }

    public static String like(String keyword) {
        return "%" + (keyword == null ? "" : keyword.trim()) + "%";
    }

    public static int page(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    public static int size(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 200);
    }

    public static int offset(int page, int size) {
        return (page - 1) * size;
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
