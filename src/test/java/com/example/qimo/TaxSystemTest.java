package com.example.qimo;

import com.example.qimo.config.ApiException;
import com.example.qimo.config.Database;
import com.example.qimo.model.LoginRequest;
import com.example.qimo.model.Payment;
import com.example.qimo.model.PaymentRequest;
import com.example.qimo.model.TaxDeclaration;
import com.example.qimo.model.TaxType;
import com.example.qimo.model.User;
import com.example.qimo.service.AuditService;
import com.example.qimo.service.AuthService;
import com.example.qimo.service.DashboardService;
import com.example.qimo.service.DeclarationService;
import com.example.qimo.service.PaymentService;
import com.example.qimo.service.TaxpayerService;
import com.example.qimo.service.TaxTypeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaxSystemTest {

    @BeforeAll
    public static void setup() {
        System.setProperty("tax.db.url", "jdbc:h2:mem:taxsystem_test;DB_CLOSE_DELAY=-1");
        Database.ensureInitialized();
    }

    @Test
    public void declarationWorkflow() {
        User admin = login("admin", "admin123");
        User auditor = login("auditor", "audit123");
        User taxpayer = login("taxpayer", "demo123");

        assertTrue(TaxpayerService.list(null, null, 1, 20).getTotal() >= 6);
        List<TaxType> taxTypes = TaxTypeService.options();
        assertTrue(taxTypes.size() >= 4);
        TaxType vat = taxTypes.get(0);

        TaxDeclaration declaration = new TaxDeclaration();
        declaration.setTaxTypeId(vat.getId());
        declaration.setPeriodStart("2026-10-01");
        declaration.setPeriodEnd("2026-10-31");
        declaration.setTaxableAmount(new BigDecimal("100000.00"));
        TaxDeclaration created = DeclarationService.create(declaration, taxpayer);
        assertEquals("草稿", created.getStatus());
        assertEquals(0, new BigDecimal("13000.00").compareTo(created.getTaxAmount()));

        DeclarationService.submit(created.getId(), taxpayer);
        assertEquals("待审核", DeclarationService.findRaw(created.getId()).getStatus());

        AuditService.review(created.getId(), true, "审核通过", auditor);
        assertEquals("已通过", DeclarationService.findRaw(created.getId()).getStatus());

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setDeclarationId(created.getId());
        paymentRequest.setMethod("银行扣款");
        Payment payment = PaymentService.pay(paymentRequest, taxpayer);
        assertEquals(0, new BigDecimal("13000.00").compareTo(payment.getAmount()));
        assertEquals("已缴款", DeclarationService.findRaw(created.getId()).getStatus());

        Map<String, Object> stats = DashboardService.stats(admin);
        assertNotNull(stats.get("monthlyTax"));
        assertEquals(6, ((List<?>) stats.get("monthlyTax")).size());
        assertEquals(userRole(admin), stats.get("role"));
    }

    @Test
    public void rejectedDeclarationNeedsComment() {
        User auditor = login("auditor", "audit123");
        User taxpayer = login("taxpayer", "demo123");
        TaxType vat = TaxTypeService.options().get(0);

        TaxDeclaration declaration = new TaxDeclaration();
        declaration.setTaxTypeId(vat.getId());
        declaration.setPeriodStart("2026-09-01");
        declaration.setPeriodEnd("2026-09-30");
        declaration.setTaxableAmount(new BigDecimal("200000.00"));
        TaxDeclaration created = DeclarationService.create(declaration, taxpayer);
        DeclarationService.submit(created.getId(), taxpayer);

        assertThrows(ApiException.class,
                () -> AuditService.review(created.getId(), false, "", auditor));
        AuditService.review(created.getId(), false, "凭证不完整", auditor);
        assertEquals("已驳回", DeclarationService.findRaw(created.getId()).getStatus());
    }

    private static User login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        User user = AuthService.login(request).getUser();
        assertNotNull(user);
        return user;
    }

    private static Object userRole(User user) {
        return user.getRole();
    }
}
