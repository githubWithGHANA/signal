package com.quantlab.client.service;

import com.quantlab.client.dto.PositionTableResponseDto;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Position;
import com.quantlab.common.repository.PositionRepository;
import com.quantlab.common.repository.UserAdminRepository;
import com.quantlab.signal.service.AuthService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;
import static com.quantlab.common.utils.staticstore.AppConstants.FORMATTER;

@Service
@Transactional
public class PositionService {

    private static final Logger logger = LogManager.getLogger(PositionService.class);

    @Autowired
    PositionRepository positionRepository;

    @Autowired
    UserAdminRepository userAdminRepository;

    @Autowired
    AuthService authService;

    public List<PositionTableResponseDto> getPosition(String clientId) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            logger.info("Fetching positions for user with ID: {}", appUser.getAppUserId());

            // Get the start of today (midnight)
            Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);

            // Get the end of today (just before midnight)
            Instant endOfDay = startOfDay.plusSeconds(86400);

            // Fetch positions for the existing user
            List<Position> positionList = positionRepository.findByAppUserIdAndDeployedOnToday(appUser.getId(), startOfDay, endOfDay);
            if (positionList.isEmpty()) {
                logger.info("No position found for user with ID: {}", appUser.getAppUserId());
            } else {
                logger.info("{} positions found for user with ID: {}", positionList.size(), appUser.getAppUserId());
            }

            // Map orders to DTOs
            List<PositionTableResponseDto> res = new ArrayList<>();
                positionList.forEach(position -> {
                    PositionTableResponseDto dto = new PositionTableResponseDto();
                    dto.setOrderId(position.getId());
                    dto.setStatus(position.getStatus());
                    dto.setQuantity(position.getNetPosition());
                    dto.setSName(position.getInstrumentName());
                    dto.setStrike(position.getStrikePrice()!= null?position.getStrikePrice().toString():"");
                    dto.setExpiry(position.getContractExpiration());

                    //in uat server mtm always came as 0, so need to make sure that it is correct with actual data
                    Float mtm = position.getMtm()!= null? (Float.parseFloat(position.getMtm())/ AMOUNT_MULTIPLIER) : 0;
                    dto.setMtm(mtm.toString());
                    dto.setPrice(position.getAveragePrice() == null ? null: position.getAveragePrice()/AMOUNT_MULTIPLIER);
                    dto.setSNo(position.getExchangeInstrumentId());
                    String createdTime = position.getDeployedOn()
                            .atZone(ZoneId.of("Asia/Kolkata"))
                            .format(FORMATTER);
                    dto.setDateTime(createdTime);
                    dto.setSymbol(position.getInstrumentName());
                    res.add(dto);
                });

                return res;
            } catch (Exception e) {
                logger.error("Error while mapping positions to DTOs for clientId: {}", clientId, e);
                throw new RuntimeException("Error while processing positions", e); // Throwing a generic runtime exception for now
            }
    }
}
