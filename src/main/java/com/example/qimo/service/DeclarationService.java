package com.example.qimo.service;

import com.example.qimo.config.ApiException;
import com.example.qimo.config.Database;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.TaxDeclaration;
import com.example.qimo.model.TaxType;
import com.example.qimo.model.Taxpayer;
import com.example.qimo.model.User;
import com.example.qimo.util.AuthContext;
import com.example.qimo.util.Numbers;
import com.example.qimo.util.SqlUtil;
import com.example.qimo.util.Statuses;
import com.example.qimo.util.Times;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeclarationService {

    private static final String SELECT_BASE = "SELECT d.*,t.name AS taxpayer_name,t.taxpayer_no AS taxpayer_no," +
            "tt.name AS tax_type_name,tt.code AS tax_type_code " +
            "FROM tax_declaration d " +
            "JOIN taxpayer t ON t.id = d.taxpayer_id " +
            "JOIN tax_type tt ON tt.id = d.tax_type_id";

    private DeclarationService() {
    }

    public static PageResult<TaxDeclaration> search(User user, String keyword, String status, Long taxTypeId,
                                                    Long taxpayerId, String startDate, String endDate,
                                                    BigDecimal minAmount, BigDecimal maxAmount,
                                                    Integer page, Integer size) {
        int safePage = SqlUtil.page(page);
        int safeSize = SqlUtil.size(size);
        SearchQuery search = buildSearch(user, keyword, status, taxTypeId, taxpayerId, startDate, endDate, minAmount, maxAmount);
        long total = count(search.where.toString(), search.params);
        List<Object> queryParams = new ArrayList<>(search.params);
        queryParams.add(safeSize);
        queryParams.add(SqlUtil.offset(safePage, safeSize));
        List<TaxDeclaration> items = queryList(SELECT_BASE + search.where + " ORDER BY d.id DESC LIMIT ? OFFSET ?", queryParams);
        return new PageResult<>(items, total, safePage, safeSize);
    }

    public static Map<String, Object> summary(User user, String keyword, String status, Long taxTypeId,
                                              Long taxpayerId, String startDate, String endDate,
                                              BigDecimal minAmount, BigDecimal maxAmount) {
        SearchQuery search = buildSearch(user, keyword, status, taxTypeId, taxpayerId, startDate, endDate, minAmount, maxAmount);
        Map<String, Object> summary = new LinkedHashMap<>();
        String sql = "SELECT COUNT(*) AS total_count,COALESCE(SUM(d.taxable_amount),0) AS taxable_total," +
                "COALESCE(SUM(d.tax_amount),0) AS tax_total,COALESCE(SUM(d.paid_amount),0) AS paid_total " +
                "FROM tax_declaration d JOIN taxpayer t ON t.id = d.taxpayer_id " +
                "JOIN tax_type tt ON tt.id = d.tax_type_id" + search.where;
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            SqlUtil.bind(ps, search.params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.put("count", rs.getLong("total_count"));
                    summary.put("taxableTotal", rs.getBigDecimal("taxable_total"));
                    summary.put("taxTotal", rs.getBigDecimal("tax_total"));
                    summary.put("paidTotal", rs.getBigDecimal("paid_total"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计申报数据失败", e);
        }
        String statusSql = "SELECT d.status AS status,COUNT(*) AS amount FROM tax_declaration d " +
                "JOIN taxpayer t ON t.id = d.taxpayer_id JOIN tax_type tt ON tt.id = d.tax_type_id" +
                search.where + " GROUP BY d.status";
        List<Map<String, Object>> statusStats = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(statusSql)) {
            SqlUtil.bind(ps, search.params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("status", rs.getString("status"));
                    item.put("count", rs.getLong("amount"));
                    statusStats.add(item);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计申报状态失败", e);
        }
        summary.put("statusStats", statusStats);
        return summary;
    }

    public static List<TaxDeclaration> unpaid(User user) {
        StringBuilder where = new StringBuilder(" WHERE d.status = ? AND d.paid_amount < d.tax_amount");
        List<Object> params = new ArrayList<>();
        params.add(Statuses.DECLARATION_APPROVED);
        if (AuthContext.isTaxpayer(user)) {
            if (user.getTaxpayerId() == null) {
                return new ArrayList<>();
            }
            where.append(" AND d.taxpayer_id = ?");
            params.add(user.getTaxpayerId());
        }
        return queryList(SELECT_BASE + where + " ORDER BY d.id DESC LIMIT 200", params);
    }

    public static TaxDeclaration get(Long id, User user) {
        TaxDeclaration declaration = findRaw(id);
        assertAccess(user, declaration);
        return declaration;
    }

    public static TaxDeclaration findRaw(Long id) {
        if (id == null) {
            throw ApiException.badRequest("申报ID不能为空");
        }
        List<TaxDeclaration> list = queryList(SELECT_BASE + " WHERE d.id = ?", single(id));
        if (list.isEmpty()) {
            throw ApiException.notFound("申报记录不存在");
        }
        return list.get(0);
    }

    public static void assertAccess(User user, TaxDeclaration declaration) {
        if (AuthContext.isTaxpayer(user) && !declaration.getTaxpayerId().equals(user.getTaxpayerId())) {
            throw ApiException.forbidden("无权查看该申报记录");
        }
    }

    public static TaxDeclaration create(TaxDeclaration input, User user) {
        if (input == null) {
            throw ApiException.badRequest("申报信息不能为空");
        }
        Long taxpayerId = input.getTaxpayerId();
        if (AuthContext.isTaxpayer(user)) {
            if (user.getTaxpayerId() == null) {
                throw ApiException.forbidden("当前账号未绑定纳税人信息");
            }
            taxpayerId = user.getTaxpayerId();
        }
        if (taxpayerId == null) {
            throw ApiException.badRequest("请选择纳税人");
        }
        if (input.getTaxTypeId() == null) {
            throw ApiException.badRequest("请选择税种");
        }
        Taxpayer taxpayer = TaxpayerService.get(taxpayerId);
        if (Statuses.TAXPAYER_CANCELLED.equals(taxpayer.getStatus())) {
            throw ApiException.badRequest("已注销纳税人不能申报");
        }
        TaxType taxType = TaxTypeService.get(input.getTaxTypeId());
        if (!Statuses.USER_ENABLED.equals(taxType.getStatus())) {
            throw ApiException.badRequest("该税种已停用，不能申报");
        }
        validatePeriod(input);
        if (input.getTaxableAmount() == null || input.getTaxableAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("计税金额不能小于0");
        }
        BigDecimal taxAmount = calculate(input.getTaxableAmount(), taxType.getRate());
        String now = Times.now();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO tax_declaration(declaration_no,taxpayer_id,tax_type_id,period_start,period_end,taxable_amount,tax_rate,tax_amount,paid_amount,status,created_at,updated_at) " +
                             "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, Numbers.declarationNo());
            ps.setLong(2, taxpayerId);
            ps.setLong(3, input.getTaxTypeId());
            ps.setString(4, input.getPeriodStart());
            ps.setString(5, input.getPeriodEnd());
            ps.setBigDecimal(6, input.getTaxableAmount());
            ps.setBigDecimal(7, taxType.getRate());
            ps.setBigDecimal(8, taxAmount);
            ps.setBigDecimal(9, BigDecimal.ZERO);
            ps.setString(10, Statuses.DECLARATION_DRAFT);
            ps.setString(11, now);
            ps.setString(12, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("新增申报失败", e);
        }
        LogService.log(user.getUsername(), "申报", "新增", "新增申报：" + taxpayer.getName() + " / " + taxType.getName());
        return queryList(SELECT_BASE + " WHERE d.taxpayer_id = ? ORDER BY d.id DESC LIMIT 1", single(taxpayerId)).get(0);
    }

    public static TaxDeclaration update(Long id, TaxDeclaration input, User user) {
        TaxDeclaration existing = get(id, user);
        if (!Statuses.DECLARATION_DRAFT.equals(existing.getStatus())
                && !Statuses.DECLARATION_REJECTED.equals(existing.getStatus())) {
            throw ApiException.conflict("只有草稿或已驳回的申报可以修改");
        }
        if (AuthContext.isTaxpayer(user)) {
            if (input.getTaxpayerId() != null && !input.getTaxpayerId().equals(user.getTaxpayerId())) {
                throw ApiException.forbidden("无权修改该申报记录");
            }
            input.setTaxpayerId(user.getTaxpayerId());
        }
        if (input.getTaxTypeId() == null) {
            throw ApiException.badRequest("请选择税种");
        }
        TaxType taxType = TaxTypeService.get(input.getTaxTypeId());
        if (!Statuses.USER_ENABLED.equals(taxType.getStatus())) {
            throw ApiException.badRequest("该税种已停用，不能申报");
        }
        validatePeriod(input);
        if (input.getTaxableAmount() == null || input.getTaxableAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("计税金额不能小于0");
        }
        BigDecimal taxAmount = calculate(input.getTaxableAmount(), taxType.getRate());
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE tax_declaration SET tax_type_id=?,period_start=?,period_end=?,taxable_amount=?,tax_rate=?,tax_amount=?,updated_at=? WHERE id=?")) {
            ps.setLong(1, input.getTaxTypeId());
            ps.setString(2, input.getPeriodStart());
            ps.setString(3, input.getPeriodEnd());
            ps.setBigDecimal(4, input.getTaxableAmount());
            ps.setBigDecimal(5, taxType.getRate());
            ps.setBigDecimal(6, taxAmount);
            ps.setString(7, Times.now());
            ps.setLong(8, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("修改申报失败", e);
        }
        LogService.log(user.getUsername(), "申报", "修改", "修改申报：" + existing.getDeclarationNo());
        return findRaw(id);
    }

    public static void delete(Long id, User user) {
        TaxDeclaration existing = get(id, user);
        if (!Statuses.DECLARATION_DRAFT.equals(existing.getStatus())
                && !Statuses.DECLARATION_REJECTED.equals(existing.getStatus())) {
            throw ApiException.conflict("只有草稿或已驳回的申报可以删除");
        }
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement audit = connection.prepareStatement("DELETE FROM audit_record WHERE declaration_id = ?");
                 PreparedStatement ps = connection.prepareStatement("DELETE FROM tax_declaration WHERE id = ?")) {
                audit.setLong(1, id);
                audit.executeUpdate();
                ps.setLong(1, id);
                ps.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("删除申报失败", e);
        }
        LogService.log(user.getUsername(), "申报", "删除", "删除申报：" + existing.getDeclarationNo());
    }

    public static TaxDeclaration submit(Long id, User user) {
        TaxDeclaration existing = get(id, user);
        if (!Statuses.DECLARATION_DRAFT.equals(existing.getStatus())
                && !Statuses.DECLARATION_REJECTED.equals(existing.getStatus())) {
            throw ApiException.conflict("当前状态不能提交审核");
        }
        String now = Times.now();
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE tax_declaration SET status=?,submit_time=?,reviewer=NULL,review_time=NULL,review_comment=NULL,updated_at=? WHERE id=?")) {
                ps.setString(1, Statuses.DECLARATION_PENDING);
                ps.setString(2, now);
                ps.setString(3, now);
                ps.setLong(4, id);
                ps.executeUpdate();
                insertAudit(connection, id, existing.getDeclarationNo(), "提交", "纳税人提交申报", user.getDisplayName(), now);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("提交申报失败", e);
        }
        LogService.log(user.getUsername(), "申报", "提交", "提交申报：" + existing.getDeclarationNo());
        return findRaw(id);
    }

    public static void insertAudit(Connection connection, Long declarationId, String declarationNo, String action,
                                   String comment, String auditor, String createdAt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO audit_record(declaration_id,declaration_no,action,comment,auditor,created_at) VALUES(?,?,?,?,?,?)")) {
            ps.setLong(1, declarationId);
            ps.setString(2, declarationNo);
            ps.setString(3, action);
            ps.setString(4, comment);
            ps.setString(5, auditor);
            ps.setString(6, createdAt);
            ps.executeUpdate();
        }
    }

    public static BigDecimal calculate(BigDecimal taxableAmount, BigDecimal rate) {
        return taxableAmount.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static void validatePeriod(TaxDeclaration input) {
        if (!SqlUtil.hasText(input.getPeriodStart()) || !SqlUtil.hasText(input.getPeriodEnd())) {
            throw ApiException.badRequest("请填写申报所属期");
        }
        if (input.getPeriodStart().compareTo(input.getPeriodEnd()) > 0) {
            throw ApiException.badRequest("所属期起始日期不能晚于结束日期");
        }
    }

    private static SearchQuery buildSearch(User user, String keyword, String status, Long taxTypeId, Long taxpayerId,
                                           String startDate, String endDate, BigDecimal minAmount, BigDecimal maxAmount) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (AuthContext.isTaxpayer(user)) {
            if (user.getTaxpayerId() == null) {
                where.append(" AND 1=0");
            } else {
                where.append(" AND d.taxpayer_id = ?");
                params.add(user.getTaxpayerId());
            }
        } else if (taxpayerId != null) {
            where.append(" AND d.taxpayer_id = ?");
            params.add(taxpayerId);
        }
        if (SqlUtil.hasText(keyword)) {
            where.append(" AND (d.declaration_no LIKE ? OR t.name LIKE ? OR t.taxpayer_no LIKE ?)");
            String like = SqlUtil.like(keyword);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (SqlUtil.hasText(status)) {
            where.append(" AND d.status = ?");
            params.add(status);
        }
        if (taxTypeId != null) {
            where.append(" AND d.tax_type_id = ?");
            params.add(taxTypeId);
        }
        if (SqlUtil.hasText(startDate)) {
            where.append(" AND d.period_start >= ?");
            params.add(startDate);
        }
        if (SqlUtil.hasText(endDate)) {
            where.append(" AND d.period_end <= ?");
            params.add(endDate);
        }
        if (minAmount != null) {
            where.append(" AND d.tax_amount >= ?");
            params.add(minAmount);
        }
        if (maxAmount != null) {
            where.append(" AND d.tax_amount <= ?");
            params.add(maxAmount);
        }
        return new SearchQuery(where, params);
    }

    private static List<TaxDeclaration> queryList(String sql, List<Object> params) {
        List<TaxDeclaration> items = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            SqlUtil.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询申报数据失败", e);
        }
        return items;
    }

    private static long count(String where, List<Object> params) {
        String sql = "SELECT COUNT(*) FROM tax_declaration d JOIN taxpayer t ON t.id = d.taxpayer_id " +
                "JOIN tax_type tt ON tt.id = d.tax_type_id" + where;
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            SqlUtil.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计申报数据失败", e);
        }
    }

    private static List<Object> single(Object value) {
        List<Object> params = new ArrayList<>();
        params.add(value);
        return params;
    }

    private static TaxDeclaration map(ResultSet rs) throws SQLException {
        TaxDeclaration declaration = new TaxDeclaration();
        declaration.setId(rs.getLong("id"));
        declaration.setDeclarationNo(rs.getString("declaration_no"));
        declaration.setTaxpayerId(rs.getLong("taxpayer_id"));
        declaration.setTaxpayerName(rs.getString("taxpayer_name"));
        declaration.setTaxpayerNo(rs.getString("taxpayer_no"));
        declaration.setTaxTypeId(rs.getLong("tax_type_id"));
        declaration.setTaxTypeName(rs.getString("tax_type_name"));
        declaration.setTaxTypeCode(rs.getString("tax_type_code"));
        declaration.setPeriodStart(rs.getString("period_start"));
        declaration.setPeriodEnd(rs.getString("period_end"));
        declaration.setTaxableAmount(rs.getBigDecimal("taxable_amount"));
        declaration.setTaxRate(rs.getBigDecimal("tax_rate"));
        declaration.setTaxAmount(rs.getBigDecimal("tax_amount"));
        declaration.setPaidAmount(rs.getBigDecimal("paid_amount"));
        declaration.setStatus(rs.getString("status"));
        declaration.setSubmitTime(rs.getString("submit_time"));
        declaration.setReviewer(rs.getString("reviewer"));
        declaration.setReviewTime(rs.getString("review_time"));
        declaration.setReviewComment(rs.getString("review_comment"));
        declaration.setCreatedAt(rs.getString("created_at"));
        declaration.setUpdatedAt(rs.getString("updated_at"));
        return declaration;
    }

    private static final class SearchQuery {
        private final StringBuilder where;
        private final List<Object> params;

        private SearchQuery(StringBuilder where, List<Object> params) {
            this.where = where;
            this.params = params;
        }
    }
}
