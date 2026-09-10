package com.quantlab.signal.sheduler;

import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.TouchlineBinaryResposne;
import com.quantlab.signal.service.redisService.TouchLineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MockPnLSimulator {
    private static final Logger logger = LoggerFactory.getLogger(MockPnLSimulator.class);

    @Autowired
    TouchLineService touchLineService;


    @Value("${spring.profiles.active}")
    private String profile;


//    @Scheduled(fixedRate = 500)
    void changeTL(){
        if (profile.equalsIgnoreCase("dev")) {
            try {
                List<TouchlineBinaryResposne> resposnes = touchLineService.getTouchLines();
                for (TouchlineBinaryResposne touchlineBinaryResposne : resposnes) {
                    int value = (int) (Math.random() * 3) - 1;
                    touchlineBinaryResposne.setLTP(touchlineBinaryResposne.getLTP() + value);
                    touchLineService.saveTouchLine("TL_" + touchlineBinaryResposne.getExchangeInstrumentId(), touchlineBinaryResposne);
                }
            } catch (Exception e) {
                logger.error("remove @scheduler in PnLScheduler as Error in changing TL data: ", e);
            }
        }
    }

//    @Scheduled(fixedRate = 500)
    void changeMD() {
        if (profile.equalsIgnoreCase("dev")) {
            try {
                List<MarketData> responses = touchLineService.findAllMarketData();

                for (MarketData md : responses) {
                    int value = (int) (Math.random() * 3) - 1;
                    md.setLTP(md.getLTP() + value);
                    touchLineService.saveMarketData("MD_" + String.valueOf(md.getExchangeInstrumentId()), md);
                }
            } catch (Exception e) {
                logger.error("remove @scheduler in PnLScheduler as Error in changing MD data: ", e);
            }
        }
    }

}
