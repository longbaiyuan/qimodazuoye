package com.example.qimo.service;

import com.example.qimo.config.ApiException;
import com.example.qimo.config.Database;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.Payment;
import com.example.qimo.model.PaymentRequest;
import com.example.qimo.model.TaxDeclaration;
import com.example.qimo.model.User;
import com.example.qimo.util.AuthContext;
import com.example.qimo.util.Numbers;
import com.example.qimo.util.SqlUtil;
import com.example.qimo.util.Statuses;
import com.example.qimo.util.Times;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PaymentService {

    private static final String SELECT_BASE = "SELECT p.*,d.declaration_no AS declaration_no,t.name AS taxpayer_name," +
            "tt.name AS tax_type_name FROM payment p " +
            "JOIN tax_declaration d ON d.id = p.declaration_id " +
            "JOIN taxpayer t ON t.id = d.taxpayer_id " +
            "JOIN tax_type tt ON tt.id = d.tax_type_id";

    private PaymentService() {
    }

    public static PageResult<Payment> list(User user, String keyword, Integer page, Integer size) {
        int safePage = SqlUtil.page(page);
        int safeSize = SqlUtil.size(size);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (AuthContext.isTaxpayer(user)) {
            if (user.getTaxpayerId() == null) {
                return new PageResult<>(new ArrayList<>(), 0, safePage, safeSize);
            }
            where.append(" AND d.taxpayer_id = ?");
            params.add(user.getTaxpayerId());
        }
        if (SqlUtil.hasText(keyword)) {
            where.append(" AND (p.payment_no LIKE ? OR d.declaration_no LIKE ? OR t.name LIKE ?)");
            String like = SqlUtil.like(keyword);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        long total = count(SELECT_BASE + where, params);
        List<Payment> items = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(SqlUtil.offset(safePage, safeSize));
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     SELECT_BASE + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?")) {
            SqlUtil.bind(ps, queryParams);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询缴款记录失败", e);
        }
        return new PageResult<>(items, total, safePage, safeSize);
    }

    public static Payment pay(PaymentRequest request, User user) {
        if (request == null || request.getDeclarationId() == null) {
            throw ApiException.badRequest("请选择需要缴款的申报");
        }
        TaxDeclaration declaration = DeclarationService.findRaw(request.getDeclarationId());
        DeclarationService.assertAccess(user, declaration);
        if (!Statuses.DECLARATION_APPROVED.equals(declaration.getStatus())) {
            throw ApiException.conflict("只有已通过且未缴款的申报可以缴款");
        }
        BigDecimal amount = declaration.getTaxAmount().subtract(declaration.getPaidAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.conflict("该申报已完成缴款");
        }
        String method = SqlUtil.hasText(request.getMethod()) ? request.getMethod().trim() : "银行扣款";
        String now = Times.now();
        String paymentNo = Numbers.paymentNo();
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO payment(payment_no,declaration_id,amount,method,payment_date,status,operator,created_at) VALUES(?,?,?,?,?,?,?,?)")) {
                ps.setString(1, paymentNo);
                ps.setLong(2, declaration.getId());
                ps.setBigDecimal(3, amount);
                ps.setString(4, method);
                ps.setString(5, now);
                ps.setString(6, Statuses.PAYMENT_PAID);
                ps.setString(7, user.getDisplayName());
                ps.setString(8, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE tax_declaration SET paid_amount=?,status=?,updated_at=? WHERE id=?")) {
                ps.setBigDecimal(1, declaration.getTaxAmount());
                ps.setString(2, Statuses.DECLARATION_PAID);
                ps.setString(3, now);
                ps.setLong(4, declaration.getId());
                ps.executeUpdate();
            }
            DeclarationService.insertAudit(connection, declaration.getId(), declaration.getDeclarationNo(),
                    "缴款", "缴纳税款 " + amount.toPlainString() + " 元，方式：" + method,
                    user.getDisplayName(), now);
            connection.commit();
            LogService.log(user.getUsername(), "缴款", "缴款",
                    "申报 " + declaration.getDeclarationNo() + " 缴款 " + amount.toPlainString() + " 元");
        } catch (SQLException e) {
            throw new IllegalStateException("缴款失败", e);
        }
        return findByNo(paymentNo);
    }

    public static Payment findByNo(String paymentNo) {
        List<Object> params = new ArrayList<>();
        params.add(paymentNo);
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BASE + " WHERE p.payment_no = ?")) {
            SqlUtil.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询缴款记录失败", e);
        }
        throw ApiException.notFound("缴款记录不存在");
    }

    private static Payment map(ResultSet rs) throws SQLException {
        Payment payment = new Payment();
        payment.setId(rs.getLong("id"));
        payment.setPaymentNo(rs.getString("payment_no"));
        payment.setDeclarationId(rs.getLong("declaration_id"));
        payment.setDeclarationNo(rs.getString("declaration_no"));
        payment.setTaxpayerName(rs.getString("taxpayer_name"));
        payment.setTaxTypeName(rs.getString("tax_type_name"));
        payment.setAmount(rs.getBigDecimal("amount"));
        payment.setMethod(rs.getString("method"));
        payment.setPaymentDate(rs.getString("payment_date"));
        payment.setStatus(rs.getString("status"));
        payment.setOperator(rs.getString("operator"));
        payment.setCreatedAt(rs.getString("created_at"));
        return payment;
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
            throw new IllegalStateException("统计缴款记录失败", e);
        }
    }
}
