package com.example.qimo.resource;

import com.example.qimo.model.Invoice;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.User;
import com.example.qimo.service.InvoiceService;
import com.example.qimo.util.AuthContext;
import com.example.qimo.util.Statuses;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InvoiceResource {

    @GET
    public PageResult<Invoice> list(@QueryParam("keyword") String keyword,
                                    @QueryParam("status") String status,
                                    @QueryParam("page") Integer page,
                                    @QueryParam("size") Integer size,
                                    @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return InvoiceService.list(user, keyword, status, page, size);
    }

    @GET
    @Path("/{id}")
    public Invoice get(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return InvoiceService.get(id, user);
    }

    @POST
    public Invoice create(Invoice invoice, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return InvoiceService.create(invoice, user);
    }

    @PUT
    @Path("/{id}")
    public Invoice update(@PathParam("id") Long id, Invoice invoice, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return InvoiceService.update(id, invoice, user);
    }

    @POST
    @Path("/{id}/void")
    public Invoice voidInvoice(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return InvoiceService.voidInvoice(id, user);
    }

    @DELETE
    @Path("/{id}")
    public Map<String, Object> delete(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        InvoiceService.delete(id, user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "发票已删除");
        return result;
    }
}
