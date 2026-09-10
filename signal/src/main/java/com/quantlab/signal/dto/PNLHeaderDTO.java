package com.quantlab.signal.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PNLHeaderDTO {
    Double todaysPAndL = 0.0;
    Double OverAllUserPAndL = 0.0;
    Double positionalPAndL = 0.0;
    Double intradayPAndL = 0.0;
    Double deployedCapital = 0.0;

    public PNLHeaderDTO(PNLHeaderDTO pnlHeaderDTO) {
        if(pnlHeaderDTO == null) return;
        this.todaysPAndL = pnlHeaderDTO.getTodaysPAndL();
        this.OverAllUserPAndL = pnlHeaderDTO.getOverAllUserPAndL();
        this.positionalPAndL = pnlHeaderDTO.getPositionalPAndL();
        this.intradayPAndL = pnlHeaderDTO.getIntradayPAndL();
        this.deployedCapital = pnlHeaderDTO.getDeployedCapital();
    }
}
