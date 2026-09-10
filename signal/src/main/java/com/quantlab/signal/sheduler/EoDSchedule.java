package com.quantlab.signal.sheduler;

import com.quantlab.common.dto.StatisticsResponseDto;
import com.quantlab.common.dto.TokenLogDto;
import com.quantlab.common.emailService.EmailService;
import com.quantlab.common.entity.*;
import com.quantlab.common.repository.AppUserRepository;
import com.quantlab.common.repository.DeploymentErrorsRepository;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.service.StatisticsService;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.UserDataDownloadDto;
import com.quantlab.signal.service.EmailDataTransferService;
import com.quantlab.signal.service.ExitPNL;
import com.quantlab.signal.service.GrpcErrorService;
import com.quantlab.signal.utils.ExcelExporter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.staticdata.StaticStore.redisFetchedData;

@Service
public class EoDSchedule {
    private static final Logger logger = LoggerFactory.getLogger(EoDSchedule.class);

    @Autowired
    BodSchedule bodSchedule;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    BodSchedule BodSchedule;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private EmailService emailService;

    @Autowired
    EmailDataTransferService emailDataTransferService;

    @Value("${spring.profiles.active}")
    private String environment;

    @Autowired
    ExitPNL exitPNL;

    AtomicReference<String> schedularSummary = new AtomicReference<>("");
    private final AtomicBoolean eodStatisticsRunning = new AtomicBoolean(false);

    @Value("${spring.profiles.active}")
    private String activeProfile;


    @Scheduled(cron = "0 20 15 ? * *")
    public void rmsSquareOff() {
        try {
            intraDayRmsSquareOff();
        } catch (Exception e) {
            logger.error("############################ Error during RMS square off processing: {}. ############################", e.getMessage());
        }
    }

    @Scheduled(cron = "0 01 01 ? * *")
    public void positionalStrategyErrorSquareOff() {
        try {
            positionalExpiredLegValidation();
        } catch (Exception e) {
            logger.error("############################ Error during Positional Strategy square off processing: {}. ############################", e.getMessage());
        }
    }

    private void intraDayRmsSquareOff() {
        try {
            rMSSquareOffSignals();
            rMSSquareOffStrategies();
        } catch (Exception e) {
            logger.error("Error during intra-day RMS square off: {}", e.getMessage());
        }

    }

    @Transactional
    public void rMSSquareOffStrategies() {
        List<Strategy> strategies = strategyRepository.findAllByStatusInAndPositionType(LIVE_ERROR, StrategyType.INTRADAY.getKey());
        List<DeploymentErrors> deploymentErrorsList = new ArrayList<>();
        for (Strategy strategy : strategies) {
            try {
                strategy.setStatus(Status.ERROR.getKey());

                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setStrategy(strategy);
                deploymentErrors.setAppUser(strategy.getAppUser());
                deploymentErrors.setDeployedOn(Instant.now());
                deploymentErrors.setStatus(strategy.getStatus());
                deploymentErrors.setDescription(new ArrayList<>(List.of(ERROR_STRATEGY_LIVE_AFTER_EOD_DESCRIPTION)));
                deploymentErrorsList.add(deploymentErrors);
            } catch (Exception e) {
                logger.error("Error when saving RMS square off logs for strategy = {} , error message is = {}",
                        strategy.getId(), e.getMessage());
            }
        }
        if (strategies.isEmpty()) {
            logger.info("No strategies found for RMS square off at EOD.");
            return;
        }
        logger.info("Found {} strategies for RMS square off at EOD.", strategies.size());
        strategyRepository.bulkUpdateStatus(Status.ERROR.getKey(), StrategyType.INTRADAY.getKey(), LIVE_ERROR);
        deploymentErrorsRepository.saveAll(deploymentErrorsList);
    }

    @Transactional
    public void rMSSquareOffSignals() {
        int total = signalRepository.bulkUpdateStatus(Status.RMS_ERROR.getKey(), StrategyType.INTRADAY.getKey(), LIVE_ERROR);
        logger.info("rMSSquareOffSignals: Total {} signals updated to RMS_ERROR status for RMS square off at EOD.", total);

    }

