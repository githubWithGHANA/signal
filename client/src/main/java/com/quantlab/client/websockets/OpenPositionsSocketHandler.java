package com.quantlab.client.websockets;

import com.quantlab.common.dao.EndpointProperties;
import com.quantlab.common.dto.ApiResponseDTO;
import com.quantlab.common.entity.UserAuthConstants;
import com.quantlab.common.repository.UserAuthConstantsRepository;
import com.quantlab.signal.dto.CookieDTO;
import com.quantlab.signal.service.AuthService;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.quantlab.client.websockets.SocketDataProcessorService.subscribedAppUsers;
import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Component
public class OpenPositionsSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LogManager.getLogger(OpenPositionsSocketHandler.class);
    public static Map<String, CopyOnWriteArrayList<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    EndpointProperties endpointProperties;

    @Autowired
    UserAuthConstantsRepository userAuthConstantsRepository;

    @Value("${spring.profiles.active}")
    private String profile;

    private final ScheduledExecutorService indexDataScheduler = Executors.newScheduledThreadPool(2);


    public OpenPositionsSocketHandler() {
        logger.info("OpenPositionsSocketHandler created");
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException, InterruptedException {
        try {
//            logger.info("### inside socket handleTextMessage message = "+message);
            String sessionKey=validateUser(message.getPayload());
            if (sessionKey == null || sessionKey.isEmpty()){
                session.sendMessage(new TextMessage("User not Found"));
                session.close();
                return;
            }
//            logger.info("SessionID = "+session.getId());

            if (userSessions.containsKey(sessionKey)) {
                CopyOnWriteArrayList<WebSocketSession> sessionsLists = userSessions.get(sessionKey);
                sessionsLists.add(session);
                userSessions.put(sessionKey,sessionsLists);
            }else {
                CopyOnWriteArrayList<WebSocketSession> sessionsLists = new CopyOnWriteArrayList<>();
                sessionsLists.add(session);
                userSessions.put(sessionKey,  sessionsLists);
            }
//            logger.info("### inside socket handleTextMessage sessionKey = "+sessionKey);

            subscribedAppUsers.add(sessionKey);
//            redisProcessingMultiThreadingService.startThreadProcessing(sessionKey);
        } catch (Exception e) {
//            logger.error("failed at afterConnectionEstablished "+e.getMessage());
            session.sendMessage(new TextMessage("unable to establish connection for user"));
            if (session.isOpen())
                session.close();
        }
    }

    private String validateUser(String response){

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            CookieDTO cookieDTO = objectMapper.readValue(response, CookieDTO.class);

            if (profile.equalsIgnoreCase("dev"))
                return cookieDTO.getUserId();

            String url = endpointProperties.getValidate()+ cookieDTO.getClientId();

            // headers
            HttpHeaders headers = new HttpHeaders();
            headers.set(TOKEN, cookieDTO.getToken());
            headers.set(OTP_SESSION_ID, cookieDTO.getOtpSessionId());
            headers.set(MOBILE_NUMBER, cookieDTO.getMobileNumber());
            headers.set(APP_ID, endpointProperties.getAppID());
            headers.set(APP_KEY, endpointProperties.getAppKey());
            HttpEntity<String> entity = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> validationResponse = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
            ApiResponseDTO apiResponseDTO = objectMapper.readValue(validationResponse.getBody(), ApiResponseDTO.class);

            if (apiResponseDTO.getCode().equals(AUTH_SUCCESS)) {
                Optional<UserAuthConstants> userAuthConstants = userAuthConstantsRepository.findByClientId(cookieDTO.getClientId());
                return userAuthConstants.map(authConstants -> authConstants.getAppUser().getAppUserId().toString()).orElse(null);
            }
            throw new Exception(validationResponse.toString());
        } catch (Exception e) {

            throw new RuntimeException(e.getMessage());
        }
    }
