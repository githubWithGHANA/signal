package com.quantlab.client.controllers;
import com.quantlab.client.dto.AllStrategiesResDto;
import com.quantlab.client.dto.DiyDropDownDto;
import com.quantlab.client.dto.DiyReqDto;
import com.quantlab.client.service.DiyService;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.exception.ErrorDetail;
import com.quantlab.common.exception.custom.StrategyAlreadyLiveException;
import com.quantlab.common.utils.staticstore.dropdownutils.ApiStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/diy")
public class DiyController {

    private final DiyService diyService;

    public DiyController(DiyService diyService){
        this.diyService = diyService;
    }


    @GetMapping("/dropdowns")
    public ResponseEntity<ApiResponse<DiyDropDownDto>> getDropdownOptions() {
        DiyDropDownDto resList = diyService.getDiyDropdownList();
        if (resList != null) {
            return ResponseEntity.ok(new ApiResponse<>(
                    "Dropdown options retrieved successfully",
                    resList
            ));
        } else {
            List<ErrorDetail> errors = List.of(new ErrorDetail(
                    "NO_DROPDOWN_OPTIONS",
                    "No dropdown options found.",
                    null
            ));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "No dropdown options available.",
                    null
            ));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiyReqDto> getDiyStrategy(@PathVariable Long id){
        // need to check if the particular user is having the access for this particular record
        // need to add the conditions later
        return ResponseEntity.ok(new DiyReqDto());
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<AllStrategiesResDto>> saveDIYStrategy(@RequestHeader("clientId") String clientId, @RequestBody DiyReqDto diyReqDto) {
        try {
            AllStrategiesResDto allStrategiesResDto = new AllStrategiesResDto();
            if(diyReqDto.getStrategyId() != null){
                allStrategiesResDto = diyService.updateStrategy(clientId, diyReqDto);
            }else{
                allStrategiesResDto = diyService.saveStrategy(clientId, diyReqDto);
            }
                return ResponseEntity.ok(new ApiResponse<>(
                        "DIY strategy saved successfully",
                        allStrategiesResDto
                ));
        } catch (StrategyAlreadyLiveException e) {
            List<ErrorDetail> errors = List.of(new ErrorDetail(
                    "INTERNAL_SERVER_ERROR",
                    "Internal Server Error occurred. Please try again later.",
                    null
            ));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    e.getMessage(),
                    errors
            ));
        }
    }

}
