package com.example.qimo.resource;

import com.example.qimo.model.PageResult;
import com.example.qimo.model.User;
import com.example.qimo.service.UserService;
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

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @GET
    public PageResult<User> list(@QueryParam("keyword") String keyword,
                                 @QueryParam("role") String role,
                                 @QueryParam("page") Integer page,
                                 @QueryParam("size") Integer size,
                                 @Context HttpHeaders headers) {
        AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        return UserService.list(keyword, role, page, size);
    }

    @POST
    public User create(User user, @Context HttpHeaders headers) {
        User operator = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        return UserService.create(user, operator);
    }

    @PUT
    @Path("/{id}")
    public User update(@PathParam("id") Long id, User user, @Context HttpHeaders headers) {
        User operator = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        return UserService.update(id, user, operator);
    }

    @DELETE
    @Path("/{id}")
    public Map<String, Object> delete(@PathParam("id") Long id, @Context HttpHeaders headers) {
        User operator = AuthContext.requireRole(headers, Statuses.ROLE_ADMIN);
        UserService.delete(id, operator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "用户已删除");
        return result;
    }
}