    @Transactional
    public void findSignalsForRmsSquareOff() {
        List<Signal> allSignals = signalRepository.findAllByStatusInAndPositionType(LIVE_ERROR, StrategyType.INTRADAY.getKey());
        if (allSignals.isEmpty()) {
            logger.info("No signals found for RMS square off at EOD.");
            return;
        }
        logger.info("Found {} signals for RMS square off at EOD.", allSignals.size());

        for (Signal signal : allSignals) {
            if (Objects.equals(signal.getPositionType(), StrategyType.INTRADAY.getKey())) {
                if (signal.getStatus().equalsIgnoreCase(Status.LIVE.getKey()))
                    exitFailedErrorLogs(ERROR_SIGNAL_LIVE_AFTER_EOD_DESCRIPTION, signal, SignalStatus.EXIT.getKey());
                else
                    exitFailedErrorLogs(ERROR_SIGNAL_EOD_DESCRIPTION, signal, SignalStatus.EXIT.getKey());
            }
        }
    }

    @Transactional
    public void eachStrategyStatusValidation() {
        List<Strategy> subscribedStrategies = strategyRepository.findAllBySubscriptionAndSourceNotNull(SubscriptionStatus.START.getKey());
        for (Strategy strategy : subscribedStrategies) {
            List<Signal> allSignals = signalRepository.findAllByStrategyAndStatusIn(strategy, LIVE_ERROR);
            for (Signal signal : allSignals) {
                if (Objects.equals(signal.getPositionType(), StrategyType.INTRADAY.getKey())) {
                    if (signal.getStatus().equalsIgnoreCase(Status.LIVE.getKey()))
                        exitFailedErrorLogs(ERROR_SIGNAL_LIVE_AFTER_EOD_DESCRIPTION, signal, RUN_TIME_EXCEPTION);
                    else
                        exitFailedErrorLogs(ERROR_SIGNAL_EOD_DESCRIPTION, signal, RUN_TIME_EXCEPTION);
                }
            }
        }
    }

