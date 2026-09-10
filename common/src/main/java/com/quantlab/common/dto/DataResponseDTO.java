package com.quantlab.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DataResponseDTO {
    @JsonProperty(value = "clientDetails" , required = false) // Match JSON key
    private ClientResponseDTO clientResponseDTO;

    public ClientDetailsDTO processData(){
        ClientDetailsDTO clientDetailsDTO = new ClientDetailsDTO();
        clientDetailsDTO.setClientId(this.clientResponseDTO.getClientId());
        clientDetailsDTO.setClientName(this.clientResponseDTO.getClientName());
        clientDetailsDTO.setAddress(this.clientResponseDTO.getAddress());
        clientDetailsDTO.setMobileNo(this.clientResponseDTO.getMobileNumber());
        clientDetailsDTO.setEmailId(this.clientResponseDTO.getEmailId());
        clientDetailsDTO.setXtsClient(this.clientResponseDTO.getXtsClient());
        if (this.clientResponseDTO.getXtsClient()) {
            clientDetailsDTO.setXTSAppKey(this.clientResponseDTO.getAppKey());
            clientDetailsDTO.setXTSSecretKey(this.clientResponseDTO.getSecretKey());
        } else {
            clientDetailsDTO.setCugUser(this.clientResponseDTO.isCugUser());
            clientDetailsDTO.setTrToken(this.clientResponseDTO.getTrToken());
            clientDetailsDTO.setUserSessionId(this.clientResponseDTO.getUserSessionId());
            clientDetailsDTO.setJsessionId(this.clientResponseDTO.getJsessionId());
        }

        return clientDetailsDTO;
    }
}
