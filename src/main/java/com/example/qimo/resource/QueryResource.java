package com.example.qimo.resource;

import com.example.qimo.model.PageResult;
import com.example.qimo.model.TaxDeclaration;
import com.example.qimo.model.User;
import com.example.qimo.service.DeclarationService;
import com.example.qimo.util.AuthContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.util.Map;

@Path("/query")
@Produces(MediaType.APPLICATION_JSON)
public class QueryResource {

    @GET
    @Path("/declarations")
    public PageResult<TaxDeclaration> declarations(@QueryParam("keyword") String keyword,
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
}
