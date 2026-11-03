package com.example.qimo.resource;

import com.example.qimo.model.AuditRecord;
import com.example.qimo.model.PageResult;
import com.example.qimo.model.ReviewRequest;
import com.example.qimo.model.TaxDeclaration;
import com.example.qimo.model.User;
import com.example.qimo.service.AuditService;
import com.example.qimo.service.DeclarationService;
import com.example.qimo.util.AuthContext;
import com.example.qimo.util.Statuses;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

@Path("/audits")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuditResource {

    @GET
    @Path("/pending")
    public PageResult<TaxDeclaration> pending(@QueryParam("keyword") String keyword,
                                              @QueryParam("page") Integer page,
                                              @QueryParam("size") Integer size,
                                              @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return DeclarationService.search(user, keyword, Statuses.DECLARATION_PENDING, null, null,
                null, null, null, null, page, size);
    }

    @GET
    @Path("/records")
    public PageResult<AuditRecord> records(@QueryParam("declarationId") Long declarationId,
                                           @QueryParam("keyword") String keyword,
                                           @QueryParam("page") Integer page,
                                           @QueryParam("size") Integer size,
                                           @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return AuditService.records(declarationId, keyword, page, size, user);
    }

    @POST
    @Path("/{id}/approve")
    public TaxDeclaration approve(@PathParam("id") Long id, ReviewRequest request,
                                  @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return AuditService.review(id, true, request == null ? null : request.getComment(), user);
    }

    @POST
    @Path("/{id}/reject")
    public TaxDeclaration reject(@PathParam("id") Long id, ReviewRequest request,
                                 @Context HttpHeaders headers) {
        User user = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN, Statuses.ROLE_AUDITOR);
        return AuditService.review(id, false, request == null ? null : request.getComment(), user);
    }
}
