package com.quantlab.client.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.exception.ErrorDetail;
import com.quantlab.common.service.APILogsService;
import com.quantlab.signal.dto.AuthValidatorDTO;
import com.quantlab.signal.dto.CookieDTO;
import com.quantlab.signal.service.AuthService;
import com.quantlab.signal.service.GrpcService;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Component
public class ValidationFilter implements Filter {

   // private final RestTemplate restTemplate;
   private static final Logger logger = LoggerFactory.getLogger(ValidationFilter.class);

    @Value("${spring.profiles.active}")
    private String profile;


    public final AuthService authService;

    @Autowired
    private APILogsService apiLogsService;


    private Set<String> excludedApis = new HashSet<>();

    @Autowired
    public ValidationFilter(AuthService authService) {
        this.authService = authService;
        excludedApis.add("/ql/ws/open-positions-data");
    }
    private FilterConfig filterConfig;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        ContentCachingRequestWrapper cachingRequest = new ContentCachingRequestWrapper(httpRequest);
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("Access-Control-Allow-Origin", "*");
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization , token, otpsessionId, mobileNumber, clientId");

        if (isWebSocketHandshake(httpRequest)) {
//            logger.info("inside webSocket doFilter, before each call");
            chain.doFilter(cachingRequest, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }
     //   logger.info("inside validationFilter doFilter, before each call");
        if (!profile.equalsIgnoreCase("dev")) {

            String currentPath = httpRequest.getRequestURI();
            if (excludedApis.contains(currentPath)) {
                chain.doFilter(cachingRequest, response);
                return;
            }

            String token = httpRequest.getHeader("token");
            String otpsessionId = httpRequest.getHeader("otpsessionId");
            String mobileNumber = httpRequest.getHeader("mobileNumber");
            String clientId = httpRequest.getHeader("clientId");

            logger.info("Token: {}, OTP Session ID: {}, Mobile Number: {}, Client ID: {}", token, otpsessionId, mobileNumber, clientId);
            if (token == null || otpsessionId == null || mobileNumber == null || clientId == null
                    || token.isEmpty() || otpsessionId.isEmpty() || mobileNumber.isEmpty() || clientId.isEmpty()) {
                httpResponse.setHeader("Access-Control-Allow-Origin", "*");
                httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
                httpResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                httpResponse.setHeader("Pragma", "no-cache");
                httpResponse.setHeader("Expires", "0");

                // Missing token or user ID, reject the request
                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"errorCode\": \"11095\", \"message\": \"UNAUTHORIZED\"}");
                httpResponse.getWriter().flush();
                return;

            }
            CookieDTO cookieDTO = new CookieDTO();
            cookieDTO.setToken(token);
            cookieDTO.setOtpSessionId(otpsessionId);
            cookieDTO.setMobileNumber(mobileNumber);
            cookieDTO.setClientId(clientId);

            try {
                authService.validTokens(cookieDTO);// this method will throw error when failed
                if (authService.isRateLimited(clientId, currentPath)) { // to limit API calls per second
                    handleRateLimitExceeded(httpResponse, clientId, currentPath);
                    return;
                }
                chain.doFilter(cachingRequest, response);
                logAPIRequest(cachingRequest);
            } catch (Exception e) {
                logger.error("Auth error (raw): {}", e.getMessage());
                Map<String, Object> jsonResponse = errorMessageProcess(e.getMessage());
                ApiResponse<Void> failedResponse = new ApiResponse<>(
                        "Authentication failed: " + jsonResponse.get("code"),
                        jsonResponse.get("message").toString(),
                        List.of(new ErrorDetail(
                                jsonResponse.get("code").toString(),
                                jsonResponse.get("message").toString(),
                                null
                        ))
                );

                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json");
                new ObjectMapper().writeValue(httpResponse.getWriter(), failedResponse);
            }
        }else{
            chain.doFilter(cachingRequest, response);
            logAPIRequest(cachingRequest);
        }


    }

    private void logAPIRequest(HttpServletRequest httpRequest) throws IOException {
        apiLogsService.logRequest(httpRequest);
    }

    private Map<String, Object> errorMessageProcess(String errorMessage) {
        try {
            String json = errorMessage.split(",", 2)[1].split("],")[0];
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> parsedResponse = mapper.readValue(json, Map.class);

            String code = parsedResponse.containsKey("code") ?
                    parsedResponse.get("code").toString() : "11095";
            String message = parsedResponse.containsKey("message") ?
                    parsedResponse.get("message").toString() : "UNAUTHORIZED";

            return Map.of(
                    "code", code,
                    "message", message
            );
        }
        catch (Exception e) {
            logger.error("Failed to parse error message. Falling back to default.", e);
            return Map.of(
                    "code", "11095",
                    "message", "Authentication failed"
            );
        }
    }

    @Override
    public void destroy() {
        // Cleanup logic if needed
    }

    // Step 4: Call the external API to validate token and userId
    private boolean isValidToken(String token, String userId) {
        try {

            // Check if the response is OK and the body is true
//            return authController.getClientProfile();
            return true;
        } catch (Exception e) {
            // Log the error if necessary and consider the token invalid in case of any exception
            return false;
        }
    }
    private boolean isWebSocketHandshake(HttpServletRequest request) {
        // Check for the "Upgrade: websocket" header
        String upgradeHeader = request.getHeader("Upgrade");
        return "websocket".equalsIgnoreCase(upgradeHeader);
    }

    private void handleRateLimitExceeded(HttpServletResponse httpResponse, String clientId, String currentPath) throws IOException {
        logger.warn("Rate limit exceeded for clientId={}, path={}", clientId, currentPath);
        httpResponse.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT);
        httpResponse.setContentType("application/json");

        ApiResponse<Void> failedResponse = new ApiResponse<>(
                "429",
                "Too many requests. Please slow down.",
                List.of(new ErrorDetail("429", "Rate limit exceeded", null))
        );

        new ObjectMapper().writeValue(httpResponse.getWriter(), failedResponse);
    }

}