    @Transactional
    public void exitFailedErrorLogs(String errorMessage, Signal signal, String status) {
        try {
            if (!status.equalsIgnoreCase(SignalStatus.EXIT.getKey()))
                saveStrategyAndSignalAsError(signal);
            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(signal.getStrategy());
            deploymentErrors.setAppUser(signal.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrors.setStatus(signal.getStrategy().getStatus());
            deploymentErrors.setDescription(new ArrayList<>(List.of(errorMessage)));
            deploymentErrorsRepository.save(deploymentErrors);
        } catch (Exception e) {
            logger.error("error when saving EOD signal live logs for strategy = {} ," +
                    " error message is = {}", signal.getStrategy().getId(), errorMessage);
        }

    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveStrategyAndSignalAsError(Signal signal) {

        signal.setStatus(Status.RMS_ERROR.getKey());
        signalRepository.save(signal);
        if (signal.getStrategy().getPositionType().equalsIgnoreCase(StrategyType.INTRADAY.getKey())) {
            Strategy strategy = signal.getStrategy();
            strategy.setStatus(Status.ERROR.getKey());
            strategyRepository.save(strategy);
        }
    }

    @Transactional
    public void positionalExpiredLegValidation() {
        try {
            List<Strategy> candidateStrategies = strategyRepository.findAllByStatusInAndPositionType(LIVE_ERROR, StrategyType.POSITIONAL.getKey());

            if (candidateStrategies == null || candidateStrategies.isEmpty()) {
                logger.info("No live strategies found for positional expired-leg validation scheduler.");
                return;
            }

            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            int scanned = 0;
            int markedError = 0;
            List<DeploymentErrors> deploymentErrorsList = new ArrayList<>();

            for (Strategy strategy : candidateStrategies) {
                if (strategy == null || !StrategyType.POSITIONAL.getKey().equalsIgnoreCase(strategy.getPositionType())) {
                    continue;
                }
                scanned++;

                Optional<Signal> liveSignalOpt = signalRepository.findFirstByStatusAndStrategy_idOrderByCreatedAtDesc(
                        SignalStatus.LIVE.getKey(), strategy.getId()
                );
                if (liveSignalOpt.isEmpty()) {
                    continue;
                }

                Signal liveSignal = liveSignalOpt.get();
                List<StrategyLeg> legs = liveSignal.getSignalLegs();
                if (legs == null || legs.isEmpty()) {
                    continue;
                }

                boolean expiredLiveLegFound = false;
                for (StrategyLeg leg : legs) {
                    if (leg == null || leg.getName() == null || leg.getName().isBlank()) {
                        continue;
                    }
                    LocalDate expiryDate = extractExpiryDateFromLegName(leg.getName());
                    if (expiryDate == null) {
                        continue;
                    }
                    if (expiryDate.isBefore(today)) {
                        expiredLiveLegFound = true;
                        logger.warn("Expired live leg detected. strategyId={}, signalId={}, legId={}, legName={}",
                                strategy.getId(), liveSignal.getId(), leg.getId(), leg.getName());
                        break;
                    }
                }

                if (!expiredLiveLegFound) {
                    continue;
                }

                strategy.setStatus(Status.ERROR.getKey());
                liveSignal.setStatus(Status.RMS_ERROR.getKey());

                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setStrategy(strategy);
                deploymentErrors.setAppUser(strategy.getAppUser());
                deploymentErrors.setDeployedOn(Instant.now());
                deploymentErrors.setStatus(Status.ERROR.getKey());
                deploymentErrors.setDescription(new ArrayList<>(List.of(ERROR_POSITIONAL_EXPIRED_LEG_LIVE_DESCRIPTION)));
                deploymentErrorsList.add(deploymentErrors);
                markedError++;
            }

            if (!deploymentErrorsList.isEmpty()) {
                deploymentErrorsRepository.saveAll(deploymentErrorsList);
            }
            logger.info("Positional expired-leg validation completed. scannedStrategies={}, markedError={}", scanned, markedError);
        } catch (Exception e) {
            logger.error("Error during positional expired-leg validation: {}", e.getMessage(), e);
        }
    }

    private LocalDate extractExpiryDateFromLegName(String legName) {
        Matcher matcher = LEG_NAME_EXPIRY_PATTERN.matcher(legName);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (Exception ignored) {
            logger.error("positional strategy name was unable to processed as it is not in expected format name: {}", legName);
            return null;
        }
    }

    @Scheduled(cron = "0 30 16 ? * MON-FRI")
    @Async
    void eodStatisticsScheduled(){

//        if (!bodSchedule.shouldRunScheduler()) {
//            return;
//        }
        if (!eodStatisticsRunning.compareAndSet(false, true)) {
            logger.warn("Skipping EOD statistics run because a previous run is still in progress.");
            return;
        }
        long startMs = System.currentTimeMillis();
        try {
            logger.info("###############$#$# start EODScheduler: Updating statistics for all strategies at EOD.");
            List<Long> subscribedStrategies = findAllSubscribedStrategies();
            StrategyProcessingResult strategyProcessingResult = processSubscribedStrategies(subscribedStrategies);
//            updateDrawDownForStrategies(strategyProcessingResult.strategyDrawDownMap());
            logger.info("###############$#$# Subscribed strategies Statistics update completed. total={}, updated={}, skippedNull={}, failedException={}, durationMs={}",
                    strategyProcessingResult.totalStrategies(),
                    strategyProcessingResult.updatedCount(),
                    strategyProcessingResult.skippedNullCount(),
                    strategyProcessingResult.failedExceptionCount(),
                    System.currentTimeMillis() - startMs);
        } catch (Exception e) {
            logger.error("Error during EOD processing", e);
        } finally {
            eodStatisticsRunning.set(false);
        }
    }

    private void updateDrawDownForStrategies(HashMap<Long, Long> strategyDrawDownMap) {
        if (strategyDrawDownMap == null || strategyDrawDownMap.isEmpty()) {
            logger.info("No subscribed strategies drawDown found for updating.");
            return;
        }
        logger.info("Updating drawDown for {} subscribed strategies.", strategyDrawDownMap.size());
        strategyDrawDownMap.forEach((id, drawDown) -> strategyRepository.updateDrawDown(id, drawDown));
        logger.info("Updated drawDown for {} subscribed strategies.", strategyDrawDownMap.size());
    }

    private Map<Long, Long> getDefaultStatistics(AppUser firstUser, Map<Long, Long> allStatistics) {
        List<Strategy> firstUserStrategies = strategyRepository.findAllByAppUser_Id(firstUser.getId());
        Map<Long, Long> firstUserStatistics = new HashMap<>();
        for (Strategy strategy : firstUserStrategies) {
            try {
                Long stats = allStatistics.get(strategy.getId());
                if (stats != null) {
                    firstUserStatistics.put(strategy.getSourceId(), stats);
                } else {
                    logger.warn("No statistics found for strategy ID: {}", strategy.getId());
                }
            } catch (Exception e) {
                logger.error("Error creating statistics for strategy ID {} : {}", strategy.getId(), e.getMessage());
            }
        }
        return  firstUserStatistics;
    }

    private void assignDrawDownToAllStrategies(List<Strategy> allStrategies, Map<Long, Long> firstUserStatistics, Map<Long, Long> statistics) {
        for (Strategy strategy : allStrategies) {
            try {
                Long drawDown = statistics.get(strategy.getId());

                if(firstUserStatistics.containsKey(strategy.getId())) {
                    drawDown = firstUserStatistics.get(strategy.getId());
                }else if (drawDown == null && strategy.getSubscription() != null &&
                        !strategy.getSubscription().equalsIgnoreCase(SubscriptionStatus.START.getKey())) {
                    drawDown = firstUserStatistics.get(strategy.getSourceId());
                }

                if (drawDown != null) {
                    strategy.setDrawDown(drawDown);
                    strategyRepository.save(strategy);
                    }
                else {
                    logger.warn("No statistics found for strategy ID: {}", strategy.getId());
                }
            } catch (Exception e) {
                logger.error("Error processing strategy ID {}: {}", strategy.getId(), e.getMessage());
            }
        }
    }

    private StrategyProcessingResult processSubscribedStrategies(List<Long> subscribedStrategiesIds) {
        HashMap<Long, Long> strategyDrawDownMap = new HashMap<>();
        int skippedNullCount = 0;
        int failedExceptionCount = 0;
        for (Long strategyId : subscribedStrategiesIds) {
            try {
                StatisticsResponseDto statisticsResponseDto = statisticsService.updateStatistics(strategyId);
                if (statisticsResponseDto == null || statisticsResponseDto.getStatisticsIndexes() == null
                        || statisticsResponseDto.getStatisticsIndexes().getMaxDrawDownPercent() == null) {
                    skippedNullCount++;
                    logger.warn("Skipping drawDown update for strategyId={} due to missing statistics payload.", strategyId);
                    continue;
                }
                Long dd = statisticsResponseDto.getStatisticsIndexes().getMaxDrawDownPercent();
                strategyDrawDownMap.put(strategyId, dd);
            } catch (Exception e) {
                failedExceptionCount++;
                logger.error("Error creating statistics for strategy ID {}", strategyId, e);
            }
        }
        return new StrategyProcessingResult(
                strategyDrawDownMap,
                subscribedStrategiesIds.size(),
                strategyDrawDownMap.size(),
                skippedNullCount,
                failedExceptionCount
        );
    }


    private record StrategyProcessingResult(
            HashMap<Long, Long> strategyDrawDownMap,
            int totalStrategies,
            int updatedCount,
            int skippedNullCount,
            int failedExceptionCount
    ) {}

    private List<Long> findAllSubscribedStrategies() {
        List<Long> strategies = strategyRepository.findIdsBySubscription(SubscriptionStatus.START.getKey());
        if (strategies == null || strategies.isEmpty()) {
            logger.info("No subscribed strategies found for EOD processing.");
            return Collections.emptyList();
        }
        return strategies;
    }

    private AppUser findFirstUser() {
        try {
        AppUser appUser = appUserRepository.findByUserRole_id(4L).get();
        return appUser;
        }catch (Exception e) {
            logger.warn("No user found with role ID 4, returning null for EOD processing.");
            return null;
        }
    }

    @Scheduled(cron = "0 50 15 ? * *")
    void sendUserDataMail(){
        try {

            if ("dev".equalsIgnoreCase(environment) ||"development".equalsIgnoreCase(environment)) {
                logger.info("Skipping user data email in development environment.");
                return;
            }

            logger.info("###############$# start EODScheduler: Sending user data email at 6:30 PM");
            List<UserDataDownloadDto> userDataDownloadDto = emailDataTransferService.userDataDownload();
            ByteArrayInputStream in = ExcelExporter.exportToExcel(userDataDownloadDto);

            String date = java.time.LocalDate.now().toString();
            String fileName = "IB_Algo_Users" + "-" + date + ".xlsx";

            byte[] excelBytes = in.readAllBytes();
            emailService.sendEmailWithAttachment(
                    "User Data " +date+" Download",
                    "Please find attached Excel file for users strategies subscription data.",
                    fileName,
                    excelBytes
            );
            logger.info("###############$# end EODScheduler: User data email sent successfully");
        }catch (Exception e){

        }
    }

    @Scheduled(cron = "0 31 15 ? * *")
    @Async
    void eodPNLScheduled(){
        try {
            logger.info("###############$#$#$# start EOD_Final_PNL_Scheduler: Processing EOD final PNL for all EXIT signals.");
            List<Map<String, String>> taskDetails = new ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Kolkata"));
            String envPrefix = activeProfile.equalsIgnoreCase("uatprod") ? "UAT Indiabulls" : "Indiabulls";
            StringBuilder errorDetails = new StringBuilder();
            AtomicReference<Long> total = new AtomicReference<>(0L);
            bodSchedule.runTaskAndTrack("EOD_Final_P&L_Scheduler", () -> total.set(eodExitPNLProcessing()), taskDetails, formatter, errorDetails);
            logger.info("###############$#$#$# END EOD_Final_PNL_Scheduler: Processing EOD final PNL for all EXIT signals.");

            try{
                DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                String formattedNow = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(formatter1);
                String subject = envPrefix + "EOD final PNL Scheduler Completed Successfully";
                String successMessage = "Indiabulls EOD final PNL Scheduler completed successfully at " + formattedNow + ".<br> total signals processes = " + total.get();
                String nextStep = "";
                String message = emailService.getEmailSuccessBodTemplate(subject,successMessage, taskDetails,nextStep);
                emailService.sendEmail( BOD_EMAILS, subject + " " + formattedNow, message);
            } catch (Exception mailEx) {
                logger.error("Error sending failure email: {}", mailEx.getMessage(), mailEx);
            }
        } catch (Exception e) {
            logger.error("Error during EOD_Final_PNL processing: {}", e.getMessage());
        }
    }
    @Scheduled(cron = "0 00 17 ? * *")
    void sendUserTokenReportMail() {
        try {
            if (!bodSchedule.shouldRunScheduler()) {
                return;
            }
            if ("dev".equalsIgnoreCase(activeProfile) || "development".equalsIgnoreCase(activeProfile)) {
                logger.info("Skipping user token report email in development environment.");
                return;
            }

            logger.info("############### Start: Sending user token report email at 5:00 PM");

            List<TokenLogDto> tokenLogDtos = emailDataTransferService.getUserTokenLogReportRows();
            if (tokenLogDtos.isEmpty()) {
                logger.info("No token logs found. Skipping report.");
                return;
            }
            // Generate Excel
            ByteArrayInputStream in = ExcelExporter.exportUserTokenLogsToExcel(tokenLogDtos);

            String date = java.time.LocalDate.now().toString();
            String fileName = "user-login-report-" + date + ".xlsx";

            byte[] excelBytes = in.readAllBytes();
            emailService.sendEmailWithAttachmentToSpecificRecipients(
                    BOD_EMAILS,
                    "User Login Report " + date,
                    "Please find attached Excel file for user login information. Total records: " + tokenLogDtos.size(),
                    fileName,
                    excelBytes
            );

            logger.info("############### End: User token report email sent successfully. Total records: {}", tokenLogDtos.size());

        } catch (Exception e) {
            logger.error("Error sending user token report email: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public Long eodExitPNLProcessing() {
        AtomicInteger total = new AtomicInteger(0);
        int batchSize = 100;
        Long lastId = 0L;
        List<Signal> signals;

        do {
            signals = signalRepository.fetchSignalsBatchAfterId(Status.EXIT.getKey(),
                    SignalStatus.SCHEDULED_PNL.getKey(), lastId, batchSize, ExecutionTypeMenu.LIVE_TRADING.getKey());
            if (signals.isEmpty()) break;

            signals.parallelStream().forEach(signal -> {
                boolean exited = exitPNL.setFinalPNL(signal);
                if (exited) {
                    total.incrementAndGet();
                }
            });

            lastId = signals.get(signals.size() - 1).getId();
        } while (signals.size() == batchSize);

        return total.longValue();
    }
}
