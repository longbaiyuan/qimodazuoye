package com.example.qimo.service;

import com.example.qimo.config.Database;
import com.example.qimo.model.OperationLog;
import com.example.qimo.model.PageResult;
import com.example.qimo.util.SqlUtil;
import com.example.qimo.util.Times;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class LogService {

    private LogService() {
    }

    public static void log(String username, String module, String action, String detail) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO operation_log(username,module,action,detail,created_at) VALUES(?,?,?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, module);
            ps.setString(3, action);
            ps.setString(4, detail);
            ps.setString(5, Times.now());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public static PageResult<OperationLog> list(String keyword, String module, Integer page, Integer size) {
        int safePage = SqlUtil.page(page);
        int safeSize = SqlUtil.size(size);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (SqlUtil.hasText(keyword)) {
            where.append(" AND (username LIKE ? OR action LIKE ? OR detail LIKE ?)");
            params.add(SqlUtil.like(keyword));
            params.add(SqlUtil.like(keyword));
            params.add(SqlUtil.like(keyword));
        }
        if (SqlUtil.hasText(module)) {
            where.append(" AND module = ?");
            params.add(module);
        }
        String base = "SELECT id,username,module,action,detail,created_at FROM operation_log";
        long total = count(base + where, params);
        List<OperationLog> items = new ArrayList<>();
        String sql = base + where + " ORDER BY id DESC LIMIT ? OFFSET ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(safeSize);
            queryParams.add(SqlUtil.offset(safePage, safeSize));
            SqlUtil.bind(ps, queryParams);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OperationLog log = new OperationLog();
                    log.setId(rs.getLong("id"));
                    log.setUsername(rs.getString("username"));
                    log.setModule(rs.getString("module"));
                    log.setAction(rs.getString("action"));
                    log.setDetail(rs.getString("detail"));
                    log.setCreatedAt(rs.getString("created_at"));
                    items.add(log);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询操作日志失败", e);
        }
        return new PageResult<>(items, total, safePage, safeSize);
    }

    private static long count(String sql, List<Object> params) {
        String countSql = "SELECT COUNT(*) FROM (" + sql + ")";
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(countSql)) {
            SqlUtil.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计操作日志失败", e);
        }
    }
}
