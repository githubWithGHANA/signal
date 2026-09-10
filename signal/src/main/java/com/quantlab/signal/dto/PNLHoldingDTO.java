package com.quantlab.signal.dto;

import com.quantlab.signal.dto.redisDto.PNLLegTableDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PNLHoldingDTO {

    // only for testing purpose
    private String uniqueKey;
    private String userID;
    private Double todaysPAndL;
    private Double overAllUserPAndL;
    private Double postionalPAndL;
    private Double intradayPAndL;
    private Double deployedCapital;
    private PNLHeaderDTO liveHeaders;
    private PNLHeaderDTO forwardHeaders;
    private ArrayList<PNLLegTableDTO> strategyLegs = new ArrayList<>();

    public PNLHoldingDTO(PNLHoldingDTO other) {
        if (other == null)
            return;

        this.uniqueKey = other.uniqueKey;
        this.userID = other.userID;
        this.todaysPAndL = other.todaysPAndL;
        this.overAllUserPAndL = other.overAllUserPAndL;
        this.postionalPAndL = other.postionalPAndL;
        this.intradayPAndL = other.intradayPAndL;
        this.deployedCapital = other.deployedCapital;

        this.liveHeaders = (other.liveHeaders != null) ? new PNLHeaderDTO(other.liveHeaders) : null;
        this.forwardHeaders = (other.forwardHeaders != null) ? new PNLHeaderDTO(other.forwardHeaders) : null;

        this.strategyLegs = new ArrayList<>();
        if (other.strategyLegs != null) {
            for (PNLLegTableDTO leg : other.strategyLegs) {
                this.strategyLegs.add(new PNLLegTableDTO(leg));
            }
        }
    }

    public void setStrategyLegs(ArrayList<StrategyLegTableDTO> strategyLegTableDTOs) {
        this.strategyLegs = new ArrayList<>();
        for (StrategyLegTableDTO legDto : strategyLegTableDTOs) {
            this.strategyLegs.add(new PNLLegTableDTO(legDto));
        }
    }
}

