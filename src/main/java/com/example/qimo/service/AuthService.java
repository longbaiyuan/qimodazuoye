package com.example.qimo.service;

import com.example.qimo.config.ApiException;
import com.example.qimo.config.Database;
import com.example.qimo.model.LoginRequest;
import com.example.qimo.model.LoginResponse;
import com.example.qimo.model.PasswordRequest;
import com.example.qimo.model.User;
import com.example.qimo.util.PasswordUtil;
import com.example.qimo.util.Statuses;
import com.example.qimo.util.SqlUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {

    private static final Map<String, User> TOKENS = new ConcurrentHashMap<>();

    private AuthService() {
    }

    public static LoginResponse login(LoginRequest request) {
        if (request == null || !SqlUtil.hasText(request.getUsername()) || !SqlUtil.hasText(request.getPassword())) {
            throw ApiException.badRequest("请输入用户名和密码");
        }
        User user = findByUsername(request.getUsername().trim());
        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getPassword())) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        if (!Statuses.USER_ENABLED.equals(user.getStatus())) {
            throw ApiException.forbidden("账号已停用，请联系管理员");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        TOKENS.put(token, user);
        LogService.log(user.getUsername(), "认证", "登录", user.getDisplayName() + " 登录系统");
        return new LoginResponse(token, user);
    }

    public static User findByToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        return TOKENS.get(token);
    }

    public static void logout(String token) {
        if (token != null) {
            TOKENS.remove(token);
        }
    }

    public static User changePassword(User current, PasswordRequest request) {
        if (request == null || !SqlUtil.hasText(request.getOldPassword()) || !SqlUtil.hasText(request.getNewPassword())) {
            throw ApiException.badRequest("请输入原密码和新密码");
        }
        if (request.getNewPassword().trim().length() < 6) {
            throw ApiException.badRequest("新密码长度不能少于6位");
        }
        User user = findByUsername(current.getUsername());
        if (user == null || !PasswordUtil.matches(request.getOldPassword(), user.getPassword())) {
            throw ApiException.badRequest("原密码不正确");
        }
        String hashed = PasswordUtil.hash(request.getNewPassword().trim());
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE sys_user SET password = ? WHERE id = ?")) {
            ps.setString(1, hashed);
            ps.setLong(2, current.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("修改密码失败", e);
        }
        user.setPassword(hashed);
        TOKENS.replaceAll((token, value) -> value.getId().equals(user.getId()) ? user : value);
        LogService.log(current.getUsername(), "认证", "修改密码", "用户修改登录密码");
        return user;
    }

    public static User findByUsername(String username) {
        String sql = "SELECT u.id,u.username,u.password,u.display_name,u.user_role,u.taxpayer_id," +
                "u.status,u.created_at,t.name AS taxpayer_name " +
                "FROM sys_user u LEFT JOIN taxpayer t ON t.id = u.taxpayer_id WHERE u.username = ?";
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询用户失败", e);
        }
    }

    public static User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setDisplayName(rs.getString("display_name"));
        user.setRole(rs.getString("user_role"));
        long taxpayerId = rs.getLong("taxpayer_id");
        user.setTaxpayerId(rs.wasNull() ? null : taxpayerId);
        user.setTaxpayerName(rs.getString("taxpayer_name"));
        user.setStatus(rs.getString("status"));
        user.setCreatedAt(rs.getString("created_at"));
        return user;
    }
}
