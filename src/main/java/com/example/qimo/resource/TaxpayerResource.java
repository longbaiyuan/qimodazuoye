package com.example.qimo.resource;

import com.example.qimo.config.ApiException;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.Taxpayer;
import com.example.qimo.model.User;
import com.example.qimo.service.TaxpayerService;
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
import java.util.List;
import java.util.Map;

@Path("/taxpayers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaxpayerResource {

    @GET
    public PageResult<Taxpayer> list(@QueryParam("keyword") String keyword,
                                     @QueryParam("status") String status,
                                     @QueryParam("page") Integer page,
                                     @QueryParam("size") Integer size,
                                     @Context HttpHeaders headers) {
        AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return TaxpayerService.list(keyword, status, page, size);
    }

    @GET
    @Path("/options")
    public List<Taxpayer> options(@Context HttpHeaders headers) {
        AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return TaxpayerService.options();
    }

    @GET
    @Path("/{id}")
    public Taxpayer get(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        if (AuthContext.isTaxpayer(user) && !id.equals(user.getTaxpayerId())) {
            throw ApiException.forbidden("无权查看其他纳税人信息");
        }
        return TaxpayerService.get(id);
    }

    @POST
    public Taxpayer create(Taxpayer taxpayer, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return TaxpayerService.create(taxpayer, user);
    }

    @PUT
    @Path("/{id}")
    public Taxpayer update(@PathParam("id") Long id, Taxpayer taxpayer, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return TaxpayerService.update(id, taxpayer, user);
    }

    @DELETE
    @Path("/{id}")
    public Map<String, Object> delete(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        TaxpayerService.delete(id, user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "纳税人已删除");
        return result;
    }
}
