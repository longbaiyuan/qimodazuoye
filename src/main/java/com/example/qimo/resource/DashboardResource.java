package com.example.qimo.resource;

import com.example.qimo.model.User;
import com.example.qimo.service.DashboardService;
import com.example.qimo.util.AuthContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @GET
    public Map<String, Object> stats(@Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return DashboardService.stats(user);
    }
}
