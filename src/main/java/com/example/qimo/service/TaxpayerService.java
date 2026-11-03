package com.example.qimo.service;

import com.example.qimo.config.ApiException;
import com.example.qimo.config.Database;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.Taxpayer;
import com.example.qimo.model.User;
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

public final class TaxpayerService {

    private static final List<String> VALID_STATUS = Arrays.asList(
            Statuses.TAXPAYER_ACTIVE, Statuses.TAXPAYER_SUSPENDED, Statuses.TAXPAYER_CANCELLED);

    private TaxpayerService() {
    }

    public static PageResult<Taxpayer> list(String keyword, String status, Integer page, Integer size) {
        int safePage = SqlUtil.page(page);
        int safeSize = SqlUtil.size(size);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (SqlUtil.hasText(keyword)) {
            where.append(" AND (name LIKE ? OR taxpayer_no LIKE ? OR legal_person LIKE ? OR phone LIKE ?)");
            String like = SqlUtil.like(keyword);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (SqlUtil.hasText(status)) {
            where.append(" AND status = ?");
            params.add(status);
        }
        String base = "SELECT * FROM taxpayer";
        long total = count(base + where, params);
        List<Taxpayer> items = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(SqlUtil.offset(safePage, safeSize));
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     base + where + " ORDER BY id DESC LIMIT ? OFFSET ?")) {
            SqlUtil.bind(ps, queryParams);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询纳税人失败", e);
        }
        return new PageResult<>(items, total, safePage, safeSize);
    }

    public static List<Taxpayer> options() {
        List<Taxpayer> items = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id,taxpayer_no,name,status FROM taxpayer WHERE status = ? ORDER BY name")) {
            ps.setString(1, Statuses.TAXPAYER_ACTIVE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Taxpayer taxpayer = new Taxpayer();
                    taxpayer.setId(rs.getLong("id"));
                    taxpayer.setTaxpayerNo(rs.getString("taxpayer_no"));
                    taxpayer.setName(rs.getString("name"));
                    taxpayer.setStatus(rs.getString("status"));
                    items.add(taxpayer);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询纳税人选项失败", e);
        }
        return items;
    }

    public static Taxpayer get(Long id) {
        if (id == null) {
            throw ApiException.badRequest("纳税人ID不能为空");
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM taxpayer WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询纳税人失败", e);
        }
        throw ApiException.notFound("纳税人不存在");
    }

    public static Taxpayer create(Taxpayer taxpayer, User operator) {
        validate(taxpayer);
        ensureTaxpayerNoUnique(taxpayer.getTaxpayerNo(), null);
        String now = Times.now();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO taxpayer(taxpayer_no,name,taxpayer_type,legal_person,id_card,phone,email,address,industry,registration_date,tax_office,status,created_at,updated_at) " +
                             "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            bind(ps, taxpayer, now, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("新增纳税人失败", e);
        }
        LogService.log(operator.getUsername(), "纳税人", "新增", "新增纳税人：" + taxpayer.getName());
        return findByNo(taxpayer.getTaxpayerNo());
    }

    public static Taxpayer update(Long id, Taxpayer taxpayer, User operator) {
        Taxpayer existing = get(id);
        validate(taxpayer);
        ensureTaxpayerNoUnique(taxpayer.getTaxpayerNo(), id);
        String now = Times.now();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE taxpayer SET taxpayer_no=?,name=?,taxpayer_type=?,legal_person=?,id_card=?,phone=?,email=?,address=?,industry=?,registration_date=?,tax_office=?,status=?,updated_at=? WHERE id=?")) {
            ps.setString(1, clean(taxpayer.getTaxpayerNo()));
            ps.setString(2, clean(taxpayer.getName()));
            ps.setString(3, clean(taxpayer.getTaxpayerType()));
            ps.setString(4, clean(taxpayer.getLegalPerson()));
            ps.setString(5, clean(taxpayer.getIdCard()));
            ps.setString(6, clean(taxpayer.getPhone()));
            ps.setString(7, clean(taxpayer.getEmail()));
            ps.setString(8, clean(taxpayer.getAddress()));
            ps.setString(9, clean(taxpayer.getIndustry()));
            ps.setString(10, clean(taxpayer.getRegistrationDate()));
            ps.setString(11, clean(taxpayer.getTaxOffice()));
            ps.setString(12, taxpayer.getStatus());
            ps.setString(13, now);
            ps.setLong(14, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("修改纳税人失败", e);
        }
        LogService.log(operator.getUsername(), "纳税人", "修改", "修改纳税人：" + existing.getName());
        return get(id);
    }

    public static void delete(Long id, User operator) {
        Taxpayer existing = get(id);
        if (countBy("tax_declaration", "taxpayer_id", id) > 0) {
            throw ApiException.conflict("该纳税人存在申报记录，不能删除");
        }
        if (countBy("invoice", "taxpayer_id", id) > 0) {
            throw ApiException.conflict("该纳税人存在发票记录，不能删除");
        }
        if (countBy("sys_user", "taxpayer_id", id) > 0) {
            throw ApiException.conflict("该纳税人已绑定系统账号，不能删除");
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM taxpayer WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("删除纳税人失败", e);
        }
        LogService.log(operator.getUsername(), "纳税人", "删除", "删除纳税人：" + existing.getName());
    }

    public static Taxpayer findByNo(String taxpayerNo) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM taxpayer WHERE taxpayer_no = ?")) {
            ps.setString(1, taxpayerNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询纳税人失败", e);
        }
        throw ApiException.notFound("纳税人不存在");
    }

    private static void validate(Taxpayer taxpayer) {
        if (taxpayer == null) {
            throw ApiException.badRequest("纳税人信息不能为空");
        }
        if (!SqlUtil.hasText(taxpayer.getTaxpayerNo())) {
            throw ApiException.badRequest("纳税人识别号不能为空");
        }
        if (!SqlUtil.hasText(taxpayer.getName())) {
            throw ApiException.badRequest("纳税人名称不能为空");
        }
        if (!SqlUtil.hasText(taxpayer.getTaxpayerType())) {
            throw ApiException.badRequest("纳税人类型不能为空");
        }
        if (!SqlUtil.hasText(taxpayer.getStatus()) || !VALID_STATUS.contains(taxpayer.getStatus())) {
            throw ApiException.badRequest("纳税人状态不正确");
        }
    }

    private static void ensureTaxpayerNoUnique(String taxpayerNo, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM taxpayer WHERE taxpayer_no = ?" + (excludeId == null ? "" : " AND id <> ?");
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, taxpayerNo.trim());
            if (excludeId != null) {
                ps.setLong(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    throw ApiException.conflict("纳税人识别号已存在");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("校验纳税人识别号失败", e);
        }
    }

    private static void bind(PreparedStatement ps, Taxpayer taxpayer, String createdAt, String updatedAt) throws SQLException {
        ps.setString(1, clean(taxpayer.getTaxpayerNo()));
        ps.setString(2, clean(taxpayer.getName()));
        ps.setString(3, clean(taxpayer.getTaxpayerType()));
        ps.setString(4, clean(taxpayer.getLegalPerson()));
        ps.setString(5, clean(taxpayer.getIdCard()));
        ps.setString(6, clean(taxpayer.getPhone()));
        ps.setString(7, clean(taxpayer.getEmail()));
        ps.setString(8, clean(taxpayer.getAddress()));
        ps.setString(9, clean(taxpayer.getIndustry()));
        ps.setString(10, clean(taxpayer.getRegistrationDate()));
        ps.setString(11, clean(taxpayer.getTaxOffice()));
        ps.setString(12, taxpayer.getStatus());
        ps.setString(13, createdAt);
        ps.setString(14, updatedAt);
    }

    private static Taxpayer map(ResultSet rs) throws SQLException {
        Taxpayer taxpayer = new Taxpayer();
        taxpayer.setId(rs.getLong("id"));
        taxpayer.setTaxpayerNo(rs.getString("taxpayer_no"));
        taxpayer.setName(rs.getString("name"));
        taxpayer.setTaxpayerType(rs.getString("taxpayer_type"));
        taxpayer.setLegalPerson(rs.getString("legal_person"));
        taxpayer.setIdCard(rs.getString("id_card"));
        taxpayer.setPhone(rs.getString("phone"));
        taxpayer.setEmail(rs.getString("email"));
        taxpayer.setAddress(rs.getString("address"));
        taxpayer.setIndustry(rs.getString("industry"));
        taxpayer.setRegistrationDate(rs.getString("registration_date"));
        taxpayer.setTaxOffice(rs.getString("tax_office"));
        taxpayer.setStatus(rs.getString("status"));
        taxpayer.setCreatedAt(rs.getString("created_at"));
        taxpayer.setUpdatedAt(rs.getString("updated_at"));
        return taxpayer;
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
            throw new IllegalStateException("统计纳税人失败", e);
        }
    }

    private static long countBy(String table, String column, Long id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("校验关联数据失败", e);
        }
    }

    private static String clean(String value) {
        return value == null ? null : value.trim();
    }
}
