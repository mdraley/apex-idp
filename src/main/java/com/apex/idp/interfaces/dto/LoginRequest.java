package com.apex.idp.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO for capturing user login credentials from the /api/v1/auth/login endpoint.
 */
@Getter
@Setter
public class LoginRequest {

    private String username;
    private String password;

}