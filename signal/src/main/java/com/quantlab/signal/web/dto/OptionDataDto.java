package com.quantlab.signal.web.dto;


import lombok.Data;

@Data
public class OptionDataDto {

    private String id;
    private String name;
    private String ceExchangeInstrumentId;
    private String ceExchangeInstrumentName;
    private String peExchangeInstrumentId;
    private String peExchangeInstrumentName;
    private String cePrice;
    private String pePrice;
}
