package com.quantlab.client.dto;

import lombok.Data;

@Data
public class SelectionMenuStringDto {

    private String key;

    private String Val;

    public SelectionMenuStringDto(String key, String Val){
        this.key = key;
        this.Val = Val;
    }
}
