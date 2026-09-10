package com.quantlab.signal.service;

import com.quantlab.common.dao.EndpointProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.quantlab.common.utils.staticstore.AppConstants;

@Service
public class XtsService {


    private static final Logger log = LogManager.getLogger(XtsService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    EndpointProperties endpointProperties;

    public String login(String appKey, String secretKey) {
        String url = endpointProperties.getXtsUrl() + AppConstants.LOGIN_INTERACTIVE;

        // Create request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("secretKey", secretKey);
        requestBody.put("appKey", appKey);
        requestBody.put("source", "WEBAPI");

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);


        log.info("XTS login url: {} requestBody: {} " ,url , requestBody);
        // Create HTTP request
        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);

        // Send request and receive response
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            JSONObject jsonResponse = new JSONObject(response.getBody());
            JSONObject result = jsonResponse.getJSONObject("result");
            String authToken = result.getString("token");
            return authToken;
        }
        return null;
    }

}
