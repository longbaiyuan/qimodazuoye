package com.example.qimo.resource;

import com.example.qimo.model.OperationLog;
import com.example.qimo.model.PageResult;
import com.example.qimo.service.LogService;
import com.example.qimo.util.AuthContext;
import com.example.qimo.util.Statuses;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

@Path("/logs")
@Produces(MediaType.APPLICATION_JSON)
public class LogResource {

    @GET
    public PageResult<OperationLog> list(@QueryParam("keyword") String keyword,
                                         @QueryParam("module") String module,
                                         @QueryParam("page") Integer page,
                                         @QueryParam("size") Integer size,
                                         @Context HttpHeaders headers) {
        AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        return LogService.list(keyword, module, page, size);
    }
}
