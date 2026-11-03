package com.example.qimo.service;

import com.example.qimo.config.ApiException;
import com.example.qimo.config.Database;
import com.example.qimo.model.Invoice;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.Taxpayer;
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

public final class InvoiceService {

    private static final String SELECT_BASE = "SELECT i.*,t.name AS taxpayer_name FROM invoice i " +
            "JOIN taxpayer t ON t.id = i.taxpayer_id";

    private InvoiceService() {
    }

    public static PageResult<Invoice> list(User user, String keyword, String status, Integer page, Integer size) {
        int safePage = SqlUtil.page(page);
        int safeSize = SqlUtil.size(size);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (AuthContext.isTaxpayer(user)) {
            if (user.getTaxpayerId() == null) {
                return new PageResult<>(new ArrayList<>(), 0, safePage, safeSize);
            }
            where.append(" AND i.taxpayer_id = ?");
            params.add(user.getTaxpayerId());
        }
        if (SqlUtil.hasText(keyword)) {
            where.append(" AND (i.invoice_no LIKE ? OR t.name LIKE ? OR i.remark LIKE ?)");
            String like = SqlUtil.like(keyword);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (SqlUtil.hasText(status)) {
            where.append(" AND i.status = ?");
            params.add(status);
        }
        long total = count(SELECT_BASE + where, params);
        List<Invoice> items = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(SqlUtil.offset(safePage, safeSize));
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     SELECT_BASE + where + " ORDER BY i.id DESC LIMIT ? OFFSET ?")) {
            SqlUtil.bind(ps, queryParams);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询发票失败", e);
        }
        return new PageResult<>(items, total, safePage, safeSize);
    }

    public static Invoice get(Long id, User user) {
        Invoice invoice = findRaw(id);
        if (AuthContext.isTaxpayer(user) && !invoice.getTaxpayerId().equals(user.getTaxpayerId())) {
            throw ApiException.forbidden("无权查看该发票");
        }
        return invoice;
    }

    public static Invoice findRaw(Long id) {
        if (id == null) {
            throw ApiException.badRequest("发票ID不能为空");
        }
        List<Object> params = new ArrayList<>();
        params.add(id);
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BASE + " WHERE i.id = ?")) {
            SqlUtil.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询发票失败", e);
        }
        throw ApiException.notFound("发票不存在");
    }

    public static Invoice create(Invoice input, User operator) {
        validate(input);
        Taxpayer taxpayer = TaxpayerService.get(input.getTaxpayerId());
        if (Statuses.TAXPAYER_CANCELLED.equals(taxpayer.getStatus())) {
            throw ApiException.badRequest("已注销纳税人不能开票");
        }
        String invoiceNo = Numbers.invoiceNo();
        String status = SqlUtil.hasText(input.getStatus()) ? input.getStatus() : Statuses.INVOICE_NORMAL;
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO invoice(invoice_no,taxpayer_id,invoice_type,amount,tax_amount,issue_date,status,remark,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, invoiceNo);
            ps.setLong(2, input.getTaxpayerId());
            ps.setString(3, input.getInvoiceType().trim());
            ps.setBigDecimal(4, input.getAmount());
            ps.setBigDecimal(5, input.getTaxAmount());
            ps.setString(6, input.getIssueDate());
            ps.setString(7, status);
            ps.setString(8, input.getRemark());
            ps.setString(9, Times.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("新增发票失败", e);
        }
        LogService.log(operator.getUsername(), "发票", "开具", "开具发票：" + invoiceNo + " / " + taxpayer.getName());
        return findByNo(invoiceNo);
    }

    public static Invoice update(Long id, Invoice input, User operator) {
        Invoice existing = findRaw(id);
        if (Statuses.INVOICE_VOID.equals(existing.getStatus())) {
            throw ApiException.conflict("已作废发票不能修改");
        }
        validate(input);
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE invoice SET taxpayer_id=?,invoice_type=?,amount=?,tax_amount=?,issue_date=?,remark=? WHERE id=?")) {
            ps.setLong(1, input.getTaxpayerId());
            ps.setString(2, input.getInvoiceType().trim());
            ps.setBigDecimal(3, input.getAmount());
            ps.setBigDecimal(4, input.getTaxAmount());
            ps.setString(5, input.getIssueDate());
            ps.setString(6, input.getRemark());
            ps.setLong(7, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("修改发票失败", e);
        }
        LogService.log(operator.getUsername(), "发票", "修改", "修改发票：" + existing.getInvoiceNo());
        return findRaw(id);
    }

    public static Invoice voidInvoice(Long id, User operator) {
        Invoice existing = findRaw(id);
        if (Statuses.INVOICE_VOID.equals(existing.getStatus())) {
            throw ApiException.conflict("发票已作废");
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE invoice SET status=? WHERE id=?")) {
            ps.setString(1, Statuses.INVOICE_VOID);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("作废发票失败", e);
        }
        LogService.log(operator.getUsername(), "发票", "作废", "作废发票：" + existing.getInvoiceNo());
        return findRaw(id);
    }

    public static void delete(Long id, User operator) {
        Invoice existing = findRaw(id);
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM invoice WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("删除发票失败", e);
        }
        LogService.log(operator.getUsername(), "发票", "删除", "删除发票：" + existing.getInvoiceNo());
    }

    public static Invoice findByNo(String invoiceNo) {
        List<Object> params = new ArrayList<>();
        params.add(invoiceNo);
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_BASE + " WHERE i.invoice_no = ?")) {
            SqlUtil.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询发票失败", e);
        }
        throw ApiException.notFound("发票不存在");
    }

    private static void validate(Invoice invoice) {
        if (invoice == null) {
            throw ApiException.badRequest("发票信息不能为空");
        }
        if (invoice.getTaxpayerId() == null) {
            throw ApiException.badRequest("请选择开票纳税人");
        }
        if (!SqlUtil.hasText(invoice.getInvoiceType())) {
            throw ApiException.badRequest("请选择发票类型");
        }
        if (invoice.getAmount() == null || invoice.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("发票金额必须大于0");
        }
        if (invoice.getTaxAmount() == null || invoice.getTaxAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("税额不能小于0");
        }
        if (!SqlUtil.hasText(invoice.getIssueDate())) {
            throw ApiException.badRequest("请填写开票日期");
        }
    }

    private static Invoice map(ResultSet rs) throws SQLException {
        Invoice invoice = new Invoice();
        invoice.setId(rs.getLong("id"));
        invoice.setInvoiceNo(rs.getString("invoice_no"));
        invoice.setTaxpayerId(rs.getLong("taxpayer_id"));
        invoice.setTaxpayerName(rs.getString("taxpayer_name"));
        invoice.setInvoiceType(rs.getString("invoice_type"));
        invoice.setAmount(rs.getBigDecimal("amount"));
        invoice.setTaxAmount(rs.getBigDecimal("tax_amount"));
        invoice.setIssueDate(rs.getString("issue_date"));
        invoice.setStatus(rs.getString("status"));
        invoice.setRemark(rs.getString("remark"));
        invoice.setCreatedAt(rs.getString("created_at"));
        return invoice;
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
            throw new IllegalStateException("统计发票失败", e);
        }
    }
}
