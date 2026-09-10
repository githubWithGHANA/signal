package com.quantlab.signal.dto;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserDataDownloadDto {
    String clientId;
    String name;
    String email;
    String phoneNumber;
    Integer totalStrategies;
    Integer liveStrategiesCount;
    List<String> liveStrategies;
    Integer forwardStrategiesCount;
    List<String> forwardStrategies;
    LocalDateTime lastLogin;
    Instant createdAt;
    Long userId;
}
