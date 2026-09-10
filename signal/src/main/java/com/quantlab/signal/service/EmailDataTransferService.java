package com.quantlab.signal.service;


import com.quantlab.common.dao.ExecutedOrdersDao;
import com.quantlab.common.dto.TokenLogDto;
import com.quantlab.common.entity.TokenLogInfo;
import com.quantlab.common.entity.UserAuthConstants;
import com.quantlab.common.repository.OrderRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.repository.TokenLogInfoRepository;
import com.quantlab.common.repository.UserAuthConstantsRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.ExecutionTypeMenu;
import com.quantlab.signal.dto.UserDataDownloadDto;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class EmailDataTransferService {


    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private UserAuthConstantsRepository userAuthConstantsRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TokenLogInfoRepository tokenLogInfoRepository;

    public List<UserDataDownloadDto> userDataDownload() {

        List<UserDataDownloadDto> userDataDownloadDtoList = new ArrayList<>();

        setUserPersonalDetails(userDataDownloadDtoList);
        setUserStrategiesDetails(userDataDownloadDtoList);

        return userDataDownloadDtoList;
    }

    @Transactional
    public void setUserStrategiesDetails(List<UserDataDownloadDto> userDataDownloadDtoList) {

        for (UserDataDownloadDto userData: userDataDownloadDtoList){
            if (userData.getUserId() == null) continue;

            List<String> strategiesLive = strategyRepository.findListOfStrategyNamesByUserAndExecutionType(userData.getUserId(), ExecutionTypeMenu.LIVE_TRADING.getKey());
            List<String> strategiesPaper = strategyRepository.findListOfStrategyNamesByUserAndExecutionType(userData.getUserId(), ExecutionTypeMenu.PAPER_TRADING.getKey());

            userData.setLiveStrategies(strategiesLive);
            userData.setForwardStrategies(strategiesPaper);
            userData.setTotalStrategies(strategiesLive.size() + strategiesPaper.size());
            userData.setLiveStrategiesCount(strategiesLive.size());
            userData.setForwardStrategiesCount(strategiesPaper.size());
        }

    }

    @Transactional
    public void setUserPersonalDetails(List<UserDataDownloadDto> userDataDownloadDtoList) {

        List<UserAuthConstants> userAuthConstantsList = userAuthConstantsRepository.findAll();
        userAuthConstantsList.forEach(userAuthConstants -> {
            try {
                UserDataDownloadDto dto = new UserDataDownloadDto();
                dto.setClientId(userAuthConstants.getClientId());
                if (userAuthConstants.getAppUser() != null) {
                    dto.setUserId(userAuthConstants.getAppUser().getId());
                    dto.setName(userAuthConstants.getName());
                    dto.setEmail(userAuthConstants.getEmailId());
                    dto.setCreatedAt(userAuthConstants.getAppUser().getCreatedAt());
                    dto.setLastLogin(userAuthConstants.getPreviousLoggedinTime());
                    dto.setEmail(userAuthConstants.getEmailId());
                    dto.setPhoneNumber(userAuthConstants.getMobileNumber());
                    userDataDownloadDtoList.add(dto);
                }
            }catch (Exception e){
                log.error("Error fetching user details for clientId: {}", userAuthConstants.getClientId(), e);
            }
        });
    }

    @Transactional
    public List<ExecutedOrdersDao> userOrdersDataDownload() {
        Instant startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        return orderRepository.findExecutedOrders(startOfDay);
    }

    @Transactional
    public List<TokenLogDto> getUserTokenLogReportRows() {
        List<TokenLogInfo> allTokenLogs = tokenLogInfoRepository.findAll();

        if (allTokenLogs == null || allTokenLogs.isEmpty()) {
            return List.of();
        }

        return allTokenLogs.stream()
                .map(log -> TokenLogDto.builder()
                        .userName(log.getAppUser() != null ? log.getAppUser().getUserName() : "N/A")
                        .clientId(log.getAppUser() != null ? log.getAppUser().getTenentId() : "N/A")
                        .machineId(log.getMachineId() != null ? log.getMachineId() : "N/A")
                        .welcomeAcknowledgedTime(log.getWelcomeAcknowledgedTime())
                        .welcomeAccepted("y".equalsIgnoreCase(log.getAcknowledgementType()))
                        .build()
                )
                .toList();
    }


}
