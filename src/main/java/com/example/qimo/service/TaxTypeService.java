package com.example.qimo.service;

import com.example.qimo.config.ApiException;
import com.example.qimo.config.Database;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.TaxType;
import com.example.qimo.model.User;
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

public final class TaxTypeService {

    private TaxTypeService() {
    }

    public static PageResult<TaxType> list(String keyword, String status, Integer page, Integer size) {
        int safePage = SqlUtil.page(page);
        int safeSize = SqlUtil.size(size);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (SqlUtil.hasText(keyword)) {
            where.append(" AND (code LIKE ? OR name LIKE ?)");
            params.add(SqlUtil.like(keyword));
            params.add(SqlUtil.like(keyword));
        }
        if (SqlUtil.hasText(status)) {
            where.append(" AND status = ?");
            params.add(status);
        }
        String base = "SELECT * FROM tax_type";
        long total = count(base + where, params);
        List<TaxType> items = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(SqlUtil.offset(safePage, safeSize));
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     base + where + " ORDER BY id ASC LIMIT ? OFFSET ?")) {
            SqlUtil.bind(ps, queryParams);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询税种失败", e);
        }
        return new PageResult<>(items, total, safePage, safeSize);
    }

    public static List<TaxType> options() {
        List<TaxType> items = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM tax_type WHERE status = ? ORDER BY id")) {
            ps.setString(1, Statuses.USER_ENABLED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询税种选项失败", e);
        }
        return items;
    }

    public static TaxType get(Long id) {
        if (id == null) {
            throw ApiException.badRequest("税种ID不能为空");
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM tax_type WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询税种失败", e);
        }
        throw ApiException.notFound("税种不存在");
    }

    public static TaxType create(TaxType taxType, User operator) {
        validate(taxType);
        ensureCodeUnique(taxType.getCode(), null);
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO tax_type(code,name,rate,description,status,created_at) VALUES(?,?,?,?,?,?)")) {
            bind(ps, taxType);
            ps.setString(6, Times.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("新增税种失败", e);
        }
        LogService.log(operator.getUsername(), "税种", "新增", "新增税种：" + taxType.getName());
        return findByCode(taxType.getCode());
    }

    public static TaxType update(Long id, TaxType taxType, User operator) {
        TaxType existing = get(id);
        validate(taxType);
        ensureCodeUnique(taxType.getCode(), id);
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE tax_type SET code=?,name=?,rate=?,description=?,status=? WHERE id=?")) {
            ps.setString(1, taxType.getCode().trim());
            ps.setString(2, taxType.getName().trim());
            ps.setBigDecimal(3, taxType.getRate());
            ps.setString(4, taxType.getDescription());
            ps.setString(5, taxType.getStatus());
            ps.setLong(6, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("修改税种失败", e);
        }
        LogService.log(operator.getUsername(), "税种", "修改", "修改税种：" + existing.getName());
        return get(id);
    }

    public static void delete(Long id, User operator) {
        TaxType existing = get(id);
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM tax_declaration WHERE tax_type_id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    throw ApiException.conflict("该税种存在申报记录，不能删除");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("校验税种关联数据失败", e);
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM tax_type WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("删除税种失败", e);
        }
        LogService.log(operator.getUsername(), "税种", "删除", "删除税种：" + existing.getName());
    }

    public static TaxType findByCode(String code) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM tax_type WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询税种失败", e);
        }
        throw ApiException.notFound("税种不存在");
    }

    private static void validate(TaxType taxType) {
        if (taxType == null) {
            throw ApiException.badRequest("税种信息不能为空");
        }
        if (!SqlUtil.hasText(taxType.getCode())) {
            throw ApiException.badRequest("税种代码不能为空");
        }
        if (!SqlUtil.hasText(taxType.getName())) {
            throw ApiException.badRequest("税种名称不能为空");
        }
        if (taxType.getRate() == null || taxType.getRate().compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("税率不能小于0");
        }
        if (!Statuses.USER_ENABLED.equals(taxType.getStatus()) && !Statuses.USER_DISABLED.equals(taxType.getStatus())) {
            throw ApiException.badRequest("税种状态不正确");
        }
    }

    private static void ensureCodeUnique(String code, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM tax_type WHERE code = ?" + (excludeId == null ? "" : " AND id <> ?");
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            if (excludeId != null) {
                ps.setLong(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    throw ApiException.conflict("税种代码已存在");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("校验税种代码失败", e);
        }
    }

    private static void bind(PreparedStatement ps, TaxType taxType) throws SQLException {
        ps.setString(1, taxType.getCode().trim());
        ps.setString(2, taxType.getName().trim());
        ps.setBigDecimal(3, taxType.getRate());
        ps.setString(4, taxType.getDescription());
        ps.setString(5, taxType.getStatus());
    }

    private static TaxType map(ResultSet rs) throws SQLException {
        TaxType taxType = new TaxType();
        taxType.setId(rs.getLong("id"));
        taxType.setCode(rs.getString("code"));
        taxType.setName(rs.getString("name"));
        taxType.setRate(rs.getBigDecimal("rate"));
        taxType.setDescription(rs.getString("description"));
        taxType.setStatus(rs.getString("status"));
        taxType.setCreatedAt(rs.getString("created_at"));
        return taxType;
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
            throw new IllegalStateException("统计税种失败", e);
        }
    }
}
