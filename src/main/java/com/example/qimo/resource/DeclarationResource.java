package com.example.qimo.resource;

import com.example.qimo.model.PageResult;
import com.example.qimo.model.TaxDeclaration;
import com.example.qimo.model.User;
import com.example.qimo.service.DeclarationService;
import com.example.qimo.util.AuthContext;
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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/declarations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeclarationResource {

    @GET
    public PageResult<TaxDeclaration> list(@QueryParam("keyword") String keyword,
                                           @QueryParam("status") String status,
                                           @QueryParam("taxTypeId") Long taxTypeId,
                                           @QueryParam("taxpayerId") Long taxpayerId,
                                           @QueryParam("startDate") String startDate,
                                           @QueryParam("endDate") String endDate,
                                           @QueryParam("minAmount") BigDecimal minAmount,
                                           @QueryParam("maxAmount") BigDecimal maxAmount,
                                           @QueryParam("page") Integer page,
                                           @QueryParam("size") Integer size,
                                           @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return DeclarationService.search(user, keyword, status, taxTypeId, taxpayerId, startDate, endDate,
                minAmount, maxAmount, page, size);
    }

    @GET
    @Path("/unpaid")
    public List<TaxDeclaration> unpaid(@Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return DeclarationService.unpaid(user);
    }

    @GET
    @Path("/summary")
    public Map<String, Object> summary(@QueryParam("keyword") String keyword,
                                       @QueryParam("status") String status,
                                       @QueryParam("taxTypeId") Long taxTypeId,
                                       @QueryParam("taxpayerId") Long taxpayerId,
                                       @QueryParam("startDate") String startDate,
                                       @QueryParam("endDate") String endDate,
                                       @QueryParam("minAmount") BigDecimal minAmount,
                                       @QueryParam("maxAmount") BigDecimal maxAmount,
                                       @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return DeclarationService.summary(user, keyword, status, taxTypeId, taxpayerId, startDate, endDate,
                minAmount, maxAmount);
    }

    @GET
    @Path("/{id}")
    public TaxDeclaration get(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return DeclarationService.get(id, user);
    }

    @POST
    public TaxDeclaration create(TaxDeclaration declaration, @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return DeclarationService.create(declaration, user);
    }

    @PUT
    @Path("/{id}")
    public TaxDeclaration update(@PathParam("id") Long id, TaxDeclaration declaration,
                                 @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return DeclarationService.update(id, declaration, user);
    }

    @DELETE
    @Path("/{id}")
    public Map<String, Object> delete(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        DeclarationService.delete(id, user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "申报记录已删除");
        return result;
    }

    @POST
    @Path("/{id}/submit")
    public TaxDeclaration submit(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return DeclarationService.submit(id, user);
    }
}
