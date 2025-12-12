package com.shutu.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{8}$", message = "手机号格式错误")
    private String phone;

    @NotBlank
    private String password;
}