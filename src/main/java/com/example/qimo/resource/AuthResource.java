package com.example.qimo.resource;

import com.example.qimo.model.LoginRequest;
import com.example.qimo.model.LoginResponse;
import com.example.qimo.model.PasswordRequest;
import com.example.qimo.model.User;
import com.example.qimo.service.AuthService;
import com.example.qimo.util.AuthContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @POST
    @Path("/login")
    public LoginResponse login(LoginRequest request) {
        return AuthService.login(request);
    }

    @POST
    @Path("/logout")
    public Map<String, Object> logout(@Context HttpHeaders headers) {
        AuthService.logout(AuthContext.token(headers));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "已安全退出");
        return result;
    }

    @GET
    @Path("/me")
    public User me(@Context HttpHeaders headers) {
        return AuthContext.require(headers);
    }

    @PUT
    @Path("/password")
    public Map<String, Object> changePassword(PasswordRequest request, @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        AuthService.changePassword(user, request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "密码修改成功");
        return result;
    }
}
