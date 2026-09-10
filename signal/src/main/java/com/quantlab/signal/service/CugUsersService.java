package com.quantlab.signal.service;

import com.quantlab.signal.service.redisService.CUGRedisService;
import com.quantlab.signal.service.redisService.HolidayService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;
import static com.quantlab.common.utils.staticstore.AppConstants.CUG_USERS;
import static com.quantlab.common.utils.staticstore.AppConstants.CUG_USERS_LIST;

@Service
public class CugUsersService {

    private static final Logger logger = LogManager.getLogger(CugUsersService.class);

    @Autowired
    CUGRedisService cUGRedisService;

    public boolean isUserInCug(String clientId) {
//        try {
//            List<String> cug_users = cUGRedisService.getCUGUsers(CUG_USERS_LIST);
//            if (cug_users == null || cug_users.isEmpty()) {
//                cUGRedisService.saveCUGUsers(CUG_USERS_LIST, CUG_USERS);
//                logger.info("CUG Users list Added in Redis.");
//                return CUG_USERS.contains(clientId);
//            }
//            return cug_users.contains(clientId);
//        } catch (Exception e) {
//            logger.error("Error fetching CUG users from Redis: ", e);
//        }
//            return CUG_USERS.contains(clientId);
        return true;
    }

}
