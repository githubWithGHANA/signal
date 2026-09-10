package com.quantlab.common.dto;


import lombok.Data;

import java.io.Serializable;

@Data
public class AllStrategiesResDto implements Serializable {

    private AllStrategyCategoryDto strategies;

    private DeployDropdownDto dropdownList;

}
