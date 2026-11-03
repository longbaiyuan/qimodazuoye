package com.example.qimo.config;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private static volatile boolean initialized = false;

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        ensureInitialized();
        return DriverManager.getConnection(url(), "sa", "");
    }

    public static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        String configured = System.getProperty("tax.db.url", "").trim();
        String url = configured.isEmpty() ? defaultUrl() : configured;
        if (!url.startsWith("jdbc:h2:mem")) {
            File dir = new File(System.getProperty("user.home"), ".qimo-tax");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("无法创建数据目录: " + dir.getAbsolutePath());
            }
        }
        try {
            Class.forName("org.h2.Driver");
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                createSchema(connection);
                SeedData.seed(connection);
            }
            initialized = true;
        } catch (Exception e) {
            throw new IllegalStateException("数据库初始化失败: " + e.getMessage(), e);
        }
    }

    public static String url() {
        String configured = System.getProperty("tax.db.url", "").trim();
        return configured.isEmpty() ? defaultUrl() : configured;
    }

    private static String defaultUrl() {
        String home = System.getProperty("user.home").replace('\\', '/');
        return "jdbc:h2:file:" + home + "/.qimo-tax/taxdb;DB_CLOSE_ON_EXIT=FALSE";
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS sys_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(64) NOT NULL UNIQUE," +
                    "password VARCHAR(128) NOT NULL," +
                    "display_name VARCHAR(64) NOT NULL," +
                    "user_role VARCHAR(20) NOT NULL," +
                    "taxpayer_id BIGINT," +
                    "status VARCHAR(20) NOT NULL," +
                    "created_at VARCHAR(32) NOT NULL" +
                    ")");
            statement.execute("CREATE TABLE IF NOT EXISTS taxpayer (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "taxpayer_no VARCHAR(32) NOT NULL UNIQUE," +
                    "name VARCHAR(128) NOT NULL," +
                    "taxpayer_type VARCHAR(32) NOT NULL," +
                    "legal_person VARCHAR(64)," +
                    "id_card VARCHAR(32)," +
                    "phone VARCHAR(32)," +
                    "email VARCHAR(128)," +
                    "address VARCHAR(255)," +
                    "industry VARCHAR(64)," +
                    "registration_date VARCHAR(16)," +
                    "tax_office VARCHAR(64)," +
                    "status VARCHAR(20) NOT NULL," +
                    "created_at VARCHAR(32) NOT NULL," +
                    "updated_at VARCHAR(32)" +
                    ")");
            statement.execute("CREATE TABLE IF NOT EXISTS tax_type (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "code VARCHAR(32) NOT NULL UNIQUE," +
                    "name VARCHAR(64) NOT NULL," +
                    "rate DECIMAL(10,4) NOT NULL," +
                    "description VARCHAR(255)," +
                    "status VARCHAR(20) NOT NULL," +
                    "created_at VARCHAR(32) NOT NULL" +
                    ")");
            statement.execute("CREATE TABLE IF NOT EXISTS tax_declaration (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "declaration_no VARCHAR(40) NOT NULL UNIQUE," +
                    "taxpayer_id BIGINT NOT NULL," +
                    "tax_type_id BIGINT NOT NULL," +
                    "period_start VARCHAR(16) NOT NULL," +
                    "period_end VARCHAR(16) NOT NULL," +
                    "taxable_amount DECIMAL(18,2) NOT NULL," +
                    "tax_rate DECIMAL(10,4) NOT NULL," +
                    "tax_amount DECIMAL(18,2) NOT NULL," +
                    "paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                    "status VARCHAR(20) NOT NULL," +
                    "submit_time VARCHAR(32)," +
                    "reviewer VARCHAR(64)," +
                    "review_time VARCHAR(32)," +
                    "review_comment VARCHAR(500)," +
                    "created_at VARCHAR(32) NOT NULL," +
                    "updated_at VARCHAR(32)" +
                    ")");
            statement.execute("CREATE TABLE IF NOT EXISTS audit_record (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "declaration_id BIGINT NOT NULL," +
                    "declaration_no VARCHAR(40) NOT NULL," +
                    "action VARCHAR(32) NOT NULL," +
                    "comment VARCHAR(500)," +
                    "auditor VARCHAR(64)," +
                    "created_at VARCHAR(32) NOT NULL" +
                    ")");
            statement.execute("CREATE TABLE IF NOT EXISTS payment (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "payment_no VARCHAR(40) NOT NULL UNIQUE," +
                    "declaration_id BIGINT NOT NULL," +
                    "amount DECIMAL(18,2) NOT NULL," +
                    "method VARCHAR(32) NOT NULL," +
                    "payment_date VARCHAR(32) NOT NULL," +
                    "status VARCHAR(20) NOT NULL," +
                    "operator VARCHAR(64)," +
                    "created_at VARCHAR(32) NOT NULL" +
                    ")");
            statement.execute("CREATE TABLE IF NOT EXISTS invoice (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "invoice_no VARCHAR(40) NOT NULL UNIQUE," +
                    "taxpayer_id BIGINT NOT NULL," +
                    "invoice_type VARCHAR(32) NOT NULL," +
                    "amount DECIMAL(18,2) NOT NULL," +
                    "tax_amount DECIMAL(18,2) NOT NULL," +
                    "issue_date VARCHAR(16) NOT NULL," +
                    "status VARCHAR(20) NOT NULL," +
                    "remark VARCHAR(255)," +
                    "created_at VARCHAR(32) NOT NULL" +
                    ")");
            statement.execute("CREATE TABLE IF NOT EXISTS operation_log (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(64)," +
                    "module VARCHAR(32) NOT NULL," +
                    "action VARCHAR(32) NOT NULL," +
                    "detail VARCHAR(500)," +
                    "created_at VARCHAR(32) NOT NULL" +
                    ")");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_declaration_taxpayer ON tax_declaration(taxpayer_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_declaration_status ON tax_declaration(status)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_payment_declaration ON payment(declaration_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_declaration ON audit_record(declaration_id)");
        }
    }
}
