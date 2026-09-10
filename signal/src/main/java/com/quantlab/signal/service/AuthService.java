package com.quantlab.signal.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.quantlab.common.dao.EndpointProperties;
import com.quantlab.common.dao.StrategyIdAndSourceIdDAO;
import com.quantlab.common.dto.*;
import com.quantlab.common.emailService.EmailService;
import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.UserNotFoundException;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.UserType;
import com.quantlab.common.utils.staticstore.dropdownutils.ExecutionTypeMenu;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.TradingMode;
import com.quantlab.signal.dto.AuthSendDTO;
import com.quantlab.signal.dto.CookieDTO;
import com.quantlab.signal.dto.TrLoginDto;
import com.quantlab.signal.dto.redisDto.InterActiveTokensDTO;
import com.quantlab.signal.service.redisService.InterActiveTokensRepository;
import com.quantlab.signal.service.redisService.SyntheticPriceRepository;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.sheduler.BodSchedule;
import com.quantlab.signal.utils.AuthUtils;
import com.quantlab.signal.utils.TestMail;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.staticdata.StaticStore.EXCEPTION_DATE;

@Service
public class AuthService {
    private static final Logger logger = LogManager.getLogger(AuthService.class);

    @Autowired
    AdminRepository adminRepository;

    @Autowired
    userRoleRepository userRoleRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    StrategyCategoryRepository strategyCategoryRepository;

    @Autowired
    UserAuthConstantsRepository userAuthConstantsRepository;

    @Autowired
    AppUserLogInfoRepository appUserLogInfoRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    EntryDaysRespository entryDaysRespository;

    @Autowired
    UIConstantsRepository uiConstantsRepository;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    BodSchedule bodSchedule;

    @Autowired
    XtsService xtsService;

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    TokenLogInfoRepository tokenLogInfoRepository;

    @Autowired
    TestMail testMail;

    @Value("${spring.profiles.active}")
    private String profile;

    @Autowired
    EndpointProperties endpointProperties;

    @Autowired
    EmailService emailService;

    @Autowired
    InterActiveTokensRepository interActiveTokensRepository;

    @Autowired
    LoginMarginCheckService loginMarginCheckService;

    @Autowired
    CugUsersService cugUsersService;

