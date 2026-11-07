package com.smartpay.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("api/v1/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    @Operation(summary = "Get current user", description = "Returns the email of the currently authenticated user.")
    @GetMapping("/me")
    public String getCurrentUser(Authentication authentication){
        return "Authenticated user: " + authentication.getName();
    }
}

//Proceed to next step
//
//[INFO] BUILD SUCCESS
//[INFO] ------------------------------------------------------------------------
//        [INFO] Total time:  7.786 s
//[INFO] Finished at: 2026-11-04T07:29:47+05:30