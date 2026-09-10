package com.quantlab.signal.sheduler;

import com.quantlab.common.dao.ExecutedOrdersDao;
import com.quantlab.common.emailService.EmailService;
import com.quantlab.signal.dto.UserDataDownloadDto;
import com.quantlab.signal.service.EmailDataTransferService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.utils.ExcelExporter;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderScheduler {

    private static final Logger logger = LogManager.getLogger(OrderScheduler.class);

    @Autowired
    private Schedule schedule;

    @Autowired
    private CommonUtils commonUtils;

    @Autowired
    private EmailService emailService;

    @Autowired
    EmailDataTransferService emailDataTransferService;

//    @Scheduled(fixedRate = 1000)
    public void GenerateSchedule() {
        if (LocalTime.now().isAfter(LocalTime.of(9, 15)) && LocalTime.now().isBefore(LocalTime.of(15, 30))) {
            CompletableFuture.runAsync(() -> {
                if (commonUtils.shouldRunScheduler()) {
                    if (LocalTime.now().isAfter(LocalTime.of(9, 15)) && LocalTime.now().isBefore(LocalTime.of(15, 30))) {
                        schedule.scheduleTask();
                    }
                }
            });
        }
    }

    @Scheduled(cron = "0 15 17 ? * MON-FRI")
    void sendUserOrderDataMail(){
        try {
            logger.info("###############$$# start EODScheduler: Sending users order data email");
            List<ExecutedOrdersDao> userDataDownloadDto = emailDataTransferService.userOrdersDataDownload();
            ByteArrayInputStream in = ExcelExporter.exportOrdersToExcel(userDataDownloadDto);
            InputStreamResource file = new InputStreamResource(in);

            String date = java.time.LocalDate.now().toString();
            String fileName = "IB_Algo_Orders_" + date +"_totalOrders="+userDataDownloadDto.size()+ ".xlsx";

            byte[] excelBytes = in.readAllBytes();

            emailService.sendEmailWithAttachment(
                    "Users Order Data",
                    "Please find attached the requested order data Excel file.",
                    fileName,
                    excelBytes
            );
            logger.info("###############$# end EODScheduler: User data email sent successfully");
        }catch (Exception e){
            logger.error("Error in sending user data email: ", e);
        }
    }

}
