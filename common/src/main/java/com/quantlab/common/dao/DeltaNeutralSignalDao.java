package com.quantlab.common.dao;

import lombok.Data;
import lombok.Getter;

@Getter
@Data
public class DeltaNeutralSignalDao {
    private Long id;
    private Integer entryUnderlingPrice;

    public DeltaNeutralSignalDao(Long id, Integer entryUnderlingPrice) {
        this.id = id;
        this.entryUnderlingPrice = entryUnderlingPrice;
    }

}
