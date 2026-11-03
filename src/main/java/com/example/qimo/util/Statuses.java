package com.example.qimo.util;

public final class Statuses {

    public static final String DECLARATION_DRAFT = "草稿";
    public static final String DECLARATION_PENDING = "待审核";
    public static final String DECLARATION_APPROVED = "已通过";
    public static final String DECLARATION_REJECTED = "已驳回";
    public static final String DECLARATION_PAID = "已缴款";

    public static final String TAXPAYER_ACTIVE = "正常";
    public static final String TAXPAYER_SUSPENDED = "停业";
    public static final String TAXPAYER_CANCELLED = "注销";

    public static final String USER_ENABLED = "启用";
    public static final String USER_DISABLED = "停用";

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_AUDITOR = "AUDITOR";
    public static final String ROLE_TAXPAYER = "TAXPAYER";

    public static final String INVOICE_NORMAL = "正常";
    public static final String INVOICE_VOID = "作废";

    public static final String PAYMENT_PAID = "已缴款";

    private Statuses() {
    }
}
