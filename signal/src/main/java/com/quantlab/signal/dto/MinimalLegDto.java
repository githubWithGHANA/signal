package com.quantlab.signal.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MinimalLegDto {
    private Long id;
    private Long exchangeInstrumentId;
    private String status;
    private String buySellFlag;

    public MinimalLegDto() {
    }

    @Override
    public String toString() {
        return "MinimalLegDto{" +
                "id=" + id +
                ", exchangeInstrumentId=" + exchangeInstrumentId +
                ", status='" + status + '\'' +
                ", buySellFlag='" + buySellFlag + '\'' +
                '}';
    }
}