    public ApiResponseDTO validateUser(CookieDTO authValidatorDTO, String endpointURL) throws Exception {
        String url = endpointURL + authValidatorDTO.getClientId();
        // headers
        HttpHeaders headers = new HttpHeaders();
        headers.set(TOKEN, authValidatorDTO.getToken());
        headers.set(OTP_SESSION_ID, authValidatorDTO.getOtpSessionId());
        headers.set(MOBILE_NUMBER, authValidatorDTO.getMobileNumber());
        headers.set(APP_ID, endpointProperties.getAppID());
        headers.set(APP_KEY, endpointProperties.getAppKey());

        HttpEntity<String> entity = new HttpEntity<>(headers);

        HttpClient httpClient = HttpClient.newBuilder().build();
        httpClient.executor();

        RestTemplate restTemplate = new RestTemplate();
        logger.info("validateUser request for user {} endpoint {} — request = method={}, url={}, headers={}", authValidatorDTO.getUserId(), endpointURL, HttpMethod.GET, url, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        logger.info("validateUser response for user {} endpoint {} — response = {}", authValidatorDTO.getUserId(), endpointURL, response);
        ObjectMapper objectMapper = new ObjectMapper();
        ApiResponseDTO apiResponseDTO = objectMapper.readValue(response.getBody(), ApiResponseDTO.class);
        logger.info("validateUser response for user {} : {}",authValidatorDTO.getUserId(), apiResponseDTO);
        if (apiResponseDTO.getCode().equals(AUTH_SUCCESS)) {
            return apiResponseDTO;
        }
        throw new Exception(response.toString());
    }

    @Transactional
    public AuthSendDTO fetchUserDetails(CookieDTO cookieDTO) {

        UserAuthConstants selectedClient;
        LocalDate today = LocalDate.now();
        AuthSendDTO userAuth =  new AuthSendDTO();
        try {
            UIConstants noUserTokenUiConstants = uiConstantsRepository.findByCode(NO_USER_TOKEN);
            selectedClient = checkUser(cookieDTO.getClientId());

            if (selectedClient != null && !shouldValidateProfileToday(selectedClient, today)) {
                logger.info("Skipping profile validation for clientId: {}. Already validated on {}", cookieDTO.getClientId(), selectedClient.getPreviousLoggedinTime());
                selectedClient.setToken(cookieDTO.getToken());
                selectedClient.setOtpSessionId(cookieDTO.getOtpSessionId());
                selectedClient.setMobileNumber(cookieDTO.getMobileNumber());
                existingUserToCreteNewStrategy(selectedClient.getClientId());
                restoreTokenIfRequired(selectedClient);
                selectedClient = userAuthConstantsRepository.save(selectedClient);
                populateAuthSendDTO(userAuth, selectedClient, noUserTokenUiConstants, today, false);
            } else {
                ApiResponseDTO apiResponseDTO = validateUser(cookieDTO, endpointProperties.getProfile());
                ClientDetailsDTO clientDetailsDTO = apiResponseDTO.processResponse(apiResponseDTO.getData());

                if (Objects.equals(apiResponseDTO.getCode(), AUTH_SUCCESS)) {
                    logger.info("1if (Objects.equals(apiResponseDTO.getCode(), AUTH_SUCCESS)) is true clientId: {}, userId: {}, ", cookieDTO.getClientId(), clientDetailsDTO.getUserId());
                    if (selectedClient != null) {
                        AppUser user = selectedClient.getAppUser();
                        if (user == null) {
                            throw new UserNotFoundException("User Not Found");
                        }
                        if (user.getTenentId() == null || user.getTenentId().isEmpty()) {
                            user.setTenentId(cookieDTO.getClientId());
                            appUserRepository.save(user);
                        }
                        populateExistingClientFromProfile(selectedClient, cookieDTO, clientDetailsDTO);
                        existingUserToCreteNewStrategy(selectedClient.getClientId());
                        selectedClient.setPreviousLoggedinTime(LocalDateTime.now());
                        selectedClient.setToken(cookieDTO.getToken());
                        selectedClient.setMobileNumber(cookieDTO.getMobileNumber());
                        restoreTokenIfRequired(selectedClient);
                        selectedClient = userAuthConstantsRepository.save(selectedClient);
                        logger.info("2 old user,  clientId: {}, token: {}, ", cookieDTO.getClientId(), selectedClient.getTrToken());
                        populateAuthSendDTO(userAuth, selectedClient, noUserTokenUiConstants, today, false);
                    } else {
                        userAuth.setNewUser(true);
                        userAuth.setLoggedInToday(false);
                        selectedClient = createNewClientFromProfile(cookieDTO, clientDetailsDTO);
                        selectedClient = saveNewUserData(selectedClient);
                        logger.info("3 new user,  clientId: {}, token: {}, ", cookieDTO.getClientId(), selectedClient.getTrToken());
                        populateAuthSendDTO(userAuth, selectedClient, noUserTokenUiConstants, today, true);
                    }
                    logger.info("4 User Auth Constants fetched for clientId: {}, userId: {}, token: {}", cookieDTO.getClientId(), userAuth.getUserId(), selectedClient.getTrToken());
                }
            }
        } catch (Exception e) {
            if (e.getMessage().contains(OK_200))
                return null;
            logger.error("################ error fetching user details {}", e.getMessage());
            throw new RuntimeException("Error fetching user details", e);
        }
        logger.info("5 User cookie Constants fetched for clientId: {}, userId: {}, isLive: {}", cookieDTO.getClientId(), userAuth.getUserId(), userAuth.getIsLive());
        logger.info("6 User cookie Constants fetched for token: {}, toString: {}", cookieDTO.getToken(), userAuth.toString());
        return userAuth;

    }

    private boolean shouldValidateProfileToday(UserAuthConstants selectedClient, LocalDate today) {
        return selectedClient.getPreviousLoggedinTime() == null
                || !selectedClient.getPreviousLoggedinTime().toLocalDate().equals(today);
    }

    private void populateAuthSendDTO(AuthSendDTO userAuth, UserAuthConstants selectedClient,
                                     UIConstants noUserTokenUiConstants, LocalDate today, boolean isNewUser) {
        userAuth.setNewUser(isNewUser);
        userAuth.setLoggedInToday(selectedClient.getLastWelcomeAckTime() == null
                || !selectedClient.getLastWelcomeAckTime().toLocalDate().equals(today));
        userAuth.setUserId(selectedClient.getAppUser().getId());
        userAuth.setIsLive(TradingMode.LIVE.getKey().equalsIgnoreCase(selectedClient.getUserTradingMode()));
        if (!TradingMode.LIVE.getKey().equalsIgnoreCase(selectedClient.getUserTradingMode())) {
            userAuth.setBannerContent(noUserTokenUiConstants.getDescription());
            logger.info("User Auth Constants fetched for clientId: {}, userId: {}, token: {}", selectedClient.getClientId(), userAuth.getUserId(), selectedClient.getTrToken());
        }
    }

    private void populateExistingClientFromProfile(UserAuthConstants selectedClient, CookieDTO cookieDTO,
                                                   ClientDetailsDTO clientDetailsDTO) {
        selectedClient.setOtpSessionId(cookieDTO.getOtpSessionId());
        selectedClient.setEmailId(clientDetailsDTO.getEmailId());
        selectedClient.setName(clientDetailsDTO.getClientName());
        selectedClient.setAddress(clientDetailsDTO.getAddress());
        selectedClient.setXtsClient(clientDetailsDTO.getXtsClient());

        if (clientDetailsDTO.getXtsClient()) {
            selectedClient.setXtsAppKey(clientDetailsDTO.getXTSAppKey());
            selectedClient.setXtsSecretKey(clientDetailsDTO.getXTSSecretKey());
        } else {
            selectedClient.setIsCugUser(clientDetailsDTO.isCugUser());
            selectedClient.setTrToken(clientDetailsDTO.getTrToken());
            if (clientDetailsDTO.getUserSessionId() != null) {
                selectedClient.setUserSessionId(clientDetailsDTO.getUserSessionId());
            } else {
                logger.warn("Upstream API returned null userSessionId for clientId: {}, retaining existing value", cookieDTO.getClientId());
            }
            if (clientDetailsDTO.getJsessionId() != null) {
                selectedClient.setJsessionId(clientDetailsDTO.getJsessionId());
            } else {
                logger.warn("Upstream API returned null jsessionId for clientId: {}, retaining existing value", cookieDTO.getClientId());
            }
            if (selectedClient.getJsessionId() != null) {
                TrLoginDto productAlias = fetchProductAlias(selectedClient);
                if (productAlias != null) {
                    selectedClient.setProductAlias(productAlias.getProductAlias());
                    selectedClient.setBranchId(productAlias.getBranchId());
                    selectedClient.setBrokerName(productAlias.getBrokerName());
                }
            } else {
                logger.warn("Skipping fetchProductAlias for existing user clientId: {} - jsessionId is null", cookieDTO.getClientId());
            }
        }
    }

    private UserAuthConstants createNewClientFromProfile(CookieDTO cookieDTO, ClientDetailsDTO clientDetailsDTO) {
        UserAuthConstants selectedClient = new UserAuthConstants();
        selectedClient.setClientId(cookieDTO.getClientId());
        selectedClient.setEmailId(clientDetailsDTO.getEmailId());
        selectedClient.setName(clientDetailsDTO.getClientName());
        selectedClient.setAddress(clientDetailsDTO.getAddress());
        selectedClient.setXtsClient(clientDetailsDTO.getXtsClient());
        if (clientDetailsDTO.getXtsClient()) {
            selectedClient.setXtsAppKey(clientDetailsDTO.getXTSAppKey());
            selectedClient.setXtsSecretKey(clientDetailsDTO.getXTSSecretKey());
        } else {
            selectedClient.setIsCugUser(clientDetailsDTO.isCugUser());
            selectedClient.setTrToken(clientDetailsDTO.getTrToken());
            selectedClient.setUserSessionId(clientDetailsDTO.getUserSessionId());
            selectedClient.setJsessionId(clientDetailsDTO.getJsessionId());
            if (selectedClient.getJsessionId() != null) {
                TrLoginDto productAlias = fetchProductAlias(selectedClient);
                if (productAlias != null) {
                    selectedClient.setProductAlias(productAlias.getProductAlias());
                    selectedClient.setBranchId(productAlias.getBranchId());
                    selectedClient.setBrokerName(productAlias.getBrokerName());
                }
            } else {
                logger.warn("Skipping fetchProductAlias for new user clientId: {} - jsessionId is null", cookieDTO.getClientId());
            }
        }

        selectedClient.setOtpSessionId(cookieDTO.getOtpSessionId());
        selectedClient.setPreviousLoggedinTime(LocalDateTime.now());
        selectedClient.setToken(cookieDTO.getToken());
        selectedClient.setMobileNumber(cookieDTO.getMobileNumber());
        selectedClient.setMaxLoss(DEFAULT_MIN_MAX_VALUE);
        selectedClient.setMinProfit(DEFAULT_MIN_MAX_VALUE);
        selectedClient.setUserTradingMode(TradingMode.FORWARD.getKey());
        return selectedClient;
    }

    public void existingUserToCreteNewStrategy(String clientId) {
        try {
            List<StrategyIdAndSourceIdDAO> strategyIdAndSourceIdDAOS = strategyRepository.findAllStrategyIDsAndSourceId(clientId);
            List<StrategyIdAndSourceIdDAO> strategyIdsAdmin= strategyRepository.findAllStrategyIDsAndSourceIdByAdminId();

            Set<Long> list2Ids = strategyIdAndSourceIdDAOS.stream()
                    .map(StrategyIdAndSourceIdDAO::getSourceId)
                    .collect(Collectors.toSet());

            List<StrategyIdAndSourceIdDAO> adminResultList = strategyIdsAdmin.stream()
                    .filter(obj -> !list2Ids.contains(obj.getId()))
                    .toList();
            if (!adminResultList.isEmpty()) {
                AppUser appUser = appUserRepository.findByTenentId(clientId.toString()).orElseThrow(() -> new UserNotFoundException("User Not Found"));
                setDefaultStrategys(appUser, true, adminResultList.stream()
                        .map(StrategyIdAndSourceIdDAO::getId)
                        .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            logger.error("Error checking existing user: {}", e.getMessage());
        }
    }

    private TrLoginDto fetchProductAlias(UserAuthConstants userAuth) {
        try {
            String baseUrl = "";
            if (profile.equalsIgnoreCase("uatprod"))
                baseUrl = userAuth.getIsCugUser() ? UAT_CUG_URL : UAT_NON_CUG_URL;
            else
                baseUrl = userAuth.getIsCugUser() ? CUG_URL : NON_CUG_URL;
            String productAliasEndpoint = "/DefaultLogin";
            logger.info("fetchProductAlias called for user: {}, baseUrl: {}, productAliasEndpoint: {}", userAuth.getClientId(), baseUrl, productAliasEndpoint);
            String jsessionId = AuthUtils.decryptData(userAuth.getJsessionId());
            String trToken = AuthUtils.decryptData(userAuth.getTrToken());
            String clientId = userAuth.getClientId();


            String jData = URLEncoder.encode("{\"uid\":\"" + clientId + "\"}", StandardCharsets.UTF_8);
            String urlString = baseUrl + productAliasEndpoint
                    + "?jData=" + jData
                    + "&jKey=" + trToken;

            System.out.println("----------- REQUEST -----------");
            System.out.println("URL: " + urlString);
            System.out.println("Cookie Header: JSESSIONID=" + jsessionId);
            System.out.println("Request Body: (empty)");

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
//            conn.setRequestProperty("Cookie", "JSESSIONID=" + jsessionId);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(0);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            System.out.println("----------- RESPONSE -----------");
            System.out.println("HTTP Status Code: " + responseCode);

            if (responseCode != 200) {
                logger.error("Failed to fetch alias, response code: {}", responseCode);
                return null;
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                System.out.println("Raw Response Body:");
                System.out.println(response.toString());

                // Parse response JSON
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.toString());

                if ("Ok".equalsIgnoreCase(root.path("stat").asText())) {
                    TrLoginDto trLoginDto = new TrLoginDto();
                    String alias = root.path("s_prdt_ali").asText();
                    String brokername = root.path("brkname").asText();
                    String brokerId = root.path("brnchid").asText();
                    trLoginDto.setProductAlias(alias);
                    trLoginDto.setBrokerName(brokername);
                    trLoginDto.setBranchId(brokerId);

                    System.out.println("Extracted s_prdt_ali: " + alias);
                    return trLoginDto;
                } else {
                    System.out.println("stat is not Ok: " + root.path("stat").asText());
                }
            }

        } catch (Exception e) {
            logger.error("Error fetching product alias: {}", e.getMessage(), e);
            logger.error("Error fetching product JsessionId: {}  , Trtoken : {}   ClientId : {}  cugUser : {}", userAuth.getJsessionId() , userAuth.getTrToken() , userAuth.getClientId(), userAuth.getIsCugUser());
        }
        return null;
    }




    public UserAuthConstants saveNewUserData(UserAuthConstants selectedClient) {
        try {
            AppUser appUser = createAppUser(selectedClient);
            appUser.setUserName(selectedClient.getName());
            appUser = appUserRepository.save(appUser);
            selectedClient.setAppUser(appUser);
            selectedClient = userAuthConstantsRepository.save(selectedClient);
            setDefaultStrategys(appUser, false, new ArrayList<Long>());
        } catch (Exception e) {
            logger.error("unable to save client details = {}", e.getMessage());
        }
        return selectedClient;
    }

    private void setDefaultStrategys(AppUser appUser, boolean partial, List<Long>strategyIds) {

        try {
            List<Strategy> defaultStrategys = new ArrayList<>();
            if (partial) {
                defaultStrategys = strategyRepository.findByIdIn(strategyIds);
            } else {
                defaultStrategys = strategyRepository.findDefaultStrategys();
            }
            Hibernate.initialize(defaultStrategys);
            // need to discuss what should be the default admin id for each user
            UserAdmin userAdmin = adminRepository.findById(1);
            List<EntryDays> allEntryDays = entryDaysRespository.findAll();
//            StrategyCategory sc = strategyCategoryRepository.getReferenceById(1l);
            for (Strategy fetchedStrategy: defaultStrategys){

                Hibernate.initialize(fetchedStrategy.getEntryDetails());
                Hibernate.initialize(fetchedStrategy.getExitDetails());
                List<StrategyLeg> newStrategyLegs = new ArrayList<>();

                Strategy savingStrategy = new Strategy();
                EntryDetails entryDetails = new EntryDetails();
                ExitDetails exitDetails = new ExitDetails();

                BeanUtils.copyProperties(fetchedStrategy, savingStrategy);
                BeanUtils.copyProperties(fetchedStrategy.getEntryDetails(), entryDetails);
                BeanUtils.copyProperties(fetchedStrategy.getExitDetails(), exitDetails);

                savingStrategy.setId(null);
                savingStrategy.setSignals(null);
                savingStrategy.setAppUser(appUser);
                savingStrategy.setUserAdmin(userAdmin);
                savingStrategy.setStrategyLeg(null);
                savingStrategy.setStatus(Status.INACTIVE.getKey());
                savingStrategy.setSourceId(fetchedStrategy.getId());
                entryDetails.setId(null);
                exitDetails.setId(null);
                entryDetails.setStrategy(savingStrategy);
                exitDetails.setStrategy(savingStrategy);
                savingStrategy.setEntryDetails(entryDetails);
                savingStrategy.setExitDetails(exitDetails);
                savingStrategy.setSubscription("N");
//                savingStrategy.setStrategyCategory(sc);
                for (StrategyLeg leg : fetchedStrategy.getStrategyLeg()){
                    StrategyLeg copyLeg = new StrategyLeg();
                    BeanUtils.copyProperties(leg, copyLeg);
                    copyLeg.setId(null);
                    copyLeg.setAppUser(appUser);
                    copyLeg.setUserAdmin(appUser.getAdmin());
                    copyLeg.setStatus(Status.ACTIVE.getKey());
                    copyLeg.setStrategy(savingStrategy);
                    newStrategyLegs.add(copyLeg);
                }
                savingStrategy.setStrategyLeg(newStrategyLegs);
                savingStrategy.setSignalCount(0);
                savingStrategy = strategyRepository.save(savingStrategy);
                entryDetails.setStrategy(savingStrategy);
                entryDetails.setEntryDays(allEntryDays);
            }

        } catch (Exception e) {
            logger.error("error creating default Strategies for user {}, {}", appUser.getUserId(), e.getMessage());
        }
    }

    private UserAuthConstants checkUser(String clientId) {
        try {
            Optional<UserAuthConstants> fetchedClient = userAuthConstantsRepository.findByClientId(clientId);
            if (fetchedClient.isPresent()) {
                return fetchedClient.get();
            }
        } catch (Exception e) {
            logger.error("unable to check clientID in DB{}", e.getMessage());
            throw new RuntimeException("unable to check clientID in DB", e);
        }
        return null;
    }

    public AppUser createAppUser(UserAuthConstants selectedClient) {

        try {
            AppUser appUser = new AppUser();
            //this is for Admin we need to change based on the role in future
            UserRole userRole = userRoleRepository.getReferenceById(1L);
            UserAdmin userAdmin= adminRepository.findById(1);
            // Set mandatory fields with provided values
            appUser.setCreatedBy(SYSTEM);  // Default "system" if createdBy is null
            appUser.setUpdatedBy(SYSTEM);  // Default updatedBy to createdBy if null

            appUser.setUserName(selectedClient.getName());
            appUser.setStatus(Status.ACTIVE.getKey());  // Default status to "active" if null
            appUser.setInvestment(ZERO_LONG);  // Default investment to 0 if null
            appUser.setCurrentValue(ZERO_LONG);  // Default currentValue to 0 if null
            appUser.setTodayProfitLoss(ZERO_LONG);  // Default to 0 if null
            appUser.setOverallProfitLoss(ZERO_LONG);  // Default to 0 if null
            appUser.setUserRole(userRole);
            appUser.setAdmin(userAdmin);
            appUser.setTenentId(selectedClient.getClientId());
            return appUser;
        } catch (Exception e) {
            logger.error("unable to create AppUser {}", e.getMessage());
            throw new RuntimeException("unable to create AppUser", e);
        }
    }

    public ClientDetailsDTO fetchProfileDataByClientId(String clientId){
        try {
            return userAuthConstantsRepository.findByClientId(clientId)
                    .map(this::createClientDTO)
                    .orElse(new ClientDetailsDTO());
        }catch (Exception e){
            throw new RuntimeException("Something went wrong", e);
        }
    }

    public ClientDetailsDTO updateMinAndMaxValues(String clientId, MinMaxDto minMaxDto){

        try{
            Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(clientId);
            if (userAuthConstantsOptional.isEmpty()) {
                throw new Exception("client info not found for clientId : " + clientId);
            }
            UserAuthConstants userAuthConstants = userAuthConstantsOptional.get();
            if (minMaxDto.getMinProfit() != null)
                userAuthConstants.setMinProfit((long) (minMaxDto.getMinProfit()*AMOUNT_MULTIPLIER));

            if (minMaxDto.getMaxLoss() != null)
                userAuthConstants.setMaxLoss((long) (minMaxDto.getMaxLoss()*AMOUNT_MULTIPLIER));

            userAuthConstantsRepository.save(userAuthConstants);
            return createClientDTO(userAuthConstants);
        } catch (RuntimeException e) {
            logger.error("unable to save min profit = "+minMaxDto.getMinProfit()+", maxLoss = "+minMaxDto.getMaxLoss());

            throw new RuntimeException("unable to store the MaxLoss and MinProfit data");
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong", e);
        }
    }

    @Transactional
    public Boolean updateLoginInfo(WelcomeDto welcomeDto , HttpServletRequest request){

        try{
            String authToken = "";
            String userAgent = request.getHeader("User-Agent");
            String machineIp = getClientIp(request);

            Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(welcomeDto.getClientId());
            if (userAuthConstantsOptional.isEmpty()) {
                throw new Exception("client info not found for clientId : " + welcomeDto.getClientId());
            }
            UserAuthConstants userAuthConstants = userAuthConstantsOptional.get();
            AppUserLogInfo appUserLogInfo = new AppUserLogInfo();
            Hibernate.initialize(userAuthConstants.getAppUser());
            appUserLogInfo.setAppUser(userAuthConstants.getAppUser());
            appUserLogInfo.setLoggedinTime(Instant.now());
            appUserLogInfo.setMechineId(machineIp);
            appUserLogInfo.setUserAgent(userAgent);
            appUserLogInfoRepository.save(appUserLogInfo);

            return true;
        } catch (RuntimeException e) {
//            e.printStackTrace();
            throw new RuntimeException("unable to store the User Login data data" + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong", e);
        }
    }

    @Transactional
    public Boolean handleWelcomeAcknowledgement(WelcomeDto welcomeDto, HttpServletRequest request) {
        String clientId = welcomeDto.getClientId();
        Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(clientId);
        if (userAuthConstantsOptional.isEmpty()) {
            return false;
        }

        UserAuthConstants userAuthConstants = userAuthConstantsOptional.get();
        if (!welcomeDto.isTermsConditions()) {
            removeInteractiveTokensInRedis(userAuthConstants);
            userAuthConstants.setUserTradingMode(TradingMode.FORWARD.getKey());
            userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
            TokenLogInfo tokenLogInfo = new TokenLogInfo();
            tokenLogInfo.setAcknowledgementType(DECLINE_TOKEN_GENERATION);
            tokenLogInfo.setMachineId(getClientIp(request));
            tokenLogInfo.setAppUser(userAuthConstants.getAppUser());
            tokenLogInfo.setUserAgent(request.getHeader("User-Agent"));
            tokenLogInfo.setWelcomeAcknowledgedTime(Instant.now());
            tokenLogInfoRepository.save(tokenLogInfo);
            strategyRepository.convertPaperTradingPreActiveStrategiesToActive(userAuthConstants.getAppUser().getId());
            userAuthConstantsRepository.save(userAuthConstants);
            return true;
        }
        int userPreActiveCount = convertPreActiveStrategiesToActive(userAuthConstants.getAppUser());

        if (userAuthConstants.getXtsClient()) {
            try {
                //commented to move to prod without xts

                logger.info("Generating XTS token for user: {}", clientId);
                if (generateXtsToken(userAuthConstants, request)) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("XTS token generation failed, falling back to forward testing : {}",e.getMessage());
                logTokenEvent(
                        userAuthConstants,
                        request,
                        UserType.XTS.getKey(),
                        ACCEPT_TOKEN_GENERATION,
                        null,
                        XTS_TOKEN_GENERATION_FAILURE,
                        false
                );

            }
            userAuthConstants.setUserTradingMode(TradingMode.FORWARD.getKey());
            userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
            userAuthConstantsRepository.save(userAuthConstants);
            return false;
        } else {
            try {
                validateTrTokens(userAuthConstants);

                userAuthConstants.setUserTradingMode(TradingMode.LIVE.getKey());
                userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());

                logTokenEvent(
                        userAuthConstants,
                        request,
                        UserType.TR.getKey(),
                        ACCEPT_TOKEN_GENERATION,
                        userAuthConstants.getTrToken(),
                        TR_TOKEN_VALIDATION_SUCCESS,
                        true
                );
                if (LocalTime.now().isAfter(LocalTime.of(8, 0)) && LocalTime.now().isBefore(LocalTime.of(15, 30))) {
                    logger.info("#### storeInteractiveTokensInRedis called for user: {}", userAuthConstants.getClientId());
                    storeInteractiveTokensInRedis(userAuthConstants);
                    userAuthConstantsRepository.save(userAuthConstants);
                    sendUserToMarginCheck(userAuthConstants);
                }
                return true;
            } catch (Exception e) {
                logger.error("TR token validation failed", e);
                logTokenEvent(
                        userAuthConstants,
                        request,
                        UserType.TR.getKey(),
                        ACCEPT_TOKEN_GENERATION,
                        null,
                        TR_TOKEN_VALIDATION_FAILURE + e.getMessage(),
                        false
                );


                userAuthConstants.setUserTradingMode(TradingMode.FORWARD.getKey()); // fallback
                userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
                userAuthConstantsRepository.save(userAuthConstants);

                return false;
            }
        }
    }

    @Transactional
    private int convertPreActiveStrategiesToActive(AppUser appUser) {
        try {
            return strategyRepository.convertAllPreActiveStrategiesToActive(appUser.getId());
        } catch (Exception e) {
            logger.error("Error converting pre-active strategies to active for user {}: {}", appUser.getUserId(), e.getMessage());
        }
        return 0;
    }

    private void restoreTokenIfRequired(UserAuthConstants selectedClient) {
        try {
            if (selectedClient == null) {
                logger.warn("restoreTokenIfRequired: selectedClient is null. Skipping restoration.");
                return;
            }

            // Check if user acknowledged today
            boolean hasAckToday = selectedClient.getLastWelcomeAckTime() != null &&
                    selectedClient.getLastWelcomeAckTime().toLocalDate().equals(LocalDate.now());

            // Check if user accepted welcome
            boolean isLiveMode = TradingMode.LIVE.getKey().equalsIgnoreCase(selectedClient.getUserTradingMode());

            String redisKey = "INTERACTIVE_" + selectedClient.getClientId();

            if (!hasAckToday) {
                logger.debug("restoreTokenIfRequired: No acknowledgement today for clientId: {}, skipping.", selectedClient.getClientId());
                return;
            }

            // Skip if user intentionally revoked token (is in FORWARD mode)
            if (!isLiveMode) {
                logger.info("restoreTokenIfRequired: User is in FORWARD mode (revoked token). No restoration needed for clientId: {}.", selectedClient.getClientId());
                return;
            }

            boolean tokenExists = interActiveTokensRepository.exists(redisKey);

            if (!tokenExists) {
                storeInteractiveTokensInRedis(selectedClient);
                logger.info("restoreTokenIfRequired: Token missing in Redis for LIVE user {}, attempting restore...", selectedClient.getClientId());
            }
            else {
                updateSessionTokens(selectedClient);
            }
        } catch (Exception ex) {
            logger.error("restoreTokenIfRequired: Failed to restore token for clientId: {} - {}",
                    selectedClient != null ? selectedClient.getClientId() : "N/A",
                    ex.getMessage(), ex);
        }
    }

    private void updateSessionTokens(UserAuthConstants selectedClient) {
        try {
            InterActiveTokensDTO dto = interActiveTokensRepository.find(selectedClient.getClientId());
            dto.setOtpSessionId(selectedClient.getOtpSessionId());
            dto.setLoginToken(selectedClient.getToken());
            interActiveTokensRepository.save(selectedClient.getClientId(), dto);
        }catch (Exception e){
            logger.info("error when saving the session tokens for:{}, exception ={}", selectedClient.getClientId(), e.getMessage());
        }
    }


    public Boolean generateXtsToken(UserAuthConstants userAuthConstants, HttpServletRequest request) {
        try {
            if (userAuthConstants.getXtsAppKey() == null || userAuthConstants.getXtsSecretKey() == null) {
                return false;
            }

            String decryptedAppKey = AuthUtils.decryptData(userAuthConstants.getXtsAppKey());
            String decryptedSecret = AuthUtils.decryptData(userAuthConstants.getXtsSecretKey());
            String xtsToken = xtsService.login(decryptedAppKey, decryptedSecret);

            userAuthConstants.setXtsToken(xtsToken);
            userAuthConstants.setUserTradingMode(TradingMode.LIVE.getKey());
            userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());

            logTokenEvent(
                    userAuthConstants,
                    request,
                    UserType.XTS.getKey(),
                    ACCEPT_TOKEN_GENERATION,
                    xtsToken,
                    XTS_TOKEN_GENERATION_SUCCESS,
                    true
            );

            userAuthConstantsRepository.save(userAuthConstants);
            if (LocalTime.now().isAfter(LocalTime.of(8, 0)) && LocalTime.now().isBefore(LocalTime.of(15, 30))) {
                storeInteractiveTokensInRedis(userAuthConstants);
                sendUserToMarginCheck(userAuthConstants);
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException("XTS token generation failed", e);
        }
    }

    public void sendUserToMarginCheck(UserAuthConstants userAuthConstants) {

        logger.info("#### sendUserToMarginCheck called for user: {}", userAuthConstants.getClientId());
        Long currentActiveStrategies = strategyRepository.findCountOfUserStrategiesByStatusAndExecutionMode(userAuthConstants.getAppUser().getId(), Status.ACTIVE.getKey(), ExecutionTypeMenu.LIVE_TRADING.getKey());
        logger.info("#### currentActiveStrategies: {}", currentActiveStrategies);
        if (currentActiveStrategies > 0) {
            if (userAuthConstants.getXtsClient()) {
                logger.info("#### XTS Client margin check sent");
                loginMarginCheckService.sendXTSMarginLogin(userAuthConstants);
            } else {
                if (userAuthConstants.getIsCugUser()) {
                    loginMarginCheckService.sendTRNMMarginLogin(userAuthConstants);
                    logger.info("#### TR NM Client margin check sent");

                }
                else {
                    loginMarginCheckService.sendTRSTTMarginLogin(userAuthConstants);
                    logger.info("#### TR STT Client margin check sent");
                }
            }
        }
    }

    public void storeInteractiveTokensInRedis(UserAuthConstants userAuthConstants) {
        try {
            InterActiveTokensDTO interActiveTokensDTO = new InterActiveTokensDTO();
            if (userAuthConstants.getXtsClient()) {

                String xtsToken = userAuthConstants.getXtsToken();
                String clientId = userAuthConstants.getClientId();
                interActiveTokensDTO.setClientId(clientId);
                interActiveTokensDTO.setToken(xtsToken);
                interActiveTokensDTO.setNumber(userAuthConstants.getMobileNumber());
            }else {
                String trToken = userAuthConstants.getTrToken();
                String jSessionId = userAuthConstants.getJsessionId();
                String userSessionId = userAuthConstants.getUserSessionId();
                String clientId = userAuthConstants.getClientId();
                interActiveTokensDTO.setClientId(clientId);
                interActiveTokensDTO.setToken(trToken);
                interActiveTokensDTO.setJSessionId(jSessionId);
                interActiveTokensDTO.setNumber(userAuthConstants.getMobileNumber());
                interActiveTokensDTO.setUserSessionId(userSessionId);
                interActiveTokensDTO.setOtpSessionId(userAuthConstants.getOtpSessionId());
                interActiveTokensDTO.setLoginToken(userAuthConstants.getToken());
            }

            if (interActiveTokensDTO.getToken() != null && interActiveTokensDTO.getClientId() != null) {
                interActiveTokensRepository.save(interActiveTokensDTO.getClientId(), interActiveTokensDTO);
                logger.info("Stored  token in Redis for clientId: {}", interActiveTokensDTO.getClientId());
            } else {
                logger.warn("token is null or empty for clientId: {}", interActiveTokensDTO.getClientId());
            }
        } catch (Exception e) {
            logger.error("Error storing XTS token in Redis: {}", e.getMessage());
        }
    }

    public void removeInteractiveTokensInRedis(UserAuthConstants userAuthConstants) {
        try {
            String key = "INTERACTIVE_" + userAuthConstants.getClientId();
            Boolean cleared = interActiveTokensRepository.delete(key);
            if (cleared)
                logger.info("Removed token from Redis for clientId: {}, key: {}", userAuthConstants.getClientId(), key);
            else
                logger.warn("No token found in Redis for clientId: {}, key: {}", userAuthConstants.getClientId(), key);

        } catch (Exception e) {
            logger.error("Error removing token from Redis: {}", e.getMessage());
        }
    }

    private void validateTrTokens(UserAuthConstants userAuthConstants) {
        if (userAuthConstants.getTrToken() == null || userAuthConstants.getJsessionId() == null) {
            throw new RuntimeException("TR token or session ID is missing");
        }
        try {
            AuthUtils.decryptData(userAuthConstants.getTrToken());
            AuthUtils.decryptData(userAuthConstants.getJsessionId());
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed for TR token or session ID", e);
        }
    }


    private void logTokenEvent(UserAuthConstants userAuthConstants,
                               HttpServletRequest request,
                               String tokenType,
                               String acknowledgementType,
                               String tradeToken,
                               String remarks,
                               boolean tokenGenerated) {
        tokenType = updateTokenType(tokenType, userAuthConstants);
        TokenLogInfo tokenLogInfo = new TokenLogInfo();
        tokenLogInfo.setTokenType(tokenType);
        tokenLogInfo.setAcknowledgementType(acknowledgementType);
        tokenLogInfo.setTradeToken(tradeToken);
        tokenLogInfo.setMachineId(getClientIp(request));
        tokenLogInfo.setAppUser(userAuthConstants.getAppUser());
        tokenLogInfo.setUserAgent(request.getHeader("User-Agent"));
        tokenLogInfo.setWelcomeAcknowledgedTime(Instant.now());

        if (tokenGenerated) {
            tokenLogInfo.setTokenGeneratedTime(Instant.now());
        }

        tokenLogInfo.setRemarks(remarks);

        tokenLogInfoRepository.save(tokenLogInfo);
    }

    private String  updateTokenType(String tokenType, UserAuthConstants userAuthConstants) {
        if (userAuthConstants.getXtsClient())
            return tokenType;

        tokenType = tokenType + (userAuthConstants.getIsCugUser()? " : CUG" : "");
        return tokenType;
    }


//    @Transactional
//    public Boolean generateXtsToken(WelcomeDto welcomeDto, HttpServletRequest request){
//
//        String clientId = welcomeDto.getClientId();
//        String decryptedAppKey;
//        String decryptedSecret;
//    Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(clientId);
//    if (userAuthConstantsOptional.isEmpty()) {
//        return false;
//    }
//        UserAuthConstants userAuthConstants = userAuthConstantsOptional.get();
//    if (!welcomeDto.isTermsConditions()) {
//        userAuthConstants.setUserTokenType(TRADING_MODE_FORWARD);
//        userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
//        userAuthConstantsRepository.save(userAuthConstants);
//        return false;
//    }
//        try {
//
//            if(userAuthConstants.getXtsClient()) {
//                decryptedAppKey = AuthUtils.decryptData(userAuthConstants.getXtsAppKey());
//                decryptedSecret = AuthUtils.decryptData(userAuthConstants.getXtsSecretKey());
//                String xtsToken = xtsService.login(decryptedAppKey, decryptedSecret);
//                userAuthConstants.setXtsToken(xtsToken);
//                userAuthConstants.setUserTradingMode(TRADING_MODE_LIVE);
//                AppUserLogInfo appUserLogInfo = new AppUserLogInfo();
//                appUserLogInfo.setAppUsers(userAuthConstants.getAppUsers());
//                appUserLogInfo.setXtsToken(xtsToken);
//                appUserLogInfo.setTokenGeneratedTime(Instant.now());
//                appUserLogInfo.setMechineId(getClientIp(request));
//                appUserLogInfo.setUserAgent(request.getHeader("User-Agent"));
//                appUserLogInfoRepository.save(appUserLogInfo);
//            }
//            else {
//                userAuthConstants.setUserTradingMode(TRADING_MODE_LIVE);
//            }
//            userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
//            userAuthConstantsRepository.save(userAuthConstants);
//            return true;
//        }catch (Exception e){
//            userAuthConstants.setUserTradingMode(TRADING_MODE_FORWARD);
//            userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
//            userAuthConstantsRepository.save(userAuthConstants);
//            return false;
//
    ////        throw new RuntimeException(e);
//
//        }
//
//    }

    public AuthSendDTO welcomeResponse(String clientId) {
        AuthSendDTO dto = new AuthSendDTO();
        Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(clientId);
        UIConstants noUserTokenUiConstants = uiConstantsRepository.findByCode(NO_USER_TOKEN);
        if (userAuthConstantsOptional.isEmpty()) {
            throw new RuntimeException("client info not found for clientId : " + clientId);
        }
        UserAuthConstants user = userAuthConstantsOptional.get();
        dto.setUserId(user.getAppUser().getId());
        dto.setNewUser(false);
        dto.setLoggedInToday(false);

        if(cugUsersService.isUserInCug(user.getClientId()))
            dto.setIsLive(TradingMode.LIVE.getKey().equalsIgnoreCase(user.getUserTradingMode()));
        else
            dto.setIsLive(false);

        if (!TradingMode.LIVE.getKey().equalsIgnoreCase(user.getUserTradingMode())) {
            dto.setBannerContent(noUserTokenUiConstants.getDescription());
        }
        return dto;
    }

    public void setUserTradingModeToForward(String clientId) {
        UserAuthConstants userAuthConstants = userAuthConstantsRepository.findByClientId(clientId)
                .orElseThrow(() -> new UserNotFoundException("User Not Found"));
        userAuthConstants.setUserTradingMode(TradingMode.FORWARD.getKey());
        userAuthConstants.setLastWelcomeAckTime(LocalDateTime.now());
        userAuthConstantsRepository.save(userAuthConstants);
    }


    public static String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    public AppUser getUserFromCLientId(String clientId) {
        Optional<UserAuthConstants> userAuthConstantsOptional = userAuthConstantsRepository.findByClientId(clientId);
        if(userAuthConstantsOptional.isEmpty()) {
            logger.error("User with clientId - " + clientId + " not found ");
            throw new UserNotFoundException("User with clientId - " + clientId + " not found");
        }
        return userAuthConstantsOptional.get().getAppUser();
    }

    ClientDetailsDTO createClientDTO(UserAuthConstants userAuthConstants){
        ClientDetailsDTO clientDetailsDTO = new ClientDetailsDTO();
        clientDetailsDTO.setClientId(userAuthConstants.getClientId());
        clientDetailsDTO.setUserId(userAuthConstants.getAppUser().getUserId());
        clientDetailsDTO.setClientName(userAuthConstants.getName());
        clientDetailsDTO.setEmailId(userAuthConstants.getEmailId());
        clientDetailsDTO.setMobileNo(userAuthConstants.getMobileNumber());
        clientDetailsDTO.setAddress(userAuthConstants.getAddress());
        clientDetailsDTO.setXtsClient(userAuthConstants.getXtsClient());
        clientDetailsDTO.setMaxLoss((userAuthConstants.getMaxLoss()/ (double)AMOUNT_MULTIPLIER));
        clientDetailsDTO.setMinProfit(userAuthConstants.getMinProfit()/ (double) AMOUNT_MULTIPLIER);
        clientDetailsDTO.setXTSAppKey(LocalTime.now().toString());
        return clientDetailsDTO;
    }

    public Boolean setExceptionDate(String clientId,String dateString) {

        if (!Objects.equals(clientId, "1052888"))
            throw new RuntimeException("user is not having Admin access to set Exception Date");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        EXCEPTION_DATE = LocalDate.parse(dateString, formatter);
        bodSchedule.bodScheduler();

        return true;
    }

    private void storeTokensInRedis(CookieDTO cookieDTO) {
        touchLineService.saveToken(cookieDTO.getClientId(), cookieDTO.toString());
    }

    public boolean validTokens(CookieDTO cookieDTO) throws Exception {
        String token = touchLineService.getToken(cookieDTO.getClientId());
        String cookieString = cookieDTO.toString();
        if (token != null && token.equals(cookieString)) {
            return true;
        }
        else {
            validateUser(cookieDTO, endpointProperties.getValidate());// this method will throw error when failed

            storeTokensInRedis(cookieDTO);
        }
        return true;

    }


    public boolean isRateLimited(String clientId, String path) {
        try {
            String key = "THROTTLE_" + clientId + "_" + path;
            Long count = fetchExistingApiCountAndIncrement(key);
            saveRateLimitCount(key, ++count);
            if (count > 0L && count <= RATE_LIMIT_MAX_CALLS) {
                return false;
            }
        } catch (Exception ex) {
            logger.error("Error while checking rate limit: {}", ex.getMessage(), ex);
            return false;
        }
        return true;
    }

    private void saveRateLimitCount(String key, Long aLong) {
        try {
            touchLineService.saveRateLimiter(key, String.valueOf(aLong));
        } catch (Exception ex) {
            logger.error("Error saving rate count for key {}: {}", key, ex.getMessage(), ex);
        }
    }

    public Long fetchExistingApiCountAndIncrement(String key) {
        try {
            String count = touchLineService.getRateLimiter(key);
            if (count == null) {
                return 0L;
            }
            return Long.valueOf(count);
        } catch (Exception ex) {
            logger.error("Error incrementing API count for key {}: {}", key, ex.getMessage(), ex);
            return 0L;
        }
    }

}