package com.quantlab.common.dto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Builder
@Getter
@Setter
public class TokenLogDto {
    private String clientId;
    private String userName;
    private Instant welcomeAcknowledgedTime;
    private boolean welcomeAccepted;
    private String machineId;

}

