package com.example.qimo.resource;

import com.example.qimo.model.PageResult;
import com.example.qimo.model.Payment;
import com.example.qimo.model.PaymentRequest;
import com.example.qimo.model.User;
import com.example.qimo.service.PaymentService;
import com.example.qimo.util.AuthContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

@Path("/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @GET
    public PageResult<Payment> list(@QueryParam("keyword") String keyword,
                                    @QueryParam("page") Integer page,
                                    @QueryParam("size") Integer size,
                                    @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return PaymentService.list(user, keyword, page, size);
    }

    @POST
    public Payment pay(PaymentRequest request, @Context HttpHeaders headers) {
        User user = AuthContext.require(headers);
        return PaymentService.pay(request, user);
    }
}
