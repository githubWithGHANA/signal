package com.quantlab.client.controllers;

import com.quantlab.common.exception.ErrorDetail;
import com.quantlab.common.utils.staticstore.dropdownutils.ApiStatus;
import com.quantlab.signal.dto.AuthSendDTO;
import com.quantlab.common.dto.ClientDetailsDTO;
import com.quantlab.signal.dto.CookieDTO;
import com.quantlab.signal.service.AuthService;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.dto.MinMaxDto;
import com.quantlab.common.dto.WelcomeDto;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LogManager.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<AuthSendDTO>> getClientProfile(@Validated @RequestBody CookieDTO cookieDTO, HttpServletRequest request)  throws Exception {
        try {
            WelcomeDto welcomeDto = new WelcomeDto();
            welcomeDto.setClientId(cookieDTO.getClientId());
            welcomeDto.setTermsConditions(false);
            AuthSendDTO user = authService.fetchUserDetails(cookieDTO);
            Boolean saved = authService.updateLoginInfo(welcomeDto,request);
            log.info("Fetched user details for clientId: {}", cookieDTO.getClientId());
            if (user != null) {
                return ResponseEntity.ok(new ApiResponse<>(
                        (user.getLoggedInToday() ? "Logging in first time today"
                                : user.getNewUser() ? "A new user is created successfully"
                                : "Existing user fetched successfully"),
                        user
                ));
            } else {
                log.warn("Login failed for clientId: {}", cookieDTO.getClientId());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(
                        "Login Attempt Failed",
                        "Session Expired, Please login again to get new tokens",
                        null
                ));
            }
        }
        catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to check for User",
                    "Error fetching client details, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @GetMapping("/userDetails")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> getUserAuthConstantsByClientId(@RequestHeader String clientId) throws Exception {
        try {
            ClientDetailsDTO user = authService.fetchProfileDataByClientId(clientId);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                        "No user found",
                        null
                ));
            }
            return ResponseEntity.ok(new ApiResponse<>("User Exists", user));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to check for User",
                    "Error fetching user details",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

    @PostMapping("/saveProfile")
    public ResponseEntity<ApiResponse<ClientDetailsDTO>> getClientProfile(@RequestHeader String clientId
            ,@Validated @RequestBody MinMaxDto minMaxDto)  throws Exception {

        try {
            ClientDetailsDTO savedUser = authService.updateMinAndMaxValues(clientId, minMaxDto);

            return ResponseEntity.ok(new ApiResponse<>(
                    (savedUser != null?"Min and Max values saved successfully":"unable to find user"),
                    savedUser
            ));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to save Profile info",
                    "Error saving profile details",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

    @PostMapping("/welcome")
    public ResponseEntity<ApiResponse<AuthSendDTO>> welcomeProfile(@RequestBody WelcomeDto welcomeDto , HttpServletRequest request, @RequestHeader String clientId)  throws Exception {
        try {
            welcomeDto.setClientId(clientId);
            Boolean saved = authService.updateLoginInfo(welcomeDto,request);
            Boolean Token = authService.handleWelcomeAcknowledgement(welcomeDto, request);
            AuthSendDTO dto = authService.welcomeResponse(welcomeDto.getClientId());
            //Remove this during live token generation
//            authService.setUserTradingModeToForward(welcomeDto.getClientId());

            return ResponseEntity.ok(new ApiResponse<>(
                    (saved?" Welcome to IB Algo":""),
                    dto
            ));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch Welcome pop up",
                    "Failed to generate Token",
                    List.of(new ErrorDetail("ERROR_CODE","Failed to generate Token", null)) // Return error details
            ));
        }
    }

    // setExceptionDate is to add market on weekends based on the requirement, needs more refinement
    @PostMapping("/exceptiondate")
    public ResponseEntity<ApiResponse<String>> setExceptionDate(@RequestHeader("clientId") String clientId, @RequestParam String ExceptionDate) {

        try {
//            Boolean setExceptionDate = authService.setExceptionDate(clientId, ExceptionDate);
            if (false) {
                return ResponseEntity.ok(new ApiResponse<>(
                        "Successfully set Exception date to " + ExceptionDate,
                        null
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "Unable to set Exception date to " + ExceptionDate,
                        null
                ));
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Error when setting Exception date to " + ExceptionDate,
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }
}
