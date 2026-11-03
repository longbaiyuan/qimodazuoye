package com.example.qimo.service;

import com.example.qimo.config.Database;
import com.example.qimo.model.TaxDeclaration;
import com.example.qimo.model.User;
import com.example.qimo.util.AuthContext;
import com.example.qimo.util.Statuses;
import com.example.qimo.util.Times;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DashboardService {

    private DashboardService() {
    }

    public static Map<String, Object> stats(User user) {
        boolean taxpayer = AuthContext.isTaxpayer(user);
        Long taxpayerId = taxpayer ? user.getTaxpayerId() : null;
        String declFilter = taxpayer ? " AND d.taxpayer_id = ?" : "";
        List<Object> filterParams = taxpayer ? Collections.singletonList((Object) taxpayerId) : new ArrayList<>();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", user.getRole());
        result.put("displayName", user.getDisplayName());
        result.put("taxpayerCount", taxpayer ? (taxpayerId == null ? 0 : 1) : scalarLong(
                "SELECT COUNT(*) FROM taxpayer WHERE status = ?", Collections.singletonList((Object) Statuses.TAXPAYER_ACTIVE)));
        result.put("declarationCount", scalarLong(
                "SELECT COUNT(*) FROM tax_declaration d WHERE 1=1" + declFilter, filterParams));
        result.put("pendingCount", scalarLong(
                "SELECT COUNT(*) FROM tax_declaration d WHERE d.status = ?" + declFilter,
                prepend(Statuses.DECLARATION_PENDING, filterParams)));
        result.put("approvedCount", scalarLong(
                "SELECT COUNT(*) FROM tax_declaration d WHERE d.status = ?" + declFilter,
                prepend(Statuses.DECLARATION_APPROVED, filterParams)));
        result.put("paidTotal", scalarDecimal(
                "SELECT COALESCE(SUM(p.amount),0) FROM payment p JOIN tax_declaration d ON d.id = p.declaration_id WHERE 1=1" + declFilter,
                filterParams));
        result.put("unpaidAmount", scalarDecimal(
                "SELECT COALESCE(SUM(d.tax_amount - d.paid_amount),0) FROM tax_declaration d WHERE d.status = ?" + declFilter,
                prepend(Statuses.DECLARATION_APPROVED, filterParams)));
        result.put("invoiceCount", invoiceCount(taxpayer, taxpayerId));
        result.put("invoiceAmount", invoiceAmount(taxpayer, taxpayerId));
        result.put("monthlyTax", monthlyTax(taxpayer, taxpayerId));
        result.put("taxTypeDistribution", taxTypeDistribution(taxpayer, taxpayerId));
        result.put("topTaxpayers", topTaxpayers(taxpayer, taxpayerId));
        PageResultHolder recent = search(user, null, 5);
        result.put("recentDeclarations", recent.items);
        PageResultHolder pending = search(user, Statuses.DECLARATION_PENDING, 5);
        result.put("pendingDeclarations", pending.items);
        return result;
    }

    private static PageResultHolder search(User user, String status, int size) {
        List<TaxDeclaration> items = DeclarationService.search(user, null, status, null, null, null, null,
                null, null, 1, size).getItems();
        return new PageResultHolder(items);
    }

    private static List<Map<String, Object>> monthlyTax(boolean taxpayer, Long taxpayerId) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        String sql = "SELECT SUBSTRING(p.payment_date,1,7) AS ym,COALESCE(SUM(p.amount),0) AS amount " +
                "FROM payment p JOIN tax_declaration d ON d.id = p.declaration_id WHERE 1=1" +
                (taxpayer ? " AND d.taxpayer_id = ?" : "") +
                " GROUP BY SUBSTRING(p.payment_date,1,7)";
        List<Object> params = taxpayer ? Collections.singletonList((Object) taxpayerId) : new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.put(rs.getString("ym"), rs.getBigDecimal("amount"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计月度税款失败", e);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            String month = current.minusMonths(i).toString();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", month);
            item.put("amount", values.getOrDefault(month, BigDecimal.ZERO));
            items.add(item);
        }
        return items;
    }

    private static List<Map<String, Object>> taxTypeDistribution(boolean taxpayer, Long taxpayerId) {
        String sql = "SELECT tt.name AS name,COALESCE(SUM(d.tax_amount),0) AS amount FROM tax_declaration d " +
                "JOIN tax_type tt ON tt.id = d.tax_type_id " +
                "WHERE d.status IN (?,?)" + (taxpayer ? " AND d.taxpayer_id = ?" : "") +
                " GROUP BY tt.name ORDER BY amount DESC LIMIT 6";
        List<Object> params = new ArrayList<>();
        params.add(Statuses.DECLARATION_APPROVED);
        params.add(Statuses.DECLARATION_PAID);
        if (taxpayer) {
            params.add(taxpayerId);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", rs.getString("name"));
                    item.put("amount", rs.getBigDecimal("amount"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计税种分布失败", e);
        }
        return items;
    }

    private static List<Map<String, Object>> topTaxpayers(boolean taxpayer, Long taxpayerId) {
        String sql = "SELECT t.name AS name,COALESCE(SUM(p.amount),0) AS amount FROM payment p " +
                "JOIN tax_declaration d ON d.id = p.declaration_id JOIN taxpayer t ON t.id = d.taxpayer_id " +
                "WHERE 1=1" + (taxpayer ? " AND d.taxpayer_id = ?" : "") +
                " GROUP BY t.name ORDER BY amount DESC LIMIT 5";
        List<Object> params = taxpayer ? Collections.singletonList((Object) taxpayerId) : new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", rs.getString("name"));
                    item.put("amount", rs.getBigDecimal("amount"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计纳税人缴款排名失败", e);
        }
        return items;
    }

    private static long invoiceCount(boolean taxpayer, Long taxpayerId) {
        String sql = "SELECT COUNT(*) FROM invoice i WHERE 1=1" + (taxpayer ? " AND i.taxpayer_id = ?" : "");
        return scalarLong(sql, taxpayer ? Collections.singletonList((Object) taxpayerId) : new ArrayList<>());
    }

    private static BigDecimal invoiceAmount(boolean taxpayer, Long taxpayerId) {
        String sql = "SELECT COALESCE(SUM(i.amount),0) FROM invoice i WHERE i.status = ?" +
                (taxpayer ? " AND i.taxpayer_id = ?" : "");
        List<Object> params = new ArrayList<>();
        params.add(Statuses.INVOICE_NORMAL);
        if (taxpayer) {
            params.add(taxpayerId);
        }
        return scalarDecimal(sql, params);
    }

    private static List<Object> with(List<Object> params, Object value) {
        List<Object> result = new ArrayList<>(params);
        result.add(value);
        return result;
    }

    private static List<Object> prepend(Object first, List<Object> rest) {
        List<Object> result = new ArrayList<>();
        result.add(first);
        result.addAll(rest);
        return result;
    }

    private static long scalarLong(String sql, List<Object> params) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计失败", e);
        }
    }

    private static BigDecimal scalarDecimal(String sql, List<Object> params) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                BigDecimal value = rs.getBigDecimal(1);
                return value == null ? BigDecimal.ZERO : value;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("统计失败", e);
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static final class PageResultHolder {
        private final List<TaxDeclaration> items;

        private PageResultHolder(List<TaxDeclaration> items) {
            this.items = items;
        }
    }
}
