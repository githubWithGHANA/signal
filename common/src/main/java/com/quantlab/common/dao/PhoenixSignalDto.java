package com.quantlab.common.dao;

public class PhoenixSignalDto {
    private Long id;
    private Long baseIndexPrice;
    private Integer currentAtm;

    public PhoenixSignalDto(Long id, Long baseIndexPrice, Integer currentAtm) {
        this.id = id;
        this.baseIndexPrice = baseIndexPrice;
        this.currentAtm = currentAtm;
    }

    public Long getId() {
        return id;
    }

    public Long getBaseIndexPrice() {
        return baseIndexPrice;
    }

    public Integer getCurrentAtm() {
        return currentAtm;
    }
}
