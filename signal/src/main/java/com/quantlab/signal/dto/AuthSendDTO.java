package com.quantlab.signal.dto;

import lombok.Data;

@Data
public class AuthSendDTO {

    private Long userId;
    private Boolean newUser;
    private Boolean loggedInToday;
    private Boolean isLive;
    private String bannerContent;
}
