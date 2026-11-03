package com.example.qimo.service;

import com.example.qimo.config.ApiException;
import com.example.qimo.config.Database;
import com.example.qimo.model.AuditRecord;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.TaxDeclaration;
import com.example.qimo.model.User;
import com.example.qimo.util.AuthContext;
import com.example.qimo.util.SqlUtil;
import com.example.qimo.util.Statuses;
import com.example.qimo.util.Times;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class AuditService {

    private AuditService() {
    }

    public static TaxDeclaration review(Long declarationId, boolean approve, String comment, User auditor) {
        TaxDeclaration declaration = DeclarationService.findRaw(declarationId);
        if (!Statuses.DECLARATION_PENDING.equals(declaration.getStatus())) {
            throw ApiException.conflict("只有待审核的申报可以审核");
        }
        if (!approve && !SqlUtil.hasText(comment)) {
            throw ApiException.badRequest("驳回时请填写审核意见");
        }
        String now = Times.now();
        String status = approve ? Statuses.DECLARATION_APPROVED : Statuses.DECLARATION_REJECTED;
        String action = approve ? "通过" : "驳回";
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE tax_declaration SET status=?,reviewer=?,review_time=?,review_comment=?,updated_at=? WHERE id=?")) {
                ps.setString(1, status);
                ps.setString(2, auditor.getDisplayName());
                ps.setString(3, now);
                ps.setString(4, comment);
                ps.setString(5, now);
                ps.setLong(6, declarationId);
                ps.executeUpdate();
                DeclarationService.insertAudit(connection, declarationId, declaration.getDeclarationNo(),
                        action, comment, auditor.getDisplayName(), now);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("审核申报失败", e);
        }
        LogService.log(auditor.getUsername(), "审核", action,
                action + "申报：" + declaration.getDeclarationNo());
        return DeclarationService.findRaw(declarationId);
    }

    public static PageResult<AuditRecord> records(Long declarationId, String keyword, Integer page, Integer size, User user) {
        if (declarationId != null) {
            DeclarationService.assertAccess(user, DeclarationService.findRaw(declarationId));
        } else if (AuthContext.isTaxpayer(user) && user.getTaxpayerId() == null) {
            return new PageResult<>(new ArrayList<>(), 0, SqlUtil.page(page), SqlUtil.size(size));
        }
        int safePage = SqlUtil.page(page);
        int safeSize = SqlUtil.size(size);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (declarationId != null) {
            where.append(" AND ar.declaration_id = ?");
            params.add(declarationId);
        } else if (AuthContext.isTaxpayer(user)) {
            where.append(" AND d.taxpayer_id = ?");
            params.add(user.getTaxpayerId());
        }
        if (SqlUtil.hasText(keyword)) {
            where.append(" AND (ar.declaration_no LIKE ? OR ar.action LIKE ? OR ar.auditor LIKE ? OR ar.comment LIKE ?)");
            String like = SqlUtil.like(keyword);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        String base = "SELECT ar.* FROM audit_record ar JOIN tax_declaration d ON d.id = ar.declaration_id";
        long total = count(base + where, params);
        List<AuditRecord> items = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(SqlUtil.offset(safePage, safeSize));
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     base + where + " ORDER BY ar.id DESC LIMIT ? OFFSET ?")) {
            SqlUtil.bind(ps, queryParams);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditRecord record = new AuditRecord();
                    record.setId(rs.getLong("id"));
                    record.setDeclarationId(rs.getLong("declaration_id"));
                    record.setDeclarationNo(rs.getString("declaration_no"));
                    record.setAction(rs.getString("action"));
                    record.setComment(rs.getString("comment"));
                    record.setAuditor(rs.getString("auditor"));
                    record.setCreatedAt(rs.getString("created_at"));
                    items.add(record);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询审核记录失败", e);
        }
        return new PageResult<>(items, total, safePage, safeSize);
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
            throw new IllegalStateException("统计审核记录失败", e);
        }
    }
}
