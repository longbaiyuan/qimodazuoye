package com.example.qimo.config;

import com.example.qimo.util.PasswordUtil;
import com.example.qimo.util.Statuses;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SeedData {

    private SeedData() {
    }

    public static void seed(Connection connection) throws SQLException {
        if (count(connection, "sys_user") > 0) {
            return;
        }
        connection.setAutoCommit(false);
        try {
            seedTaxTypes(connection);
            seedTaxpayers(connection);
            seedUsers(connection);
            seedDeclarations(connection);
            seedInvoices(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO operation_log(username,module,action,detail,created_at) VALUES(?,?,?,?,?)")) {
                ps.setString(1, "system");
                ps.setString(2, "系统");
                ps.setString(3, "初始化");
                ps.setString(4, "税务业务基础管理系统初始化完成，演示数据已加载");
                ps.setString(5, "2026-10-01 08:00:00");
                ps.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static void seedTaxTypes(Connection connection) throws SQLException {
        String[][] types = {
                {"VAT", "增值税", "13.0000", "销售货物、提供加工修理修配劳务等"},
                {"CIT", "企业所得税", "25.0000", "居民企业应纳税所得额"},
                {"IIT", "个人所得税", "3.0000", "综合所得预扣预缴"},
                {"STAMP", "印花税", "0.0500", "经济合同及产权转移书据"},
                {"CONSUMPTION", "消费税", "5.0000", "特定消费品和消费行为"},
                {"PROPERTY", "房产税", "1.2000", "房屋原值或租金收入"}
        };
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tax_type(code,name,rate,description,status,created_at) VALUES(?,?,?,?,?,?)")) {
            for (String[] type : types) {
                ps.setString(1, type[0]);
                ps.setString(2, type[1]);
                ps.setBigDecimal(3, new BigDecimal(type[2]));
                ps.setString(4, type[3]);
                ps.setString(5, Statuses.USER_ENABLED);
                ps.setString(6, "2026-01-01 09:00:00");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void seedTaxpayers(Connection connection) throws SQLException {
        String[][] taxpayers = {
                {"91310000MA1K12345A", "上海华信科技有限公司", "企业", "张伟", "310101198505121234",
                        "13800138001", "finance@huaxin.com", "上海市浦东新区张江路88号", "信息技术",
                        "2018-03-12", "浦东新区税务局第一税务所", "正常"},
                {"91310000MA1K67890B", "上海恒达贸易有限公司", "企业", "刘洋", "310104198802203456",
                        "13900139002", "service@hengda.com", "上海市徐汇区漕溪路168号", "批发零售",
                        "2016-07-20", "徐汇区税务局第二税务所", "正常"},
                {"92310000MA2K55555C", "静安区鑫盛商贸中心", "个体工商户", "王芳", "310106199003154567",
                        "13700137003", "xinsheng@example.com", "上海市静安区南京西路520号", "批发零售",
                        "2020-05-18", "静安区税务局第三税务所", "正常"},
                {"310101199001011234", "李明", "自然人", "李明", "310101199001011234",
                        "13600136004", "liming@example.com", "上海市黄浦区人民大道100号", "自由职业",
                        "2021-01-05", "黄浦区税务局", "正常"},
                {"91310000MA1K77777D", "上海远航制造有限公司", "企业", "陈强", "310115198711227890",
                        "13500135005", "office@yuanhang.com", "上海市浦东新区金桥路666号", "制造业",
                        "2012-11-08", "浦东新区税务局第四税务所", "停业"},
                {"91310000MA1K88888E", "上海恒信咨询服务有限公司", "企业", "赵敏", "310110199205056789",
                        "13400134006", "hello@hengxin.com", "上海市杨浦区五角场路99号", "商务服务",
                        "2019-09-25", "杨浦区税务局第一税务所", "注销"}
        };
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO taxpayer(taxpayer_no,name,taxpayer_type,legal_person,id_card,phone,email,address,industry,registration_date,tax_office,status,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (String[] taxpayer : taxpayers) {
                ps.setString(1, taxpayer[0]);
                ps.setString(2, taxpayer[1]);
                ps.setString(3, taxpayer[2]);
                ps.setString(4, taxpayer[3]);
                ps.setString(5, taxpayer[4]);
                ps.setString(6, taxpayer[5]);
                ps.setString(7, taxpayer[6]);
                ps.setString(8, taxpayer[7]);
                ps.setString(9, taxpayer[8]);
                ps.setString(10, taxpayer[9]);
                ps.setString(11, taxpayer[10]);
                ps.setString(12, taxpayer[11]);
                ps.setString(13, "2026-01-01 09:00:00");
                ps.setString(14, "2026-01-01 09:00:00");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void seedUsers(Connection connection) throws SQLException {
        long taxpayer1 = idBy(connection, "taxpayer", "taxpayer_no", "91310000MA1K12345A");
        long taxpayer2 = idBy(connection, "taxpayer", "taxpayer_no", "91310000MA1K67890B");
        Object[][] users = {
                {"admin", PasswordUtil.hash("admin123"), "系统管理员", Statuses.ROLE_ADMIN, null, Statuses.USER_ENABLED},
                {"auditor", PasswordUtil.hash("audit123"), "审核员·周敏", Statuses.ROLE_AUDITOR, null, Statuses.USER_ENABLED},
                {"taxpayer", PasswordUtil.hash("demo123"), "张伟（华信科技）", Statuses.ROLE_TAXPAYER, taxpayer1, Statuses.USER_ENABLED},
                {"taxpayer2", PasswordUtil.hash("demo123"), "刘洋（恒达贸易）", Statuses.ROLE_TAXPAYER, taxpayer2, Statuses.USER_ENABLED}
        };
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sys_user(username,password,display_name,user_role,taxpayer_id,status,created_at) VALUES(?,?,?,?,?,?,?)")) {
            for (Object[] user : users) {
                ps.setString(1, (String) user[0]);
                ps.setString(2, (String) user[1]);
                ps.setString(3, (String) user[2]);
                ps.setString(4, (String) user[3]);
                if (user[4] == null) {
                    ps.setObject(5, null);
                } else {
                    ps.setLong(5, (Long) user[4]);
                }
                ps.setString(6, (String) user[5]);
                ps.setString(7, "2026-01-01 09:00:00");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void seedDeclarations(Connection connection) throws SQLException {
        long tp1 = idBy(connection, "taxpayer", "taxpayer_no", "91310000MA1K12345A");
        long tp2 = idBy(connection, "taxpayer", "taxpayer_no", "91310000MA1K67890B");
        long tp3 = idBy(connection, "taxpayer", "taxpayer_no", "92310000MA2K55555C");
        long tp4 = idBy(connection, "taxpayer", "taxpayer_no", "310101199001011234");
        long tp5 = idBy(connection, "taxpayer", "taxpayer_no", "91310000MA1K77777D");

        long vat = idBy(connection, "tax_type", "code", "VAT");
        long cit = idBy(connection, "tax_type", "code", "CIT");
        long iit = idBy(connection, "tax_type", "code", "IIT");
        long stamp = idBy(connection, "tax_type", "code", "STAMP");

        insertDeclaration(connection, "SB20260508001", tp3, vat, "2026-04-01", "2026-04-30",
                "400000.00", "13.0000", "52000.00", "52000.00", Statuses.DECLARATION_PAID,
                "2026-05-08 09:20:00", "周敏", "2026-05-09 10:10:00", "资料齐全，准予通过。",
                "2026-05-08 09:20:00", "2026-05-10 10:30:00");
        insertDeclaration(connection, "SB20260612002", tp2, vat, "2026-05-01", "2026-05-31",
                "500000.00", "13.0000", "65000.00", "65000.00", Statuses.DECLARATION_PAID,
                "2026-06-12 09:00:00", "周敏", "2026-06-13 14:20:00", "申报数据与发票数据一致。",
                "2026-06-12 09:00:00", "2026-06-14 11:00:00");
        insertDeclaration(connection, "SB20260715003", tp1, vat, "2026-06-01", "2026-06-30",
                "600000.00", "13.0000", "78000.00", "78000.00", Statuses.DECLARATION_PAID,
                "2026-07-15 10:05:00", "周敏", "2026-07-16 09:30:00", "资料齐全，准予通过。",
                "2026-07-15 10:05:00", "2026-07-16 15:40:00");
        insertDeclaration(connection, "SB20260808004", tp3, vat, "2026-07-01", "2026-07-31",
                "700000.00", "13.0000", "91000.00", "91000.00", Statuses.DECLARATION_PAID,
                "2026-08-08 09:30:00", "周敏", "2026-08-09 11:00:00", "资料齐全，准予通过。",
                "2026-08-08 09:30:00", "2026-08-10 10:00:00");
        insertDeclaration(connection, "SB20260918005", tp2, cit, "2026-07-01", "2026-09-30",
                "860000.00", "25.0000", "215000.00", "215000.00", Statuses.DECLARATION_PAID,
                "2026-09-18 10:00:00", "周敏", "2026-09-19 09:20:00", "季度申报审核通过。",
                "2026-09-18 10:00:00", "2026-09-20 13:10:00");
        insertDeclaration(connection, "SB20261005006", tp1, vat, "2026-09-01", "2026-09-30",
                "1200000.00", "13.0000", "156000.00", "156000.00", Statuses.DECLARATION_PAID,
                "2026-10-05 09:10:00", "周敏", "2026-10-06 10:30:00", "申报数据完整，审核通过。",
                "2026-10-05 09:10:00", "2026-10-08 09:50:00");
        insertDeclaration(connection, "SB20261008007", tp3, vat, "2026-09-01", "2026-09-30",
                "260000.00", "13.0000", "33800.00", "0.00", Statuses.DECLARATION_APPROVED,
                "2026-10-08 14:00:00", "周敏", "2026-10-09 09:40:00", "审核通过，请及时缴纳税款。",
                "2026-10-08 14:00:00", null);
        insertDeclaration(connection, "SB20261012008", tp4, iit, "2026-09-01", "2026-09-30",
                "18000.00", "3.0000", "540.00", "0.00", Statuses.DECLARATION_PENDING,
                "2026-10-12 09:15:00", null, null, null, "2026-10-12 09:15:00", null);
        insertDeclaration(connection, "SB20261015009", tp1, vat, "2026-10-01", "2026-10-31",
                "980000.00", "13.0000", "127400.00", "0.00", Statuses.DECLARATION_PENDING,
                "2026-10-15 10:20:00", null, null, null, "2026-10-15 10:20:00", null);
        insertDeclaration(connection, "SB20261018010", tp5, cit, "2026-07-01", "2026-09-30",
                "450000.00", "25.0000", "112500.00", "0.00", Statuses.DECLARATION_REJECTED,
                "2026-10-18 11:00:00", "周敏", "2026-10-19 15:30:00", "成本费用凭证不完整，请补充后重新申报。",
                "2026-10-18 11:00:00", null);
        insertDeclaration(connection, "SB20261020011", tp2, stamp, "2026-09-01", "2026-09-30",
                "3000000.00", "0.0500", "1500.00", "1500.00", Statuses.DECLARATION_PAID,
                "2026-10-20 09:40:00", "周敏", "2026-10-20 16:10:00", "印花税申报审核通过。",
                "2026-10-20 09:40:00", "2026-10-21 10:20:00");
        insertDeclaration(connection, "SB20261022012", tp3, vat, "2026-10-01", "2026-10-31",
                "300000.00", "13.0000", "39000.00", "0.00", Statuses.DECLARATION_DRAFT,
                null, null, null, null, "2026-10-22 10:00:00", null);
        insertDeclaration(connection, "SB20261023013", tp1, cit, "2026-07-01", "2026-09-30",
                "1500000.00", "25.0000", "375000.00", "375000.00", Statuses.DECLARATION_PAID,
                "2026-10-20 10:00:00", "周敏", "2026-10-21 09:10:00", "季度企业所得税审核通过。",
                "2026-10-20 10:00:00", "2026-10-22 10:30:00");
        insertDeclaration(connection, "SB20261025014", tp2, vat, "2026-10-01", "2026-10-31",
                "730000.00", "13.0000", "94900.00", "0.00", Statuses.DECLARATION_PENDING,
                "2026-10-25 14:30:00", null, null, null, "2026-10-25 14:30:00", null);
    }

    private static void insertDeclaration(Connection connection, String declarationNo, long taxpayerId,
                                          long taxTypeId, String periodStart, String periodEnd,
                                          String taxableAmount, String taxRate, String taxAmount,
                                          String paidAmount, String status, String submitTime,
                                          String reviewer, String reviewTime, String reviewComment,
                                          String createdAt, String paymentDate) throws SQLException {
        String updatedAt = reviewTime != null ? reviewTime : (submitTime != null ? submitTime : createdAt);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO tax_declaration(declaration_no,taxpayer_id,tax_type_id,period_start,period_end,taxable_amount,tax_rate,tax_amount,paid_amount,status,submit_time,reviewer,review_time,review_comment,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, declarationNo);
            ps.setLong(2, taxpayerId);
            ps.setLong(3, taxTypeId);
            ps.setString(4, periodStart);
            ps.setString(5, periodEnd);
            ps.setBigDecimal(6, new BigDecimal(taxableAmount));
            ps.setBigDecimal(7, new BigDecimal(taxRate));
            ps.setBigDecimal(8, new BigDecimal(taxAmount));
            ps.setBigDecimal(9, new BigDecimal(paidAmount));
            ps.setString(10, status);
            ps.setString(11, submitTime);
            ps.setString(12, reviewer);
            ps.setString(13, reviewTime);
            ps.setString(14, reviewComment);
            ps.setString(15, createdAt);
            ps.setString(16, updatedAt);
            ps.executeUpdate();
            long declarationId;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                declarationId = keys.getLong(1);
            }
            if (submitTime != null) {
                insertAudit(connection, declarationId, declarationNo, "提交", "纳税人提交申报", "纳税人", submitTime);
            }
            if (reviewer != null && reviewTime != null) {
                if (Statuses.DECLARATION_REJECTED.equals(status)) {
                    insertAudit(connection, declarationId, declarationNo, "驳回", reviewComment, reviewer, reviewTime);
                } else {
                    insertAudit(connection, declarationId, declarationNo, "通过", reviewComment, reviewer, reviewTime);
                }
            }
            if (Statuses.DECLARATION_PAID.equals(status) && paymentDate != null) {
                try (PreparedStatement payment = connection.prepareStatement(
                        "INSERT INTO payment(payment_no,declaration_id,amount,method,payment_date,status,operator,created_at) VALUES(?,?,?,?,?,?,?,?)")) {
                    payment.setString(1, declarationNo.replaceFirst("^SB", "JK"));
                    payment.setLong(2, declarationId);
                    payment.setBigDecimal(3, new BigDecimal(paidAmount));
                    payment.setString(4, "银行扣款");
                    payment.setString(5, paymentDate);
                    payment.setString(6, Statuses.PAYMENT_PAID);
                    payment.setString(7, "系统");
                    payment.setString(8, paymentDate);
                    payment.executeUpdate();
                }
            }
        }
    }

    private static void insertAudit(Connection connection, long declarationId, String declarationNo, String action,
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

    private static void seedInvoices(Connection connection) throws SQLException {
        long tp1 = idBy(connection, "taxpayer", "taxpayer_no", "91310000MA1K12345A");
        long tp2 = idBy(connection, "taxpayer", "taxpayer_no", "91310000MA1K67890B");
        long tp3 = idBy(connection, "taxpayer", "taxpayer_no", "92310000MA2K55555C");
        long tp4 = idBy(connection, "taxpayer", "taxpayer_no", "310101199001011234");
        Object[][] invoices = {
                {"FP20260903001", tp1, "增值税专用发票", "120000.00", "15600.00", "2026-09-03", Statuses.INVOICE_NORMAL, "软件服务费"},
                {"FP20260915002", tp2, "增值税普通发票", "80000.00", "10400.00", "2026-09-15", Statuses.INVOICE_NORMAL, "货物销售"},
                {"FP20261008003", tp3, "增值税普通发票", "35000.00", "4550.00", "2026-10-08", Statuses.INVOICE_NORMAL, "零售商品"},
                {"FP20261012004", tp1, "增值税专用发票", "210000.00", "27300.00", "2026-10-12", Statuses.INVOICE_NORMAL, "技术服务费"},
                {"FP20261020005", tp2, "增值税普通发票", "60000.00", "7800.00", "2026-10-20", Statuses.INVOICE_VOID, "开票信息有误，已作废"},
                {"FP20261026006", tp4, "增值税普通发票", "12000.00", "360.00", "2026-10-26", Statuses.INVOICE_NORMAL, "咨询服务"}
        };
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO invoice(invoice_no,taxpayer_id,invoice_type,amount,tax_amount,issue_date,status,remark,created_at) VALUES(?,?,?,?,?,?,?,?,?)")) {
            for (Object[] invoice : invoices) {
                ps.setString(1, (String) invoice[0]);
                ps.setLong(2, (Long) invoice[1]);
                ps.setString(3, (String) invoice[2]);
                ps.setBigDecimal(4, new BigDecimal((String) invoice[3]));
                ps.setBigDecimal(5, new BigDecimal((String) invoice[4]));
                ps.setString(6, (String) invoice[5]);
                ps.setString(7, (String) invoice[6]);
                ps.setString(8, (String) invoice[7]);
                ps.setString(9, (String) invoice[5] + " 10:00:00");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static long idBy(Connection connection, String table, String column, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM " + table + " WHERE " + column + " = ?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("演示数据缺少记录: " + table + "." + column + "=" + value);
                }
                return rs.getLong(1);
            }
        }
    }
}
