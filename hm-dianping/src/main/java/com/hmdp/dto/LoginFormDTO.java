package com.hmdp.dto;

import lombok.Data;

@Data
public class LoginFormDTO {
    private String phone;
    private String code;//验证码
    private String password;
}