//
//    public void afterConnectionEstablished(WebSocketSession session) throws IOException, InterruptedException {
//        try {
//            session.sendMessage(new TextMessage("hello World"));
//            Long sessionKey=1L;
//            logger.info("SessionID = "+session.getId());
//            userSessions.put(sessionKey, session);
//            socketDataProcessorService.startProcessing(sessionKey);
//
//
//        } catch (Exception e) {
//            logger.error("failed at afterConnectionEstablished "+e.getMessage());
//        }
//
//    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
//        logger.info("WebSocket connection closed, sessionId: {}, status: {}", session.getId(), status);
        Iterator<Map.Entry<String, CopyOnWriteArrayList<WebSocketSession>>> iterator = userSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CopyOnWriteArrayList<WebSocketSession>> entry = iterator.next();
            String userId = entry.getKey();
            CopyOnWriteArrayList<WebSocketSession> sessions = entry.getValue();
            sessions.remove(session);
            if (sessions.isEmpty()) {
                iterator.remove();
                subscribedAppUsers.remove(userId);
                logger.info("Removed all sessions for user: {}", userId);
            }
        }
    }

    public void sendOpenPositionsToUser(String sessionKey, Object marketData) {

        if (sessionExists(sessionKey)) {
            try {
                CopyOnWriteArrayList<WebSocketSession> sessionList = userSessions.get(sessionKey);
                CopyOnWriteArrayList<WebSocketSession> disconnectedSessions = new CopyOnWriteArrayList<>();

                String data = objectMapper.writeValueAsString(marketData);

                for (WebSocketSession session: sessionList) {
                    if (session != null && session.isOpen()) {
                        try {
                            synchronized (session) {
                                session.sendMessage(new TextMessage(data));
                            }
                        } catch (IOException e) {
                            logger.error("Failed to send data to session: {} for user: {} - {}", session.getId(), sessionKey, e.getMessage());
                            disconnectedSessions.add(session);
                        }
                    } else {
                        disconnectedSessions.add(session);
                    }
                }

                if (!disconnectedSessions.isEmpty()) {
                    for (WebSocketSession session : disconnectedSessions) {
                        closeConnection(sessionKey, session);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to sendOpenPositionsToUser, error: {}", e.getMessage());
                sessionExistCheck(sessionKey);
            }
        }
    }

    public void broadcastIndexData(Map<String, Object> wrapper) {

        String json;
        try {
            json = objectMapper.writeValueAsString(wrapper);
            TextMessage message = new TextMessage(json);

            for (Map.Entry<String, CopyOnWriteArrayList<WebSocketSession>> entry : userSessions.entrySet()) {
                for (WebSocketSession session : entry.getValue()) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(message);
                        } catch (Exception e) {
                            logger.error("Error sending to session {}: {}", session.getId(), e.getMessage());
                            session.close();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection(String sessionKey, WebSocketSession currentWebSocketSession) {
//        logger.info("inside closeConnection SessionKey " + sessionKey);

        try {
            CloseStatus status = new CloseStatus(4000, "socket disconnected");
            CopyOnWriteArrayList<WebSocketSession> sessionList = userSessions.get(sessionKey);
            if (currentWebSocketSession != null && !currentWebSocketSession.isOpen()) {
                currentWebSocketSession.close(status);
                sessionList.remove(currentWebSocketSession);
            }

            userSessions.put(sessionKey,sessionList);
            if (userSessions.get(sessionKey) == null) {
                userSessions.remove(sessionKey);
                subscribedAppUsers.remove(sessionKey);
            }
        } catch (IOException e) {
            logger.error("failed to closeConnection for: "+sessionKey+" ||"+e.getMessage());
        }
    }

    public void sessionExistCheck(String sessionKey) {
        try {
            if (userSessions.containsKey(sessionKey)) {
                CopyOnWriteArrayList<WebSocketSession> sessionList = userSessions.get(sessionKey);
                for (WebSocketSession session : sessionList) {
                    if (session != null && !session.isOpen()) {
                        sessionList.remove(session);
                        closeConnection(sessionKey, session);
                    }
                }
                userSessions.put(sessionKey,sessionList);
            }
        } catch (Exception e) {
            logger.error("unable to find if the session exists, sessionKey = {}, error: {}", sessionKey, e.getMessage());
        }

    }

    public boolean sessionExists(String sessionKey) {
        try {
            if (!userSessions.get(sessionKey).isEmpty())
                return true;
        } catch (Exception e) {
            logger.error("error trying to find session for user, sessionKey = {}, error: {}", sessionKey, e.getMessage());
        }

        return false;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void clearAllSessionsAtMidnight() {
        logger.info("Midnight cleanup: closing all WebSocket sessions. Active users: {}", userSessions.size());
        for (Map.Entry<String, CopyOnWriteArrayList<WebSocketSession>> entry : userSessions.entrySet()) {
            for (WebSocketSession session : entry.getValue()) {
                try {
                    if (session.isOpen()) {
                        session.close(new CloseStatus(1000, "Midnight session cleanup"));
                    }
                } catch (IOException e) {
                    logger.error("Error closing session for user {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
        userSessions.clear();
        subscribedAppUsers.clear();
        PNLSocketUI.userUISocketDTO.clear();
        PNLSocketUI.dirtyUsers.clear();
        logger.info("Midnight cleanup complete: all sessions and caches cleared");
    }
}
