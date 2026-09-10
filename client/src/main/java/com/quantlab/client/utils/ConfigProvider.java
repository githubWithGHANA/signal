package com.quantlab.client.utils;

import org.springframework.stereotype.Component;

@Component
public abstract class ConfigProvider {


    public static Integer niftyLotSize;
    public static Integer bankNiftyLotSize;
    public static Integer finNiftyLotSize;

    public void LoadConfig(Integer niftyLotSize, Integer bankNiftyLotSize, Integer finNiftyLotSize){
        ConfigProvider.niftyLotSize = niftyLotSize;
        ConfigProvider.bankNiftyLotSize = bankNiftyLotSize;
        ConfigProvider.finNiftyLotSize = finNiftyLotSize;
    }
}
