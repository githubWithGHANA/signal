package com.quantlab.common.service;

import com.quantlab.common.entity.ApiAccessLog;
import com.quantlab.common.repository.ApiAccessLogRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class APILogsService {
    private static final Logger logger = LoggerFactory.getLogger(APILogsService.class);
    ArrayList<String> allowedApis = new ArrayList<>(Stream.of(
            // V2 APIs
            "/strategy/v2/deploy","/strategy/v2/oneclickdeploy",
            "/strategy/v2/undeploy","/strategy/v2/changemultiplier",
            "/strategy/v2/standby","/strategy/v2/unsubscribe",
            "/strategy/v2/exitstrategy","/strategy/v2/toLiveTrading",
            "/strategy/v2/toForwardTest",
            "/errormanagement/v2/manuallytraded","/errormanagement/v2/cancelled",
            "/errormanagement/v2/retry",
            // V1 APIs
            "diy/save","/errormanagement","strategy/undeploy","auth/saveProfile",
            "/strategy/deploy","/strategy/oneclickdeploy","/strategy/manualentry",
            "/strategy/undeploy","/strategy/exit","/strategy/exitall",
            "/strategy/changemultiplier","/strategy/pause","/strategy/standby",
            "/strategy/unsubscribe"
    ).toList());

    Map<String, String> apiMap = allowedApis.stream()
            .collect(Collectors.toMap(
                    uri -> uri,
                    uri -> uri.substring(uri.lastIndexOf("/") + 1)

            ));

    @Autowired
    ApiAccessLogRepository apiAccessLogRepository;

    @PostConstruct
    private void init() {
        apiMap.forEach((k, v) -> System.out.println(k + " => " + v));
    }

    public void logRequest(ServletRequest request1) throws IOException {
        try {
            HttpServletRequest request = (HttpServletRequest) request1;
            logger.info("Request URI: " + request.getRequestURI());
            if (isLoggable(request)) {
                logger.info("logging Request URI: " + request.getRequestURI() + " | Request Body: ");
                String requestBody = getRequestBody(request);
                ApiAccessLog log = new ApiAccessLog();
                log.setApiType(apiMap.get(request.getRequestURI()));
                log.setRequestApi(request.getRequestURI() + "?" + request.getQueryString());
                log.setApiAccessTime(Instant.now());
                log.setTenantId(request.getHeader("clientId"));
                log.setRequestIp(getClientIp(request));
                log.setUserAgent(request.getHeader("User-Agent"));
                log.setApiRequestBody(requestBody);
                log.setMachineId(request.getHeader("User-Agent"));
                saveApiAccessLog(log);
            }
        } catch (Exception e) {
            logger.error("Error while logging API access: ", e);
        }

    }

    @Async
    @Transactional
    public void saveApiAccessLog(ApiAccessLog log) {
        apiAccessLogRepository.save(log);

    }

    private boolean isLoggable(HttpServletRequest request) {

        String URI = request.getRequestURI();
        // Return true if any allowedApi is a substring of URI
        return allowedApis.stream().anyMatch(URI::contains);
    }

    private String getRequestBody(HttpServletRequest request) throws IOException {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] buf = wrapper.getContentAsByteArray();
            if(buf.length > 0) {
                System.out.println("Request Body: " + new String(buf, wrapper.getCharacterEncoding()));
                return new String(buf, wrapper.getCharacterEncoding());
            }
            System.out.println("Request Body: has no data");
        }

        return "";
    }
    private  String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }
}
