package com.quantlab.common.utils.staticstore.dropdownutils;

public enum OmsType {
    OMS_TR("OMS-TR"),
    OMS_XTS("OMS-XTS");

    private final String omsType;

    OmsType(String omsType) {
        this.omsType = omsType;
    }

    public String getOmsType() {
        return omsType;
    }
}
