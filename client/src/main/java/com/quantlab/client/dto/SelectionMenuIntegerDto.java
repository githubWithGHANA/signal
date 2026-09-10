package com.quantlab.client.dto;

import lombok.Data;

@Data
public class SelectionMenuIntegerDto {

    private Integer key;

    private String Val;

    public SelectionMenuIntegerDto(Integer key, String Val){
        this.key = key;
        this.Val = Val;
    }
}
