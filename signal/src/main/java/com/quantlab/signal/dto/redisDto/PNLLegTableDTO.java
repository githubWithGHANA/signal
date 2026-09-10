package com.quantlab.signal.dto.redisDto;

import com.quantlab.signal.dto.LegHoldingDTO;
import com.quantlab.signal.dto.PNLSocketRowData;
import com.quantlab.signal.dto.StrategyLegTableDTO;
import lombok.Data;

import java.util.ArrayList;

@Data
public class PNLLegTableDTO {

    private Long strategyId;
//    private String strategyStatus;
    private Double strategyMTM;
    private Double indexCurrentPrice;
    private ArrayList<PNLSocketRowData> data = new ArrayList<>();

    public PNLLegTableDTO(StrategyLegTableDTO strategyLegTableDTO){
        this.strategyId = strategyLegTableDTO.getStrategyId();
//        this.strategyStatus = strategyLegTableDTO.getStrategyStatus();
        this.strategyMTM = strategyLegTableDTO.getStrategyMTM();
        this.indexCurrentPrice = strategyLegTableDTO.getIndexCurrentPrice();
        for (LegHoldingDTO dto : strategyLegTableDTO.getData()) {
            this.data.add(new PNLSocketRowData().mapToPNLSocketRowData(dto));
        }
    }

    public PNLLegTableDTO(PNLLegTableDTO other) {
        this.strategyId = other.strategyId;
//        this.strategyStatus = other.strategyStatus;
        this.strategyMTM = other.strategyMTM;
        this.indexCurrentPrice = other.indexCurrentPrice;

        this.data = new ArrayList<>();
        if (other.data != null) {
            for (PNLSocketRowData row : other.data) {
                this.data.add(new PNLSocketRowData(row)); // assumes PNLSocketRowData has a copy constructor
            }
        }
    }
}
