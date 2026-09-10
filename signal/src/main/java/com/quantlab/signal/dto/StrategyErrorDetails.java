package com.quantlab.signal.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StrategyErrorDetails {

    String timeStamp;
    List<String> Description = new ArrayList<>();
    String status;
}

