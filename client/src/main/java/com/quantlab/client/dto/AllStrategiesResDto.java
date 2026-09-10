package com.quantlab.client.dto;


import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AllStrategiesResDto implements Serializable {

    private AllStrategyCategoryDto strategies;

    private DeployDropdownDto dropdownList;

}
