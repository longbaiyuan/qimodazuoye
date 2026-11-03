package com.example.qimo.service;

import com.example.qimo.config.ApiException;
import com.example.qimo.config.Database;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.User;
import com.example.qimo.util.PasswordUtil;
import com.example.qimo.util.SqlUtil;
import com.example.qimo.util.Statuses;
import com.example.qimo.util.Times;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class UserService {

    private static final List<String> ROLES = Arrays.asList(
            Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR, Statuses.ROLE_TAXPAYER);

    private UserService() {
    }

    public static PageResult<User> list(String keyword, String role, Integer page, Integer size) {
        int safePage = SqlUtil.page(page);
        int safeSize = SqlUtil.size(size);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (SqlUtil.hasText(keyword)) {
            where.append(" AND (u.username LIKE ? OR u.display_name LIKE ?)");
            params.add(SqlUtil.like(keyword));
            params.add(SqlUtil.like(keyword));
        }
        if (SqlUtil.hasText(role)) {
            where.append(" AND u.user_role = ?");
            params.add(role);
        }
        String base = "SELECT u.id,u.username,u.password,u.display_name,u.user_role,u.taxpayer_id,u.status,u.created_at," +
                "t.name AS taxpayer_name FROM sys_user u LEFT JOIN taxpayer t ON t.id = u.taxpayer_id";
        long total = count(base + where, params);
        List<User> items = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(SqlUtil.offset(safePage, safeSize));
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     base + where + " ORDER BY u.id ASC LIMIT ? OFFSET ?")) {
            SqlUtil.bind(ps, queryParams);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(AuthService.mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询用户失败", e);
        }
        return new PageResult<>(items, total, safePage, safeSize);
    }

    public static User get(Long id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT u.id,u.username,u.password,u.display_name,u.user_role,u.taxpayer_id,u.status,u.created_at," +
                             "t.name AS taxpayer_name FROM sys_user u LEFT JOIN taxpayer t ON t.id = u.taxpayer_id WHERE u.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return AuthService.mapUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询用户失败", e);
        }
        throw ApiException.notFound("用户不存在");
    }

    public static User create(User input, User operator) {
        validate(input, true);
        ensureUsernameUnique(input.getUsername(), null);
        if (Statuses.ROLE_TAXPAYER.equals(input.getRole())) {
            TaxpayerService.get(input.getTaxpayerId());
            ensureTaxpayerBindingUnique(input.getTaxpayerId(), null);
        } else {
            input.setTaxpayerId(null);
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO sys_user(username,password,display_name,user_role,taxpayer_id,status,created_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, input.getUsername().trim());
            ps.setString(2, PasswordUtil.hash(input.getPassword()));
            ps.setString(3, input.getDisplayName().trim());
            ps.setString(4, input.getRole());
            if (input.getTaxpayerId() == null) {
                ps.setObject(5, null);
            } else {
                ps.setLong(5, input.getTaxpayerId());
            }
            ps.setString(6, input.getStatus());
            ps.setString(7, Times.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("新增用户失败", e);
        }
        LogService.log(operator.getUsername(), "用户", "新增", "新增用户：" + input.getUsername());
        return AuthService.findByUsername(input.getUsername().trim());
    }

    public static User update(Long id, User input, User operator) {
        User existing = get(id);
        validate(input, false);
        if (existing.getId().equals(operator.getId())) {
            if (!existing.getRole().equals(input.getRole())) {
                throw ApiException.conflict("不能修改自己的角色");
            }
            if (!Statuses.USER_ENABLED.equals(input.getStatus())) {
                throw ApiException.conflict("不能停用自己的账号");
            }
        }
        if (Statuses.ROLE_ADMIN.equals(existing.getRole())
                && !Statuses.ROLE_ADMIN.equals(input.getRole())
                && countAdmins() <= 1) {
            throw ApiException.conflict("系统至少需要保留一个管理员");
        }
        if (Statuses.ROLE_TAXPAYER.equals(input.getRole())) {
            if (input.getTaxpayerId() == null) {
                throw ApiException.badRequest("纳税人账号必须绑定纳税人");
            }
            TaxpayerService.get(input.getTaxpayerId());
            ensureTaxpayerBindingUnique(input.getTaxpayerId(), id);
        } else {
            input.setTaxpayerId(null);
        }
        boolean changePassword = SqlUtil.hasText(input.getPassword());
        if (changePassword && input.getPassword().trim().length() < 6) {
            throw ApiException.badRequest("密码长度不能少于6位");
        }
        StringBuilder sql = new StringBuilder(
                "UPDATE sys_user SET display_name=?,user_role=?,taxpayer_id=?,status=?");
        if (changePassword) {
            sql.append(",password=?");
        }
        sql.append(" WHERE id=?");
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int index = 1;
            ps.setString(index++, input.getDisplayName().trim());
            ps.setString(index++, input.getRole());
            if (input.getTaxpayerId() == null) {
                ps.setObject(index++, null);
            } else {
                ps.setLong(index++, input.getTaxpayerId());
            }
            ps.setString(index++, input.getStatus());
            if (changePassword) {
                ps.setString(index++, PasswordUtil.hash(input.getPassword().trim()));
            }
            ps.setLong(index, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("修改用户失败", e);
        }
        LogService.log(operator.getUsername(), "用户", "修改", "修改用户：" + existing.getUsername());
        return get(id);
    }

    public static void delete(Long id, User operator) {
        User existing = get(id);
        if (existing.getId().equals(operator.getId())) {
            throw ApiException.conflict("不能删除当前登录账号");
        }
        if (Statuses.ROLE_ADMIN.equals(existing.getRole()) && countAdmins() <= 1) {
            throw ApiException.conflict("系统至少需要保留一个管理员");
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM sys_user WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("删除用户失败", e);
        }
        LogService.log(operator.getUsername(), "用户", "删除", "删除用户：" + existing.getUsername());
    }

    private static void validate(User input, boolean requirePassword) {
        if (input == null) {
            throw ApiException.badRequest("用户信息不能为空");
        }
        if (requirePassword) {
            if (!SqlUtil.hasText(input.getUsername())) {
                throw ApiException.badRequest("用户名不能为空");
            }
            if (!SqlUtil.hasText(input.getPassword()) || input.getPassword().trim().length() < 6) {
                throw ApiException.badRequest("密码长度不能少于6位");
            }
        }
        if (!SqlUtil.hasText(input.getDisplayName())) {
            throw ApiException.badRequest("姓名不能为空");
        }
        if (!SqlUtil.hasText(input.getRole()) || !ROLES.contains(input.getRole())) {
            throw ApiException.badRequest("用户角色不正确");
        }
        if (!Statuses.USER_ENABLED.equals(input.getStatus()) && !Statuses.USER_DISABLED.equals(input.getStatus())) {
            throw ApiException.badRequest("用户状态不正确");
        }
    }

    private static void ensureUsernameUnique(String username, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM sys_user WHERE username = ?" + (excludeId == null ? "" : " AND id <> ?");
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            if (excludeId != null) {
                ps.setLong(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    throw ApiException.conflict("用户名已存在");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("校验用户名失败", e);
        }
    }

    private static void ensureTaxpayerBindingUnique(Long taxpayerId, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM sys_user WHERE taxpayer_id = ?" + (excludeId == null ? "" : " AND id <> ?");
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, taxpayerId);
            if (excludeId != null) {
                ps.setLong(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    throw ApiException.conflict("该纳税人已绑定其他账号");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("校验纳税人绑定失败", e);
        }
    }

    private static long countAdmins() {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM sys_user WHERE user_role = ? AND status = ?")) {
            ps.setString(1, Statuses.ROLE_ADMIN);
            ps.setString(2, Statuses.USER_ENABLED);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计管理员失败", e);
        }
    }

    private static long count(String sql, List<Object> params) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM (" + sql + ")")) {
            SqlUtil.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计用户失败", e);
        }
    }
}
