package com.quantlab.client.dto;

import lombok.Data;

@Data
public class SelectionMenuLongDto {

    private Long key;

    private String Val;

    public SelectionMenuLongDto(Long key, String Val){
        this.key = key;
        this.Val = Val;
    }
}
