package com.example.qimo.resource;

import com.example.qimo.model.PageResult;
import com.example.qimo.model.TaxType;
import com.example.qimo.model.User;
import com.example.qimo.service.TaxTypeService;
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

@Path("/tax-types")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaxTypeResource {

    @GET
    public PageResult<TaxType> list(@QueryParam("keyword") String keyword,
                                    @QueryParam("status") String status,
                                    @QueryParam("page") Integer page,
                                    @QueryParam("size") Integer size,
                                    @Context HttpHeaders headers) {
        AuthContext.require(headers);
        return TaxTypeService.list(keyword, status, page, size);
    }

    @GET
    @Path("/options")
    public List<TaxType> options(@Context HttpHeaders headers) {
        AuthContext.require(headers);
        return TaxTypeService.options();
    }

    @POST
    public TaxType create(TaxType taxType, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        return TaxTypeService.create(taxType, user);
    }

    @PUT
    @Path("/{id}")
    public TaxType update(@PathParam("id") Long id, TaxType taxType, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        return TaxTypeService.update(id, taxType, user);
    }

    @DELETE
    @Path("/{id}")
    public Map<String, Object> delete(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        TaxTypeService.delete(id, user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "税种已删除");
        return result;
    }
}
