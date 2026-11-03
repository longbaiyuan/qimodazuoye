package com.example.qimo.util;

import com.example.qimo.config.ApiException;
import com.example.qimo.model.User;
import com.example.qimo.service.AuthService;
import jakarta.ws.rs.core.HttpHeaders;

public final class AuthContext {

    private AuthContext() {
    }

    public static String token(HttpHeaders headers) {
        String authorization = headers == null ? null : headers.getHeaderString("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        String headerToken = headers == null ? null : headers.getHeaderString("X-Auth-Token");
        return headerToken == null ? null : headerToken.trim();
    }

    public static User require(HttpHeaders headers) {
        User user = AuthService.findByToken(token(headers));
        if (user == null) {
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return user;
    }

    public static User requireRole(HttpHeaders headers, String... roles) {
        User user = require(headers);
        requireRole(user, roles);
        return user;
    }

    public static void requireRole(User user, String... roles) {
        for (String role : roles) {
            if (role.equals(user.getRole())) {
                return;
            }
        }
        throw ApiException.forbidden("当前账号无权执行该操作");
    }

    public static boolean isAdmin(User user) {
        return Statuses.ROLE_ADMIN.equals(user.getRole());
    }

    public static boolean isAuditor(User user) {
        return Statuses.ROLE_AUDITOR.equals(user.getRole());
    }

    public static boolean isTaxpayer(User user) {
        return Statuses.ROLE_TAXPAYER.equals(user.getRole());
    }

    public static boolean isStaff(User user) {
        return isAdmin(user) || isAuditor(user);
    }
}
