package com.quantlab.common.emailService;


import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.utils.staticstore.AppConstants;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Autowired
    @Qualifier("bodMailExecutor")
    private Executor bodMailExecutor;


    @Value("${spring.profiles.active}")
    private String environment;

    /**
     * Constructor for EmailServiceImpl.
     *
     * @param mailSender the JavaMailSender to use for sending emails
     */
    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        log.info("EmailServiceImpl initialized with environment: {}", environment);
    }

    @Value("${email.sender}")
    private String senderEmail;

    @Value("classpath:templates/otp-template.html")
    private Resource template;

    @Value("classpath:templates/failed-attempt.html")
    private Resource failedAttemptTemplate;

    @Value("classpath:templates/error-template.html")
    private Resource errorTemplate;

    @Value("classpath:templates/success-template.html")
    private Resource successTemplate;

    @Value("classpath:templates/criticalError-template.html")
    private Resource criticalErrorTemplate;


    @Value("classpath:templates/critical-Error.html")
    private Resource criticalErrorBodTemplate;


    @Value("classpath:templates/login-success.html")
    private Resource successBodTemplate;

    private void setEnv() {
        if (environment == null || environment.isEmpty()) {
            environment = "develop"; // Fallback to a default value if not set
        }else if (environment.equals("prod")) {
            environment = "production";
        } else if (environment.equals("uatprod")) {
            environment = "testing";
        } else if (environment.equals("dev")) {
            environment = "development";
        }
    }

    @Override
    public void sendEmail(String[] to, String subject, String body) {
        if ("dev".equalsIgnoreCase(environment) ||"development".equalsIgnoreCase(environment)) {
            log.warn("Email sending is disabled in 'dev' environment");
            return;
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(senderEmail);
            String[] filteredRecipients = filterEmailsForEnvironment(to);
            helper.setTo(filteredRecipients);
            helper.setSubject(subject);
            helper.setText(body, true);

            mailSender.send(mimeMessage);

            log.info("Email sent successfully to: " + to);
        } catch (MessagingException e) {
            log.error("Error sending email to: " + to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public String getEmailOtpTemplate(String otp) {
        log.info("Reading OTP email template...");
        String message;
        try (InputStream inputStream = template.getInputStream()) {
            message = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            message = message.replace("{{otp}}", String.valueOf(otp));
            return message;
        } catch (Exception e) {
            log.error("Error reading OTP email template", e);
            throw new RuntimeException("Failed to load OTP email template", e);
        }
    }

    @Override
    public String getEmailFailedTemplate(String otp) {

        log.info("Reading OTP email template...");
        String message;
        try (InputStream inputStream = failedAttemptTemplate.getInputStream()) {
            message = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            return message;
        } catch (Exception e) {
            log.error("Error reading OTP email template", e);
            throw new RuntimeException("Failed to load OTP email template", e);
        }
    }

    @Override
    public String getEmailErrorTemplate(String errorDetails) {
        log.info("Reading Error email template...");
        try (InputStream inputStream = errorTemplate.getInputStream()) {
            String message = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            message = message.replace("{{errorDetails}}", errorDetails);
            message = message.replace("[dd/mm/yy]", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yy")));
            message = message.replace("[HH:MM AM/PM]", LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));
            message = message.replace("{{CLIENT_NAME}}", AppConstants.CLIENT_NAME);
            return message;
        } catch (Exception e) {
            log.error("Error reading Error email template", e);
            throw new RuntimeException("Failed to load Error email template", e);
        }
    }


    @Override
    public String getEmailSuccessTemplate() {
        log.info("Reading Success email template...");
        try (InputStream inputStream = successTemplate.getInputStream()) {
            String message = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            message = message.replace("{{date}}", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            message = message.replace("{{time}}", LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));

            return message;
        } catch (Exception e) {
            log.error("Error reading Success email template", e);
            throw new RuntimeException("Failed to load Success email template", e);
        }
    }

    public String getEmailCriticalErrorTemplate(String errorDetails) {
        log.info("Reading Critical Error email template...");
        try (InputStream inputStream = criticalErrorTemplate.getInputStream()) {
            String message = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            message = message.replace("{{errorDetails}}", errorDetails);

            String dateTime = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yy")) + " "
                    + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            message = message.replace("{{dateTime}}", dateTime);

            return message;
        } catch (Exception e) {
            log.error("Error reading Critical Error email template", e);
            throw new RuntimeException("Failed to load Critical Error email template", e);
        }
    }

    @Override
    public String getEmailCriticalErrorBodTemplate(String errorDetails) {
        setEnv();
        log.info("Reading Critical Error email template...");
        try (InputStream inputStream = criticalErrorBodTemplate.getInputStream()) {
            String message = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            message = message.replace("{{errorDetails}}", errorDetails);
            message = message.replace("{{environment}}", environment);

            String dateTime = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yy")) + " "
                    + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            message = message.replace("{{dateTime}}", dateTime);

            return message;
        } catch (Exception e) {
            log.error("Error reading Critical Error email template", e);
            throw new RuntimeException("Failed to load Critical Error email template", e);
        }
    }

    @Override
    public String getEmailSuccessBodTemplate(String message, String paragraph, List<Map<String, String>> tasks, String nextStep) {
        setEnv();
        log.info("Reading Critical Error email template...");
        try (InputStream inputStream = successBodTemplate.getInputStream()) {
            String newmessage = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            boolean hasFailure = tasks.stream().anyMatch(task -> "Failed".equalsIgnoreCase(task.get("status")));

            String headerColor = hasFailure ? "#ff4d4d" : "#19f77b";
            String tableHeaderBg = hasFailure ? "#ff4d4d" : "#19f77b";
            String tableHeaderText = hasFailure ? "#ffffff" : "#101820";

            newmessage = newmessage.replace("{{header}}", message);
            newmessage = newmessage.replace("{{paragraph}}", paragraph);
            newmessage = newmessage.replace("{{nextStep}}", nextStep);
            newmessage = newmessage.replace("{{headerColor}}", headerColor);
            newmessage = newmessage.replace("{{tableHeaderBg}}", tableHeaderBg);
            newmessage = newmessage.replace("{{tableHeaderText}}", tableHeaderText);
            newmessage = newmessage.replace("{{CLIENT_NAME}}", AppConstants.CLIENT_NAME);


            StringBuilder taskRows = new StringBuilder();
            for (Map<String, String> task : tasks) {
                String status = task.getOrDefault("status", "-");
                String statusColor = "Failed".equalsIgnoreCase(status) ? "style='color:red;'" : "style='color:lime;'";
                taskRows.append("<tr>")
                        .append("<td>").append(task.getOrDefault("taskName", "-")).append("</td>")
                        .append("<td>").append(task.getOrDefault("startTime", "-")).append("</td>")
                        .append("<td>").append(task.getOrDefault("endTime", "-")).append("</td>")
                        .append("<td ").append(statusColor).append(">").append(status).append("</td>")
                        .append("</tr>");
            }
            newmessage = newmessage.replace("{{taskRows}}", taskRows.toString());

            return newmessage;
        } catch (Exception e) {
            log.error("Error reading Critical Error email template", e);
            throw new RuntimeException("Failed to load Critical Error email template", e);
        }
    }

    @Override
    public void sendBodMailAsync(
            String[] recipients,
            String emailSubject,
            String templateSubject,
            String templateMessage,
            List<Map<String, String>> taskDetails,
            String nextStep
    ) {
        List<Map<String, String>> taskDetailsSnapshot = new ArrayList<>(taskDetails);
        bodMailExecutor.execute(() -> {
            try {
                log.info("Started async BOD email send for subject: {}, thread: {}", emailSubject, Thread.currentThread().getName());
                String message = getEmailSuccessBodTemplate(templateSubject, templateMessage, taskDetailsSnapshot, nextStep);
                sendEmail(recipients, emailSubject, message);
                log.info("Completed async BOD email send for subject: {}, thread: {}", emailSubject, Thread.currentThread().getName());
            } catch (Exception mailEx) {
                log.error("Error sending BOD email: {}", mailEx.getMessage(), mailEx);
            }
        });
        log.info("Queued async BOD email for subject: {}", emailSubject);
    }


    @Async
    @Override
    public void sendAdminEmail(String message,boolean status) {
        try {
            log.info("Reading Error email template...");

            if (status){
                message = getEmailFailedTemplate(message);
            }
            MimeMessage mimeMessage = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(senderEmail);
            helper.setTo(ADMIN_EMAILS);
            helper.setSubject("BOD is working Status");
            helper.setText(message, true);

            mailSender.send(mimeMessage);

            log.info("Email sent successfully to: ");
        } catch (MessagingException e) {
            log.error("Error sending email to: " + "host" , e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
    public void sendStrategyErrorEmail(Strategy strategy, String errorMessage) {
        try {
            AppUser appUser = strategy.getAppUser();
            if (appUser == null) {
                log.warn("Cannot send strategy error email - user or email is null for strategy {}", strategy.getId());
                return;
            }

            String errorDetails = String.format(
                    "Strategy Error Details:<br>" +
                            "<strong>Strategy ID:</strong> %s<br>" +
                            "<strong>Strategy Name:</strong> %s<br>" +
                            "<strong>Strategy Category:</strong> %s<br>" +
                            "<strong>Client ID:</strong> %s<br>" +
                            "<strong>User Name:</strong> %s<br>" +
                            "<strong>Error:</strong> %s",
                    strategy.getId(),
                    strategy.getName(),
                    strategy.getCategory(),
                    appUser.getTenentId(),
                    appUser.getUserName(),
                    errorMessage
            );

            String emailTitle = "Indiabulls Strategy Error Alert - " + strategy.getName();
            String nextStep = "Please review the admin page and contact the user";
            String clientName = "Indiabulls";

            String template = getEmailErrorTemplate(errorDetails); // Load template string

            String emailBody = template
                    .replace("{{EMAIL_TITLE}}", emailTitle)
                    .replace("{{errorDetails}}", errorDetails)
                    .replace("{{Status}}","Failed")
                    .replace("{{NEXT_STEP}}", nextStep)
                    .replace("{{CLIENT_NAME}}", clientName)
                    .replace("[dd/mm/yy]", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yy")))
                    .replace("[HH:MM AM/PM]", LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));

            sendEmail(BOD_EMAILS, emailTitle, emailBody);
        } catch (Exception e) {
            log.error("Failed to send strategy error email for {}: {}", strategy.getId(), e.getMessage());
        }
    }
    private String[] filterEmailsForEnvironment(String[] recipients) {
        if ("uatprod".equalsIgnoreCase(environment) ||"testing".equalsIgnoreCase(environment) || "development".equalsIgnoreCase(environment) || "dev".equalsIgnoreCase(environment)) {
            // Exclude these emails in UAT
            List<String> excludedEmails = List.of(BOD_ADMIN_EMAILS);
            return Arrays.stream(recipients)
                    .filter(email -> !excludedEmails.contains(email))
                    .toArray(String[]::new);
        }
        return recipients;
    }
    public void sendEmailAlert(double ltp, long tradedTime) {
        String subject = "Alert: marketdata unchanged";

        String errorDetails = String.format(
                "Lasttradingprice and Lastupdatedtime have not changed for over 60 seconds.<br><br>" +
                        "<strong>LTP:</strong> %.2f<br>" +
                        "<strong>LastTradedTime:</strong> %d",
                ltp, tradedTime
        );

        String envPrefix = "uatprod".equalsIgnoreCase(environment) || "testing".equalsIgnoreCase(environment) ? "UAT Indiabulls" : "Indiabulls";
        String emailTitle = envPrefix + " mfeed data not updated";
        String nextStep = "Please check mfeed logs.";
        String clientName = "Indiabulls";

        try {
            log.warn("Triggering email alert to recipients...");

            String template = getEmailErrorTemplate(errorDetails);

            String emailBody = template
                    .replace("{{EMAIL_TITLE}}", emailTitle)
                    .replace("{{errorDetails}}", errorDetails)
                    .replace("{{Status}}", "notupdated")
                    .replace("{{NEXT_STEP}}", nextStep)
                    .replace("{{CLIENT_NAME}}", clientName)
                    .replace("[dd/mm/yy]", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yy")))
                    .replace("[HH:MM AM/PM]", LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));

            sendEmail(BOD_EMAILS, subject, emailBody);
        } catch (Exception e) {
            log.error("Failed to send email alert", e);
        }
    }
    public void sendEmailAlertForNullData(String symbol) {
        String subject = "ALERT: MarketData Missing for Symbol " + symbol;

        String errorDetails = String.format(
                "MarketData is missing for symbol: <strong>%s</strong>.<br>" +
                        "Possible reasons: Mfeed data failure, Redis issue<br><br>" +
                        "<strong>LTP:</strong> N/A<br>" +
                        "<strong>LastTradedTime:</strong> N/A", symbol);

        String envPrefix = "uatprod".equalsIgnoreCase(environment) || "testing".equalsIgnoreCase(environment) ? "UAT Indiabulls" : "Indiabulls";
        String emailTitle = envPrefix + " mfeed MarketData missing";
        String nextStep = "Please check Redis and MFeed logs.";
        String clientName = "Indiabulls";

        try {
            log.warn("Triggering NULL data email alert to recipients...");

            String template = getEmailErrorTemplate(errorDetails);

            String emailBody = template
                    .replace("{{EMAIL_TITLE}}", emailTitle)
                    .replace("{{errorDetails}}", errorDetails)
                    .replace("{{Status}}", "Data Not Found")
                    .replace("{{NEXT_STEP}}", nextStep)
                    .replace("{{CLIENT_NAME}}", clientName)
                    .replace("[dd/mm/yy]", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yy")))
                    .replace("[HH:MM AM/PM]", LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));

            sendEmail(BOD_EMAILS, subject, emailBody);
        } catch (Exception e) {
            log.error("Failed to send MarketData null alert", e);
        }
    }

    @Override
    public void sendEmailWithAttachment(String subject, String body, String fileName, byte[] attachmentBytes) {
        try {
            if ("dev".equalsIgnoreCase(environment) ||"development".equalsIgnoreCase(environment)) {
                log.warn("Email sending is disabled in 'dev' environment");
                return;
            }

            String[] to = filterEmailsForEnvironment(TO_BOD_EMAILS);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(senderEmail);
            helper.setTo(to);

            String[] filteredBcc = filterEmailsForEnvironment(BOD_EMAILS);
            helper.setBcc(filteredBcc);

            helper.setSubject(subject);
            helper.setText(body, true);

            if (attachmentBytes != null && fileName != null) {
                helper.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(attachmentBytes));
            }

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send email with attachment", e);
            throw new RuntimeException("Failed to send email with attachment", e);
        }
    }

    @Override
    public void sendOrderEmailWithAttachment(String subject, String body, String fileName, byte[] attachmentBytes) {
        try {
            if ("dev".equalsIgnoreCase(environment) ||"development".equalsIgnoreCase(environment)) {
                log.warn("Email sending is disabled in 'dev' environment");
                return;
            }

            String[] to = {};

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(senderEmail);
            helper.setTo(to);

            helper.setSubject(subject);
            helper.setText(body, true);

            if (attachmentBytes != null && fileName != null) {
                helper.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(attachmentBytes));
            }

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send email with attachment", e);
            throw new RuntimeException("Failed to send email with attachment", e);
        }
    }
    @Override
    public void sendEmailWithAttachmentToSpecificRecipients(String[] recipients, String subject, String body, String fileName, byte[] attachmentBytes) {
        try {
            if ("dev".equalsIgnoreCase(environment) || "development".equalsIgnoreCase(environment)) {
                log.warn("Email sending is disabled in 'dev' environment");
                return;
            }

            String[] filteredRecipients = filterEmailsForEnvironment(recipients);

            if (filteredRecipients.length == 0) {
                log.warn("No valid recipients after filtering for environment: {}", environment);
                return;
            }

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(senderEmail);
            helper.setTo(filteredRecipients);
            helper.setSubject(subject);
            helper.setText(body, true);

            if (attachmentBytes != null && fileName != null) {
                helper.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(attachmentBytes));
            }

            mailSender.send(mimeMessage);
            log.info("Email sent successfully to {} recipients", filteredRecipients.length);
        } catch (MessagingException e) {
            log.error("Failed to send email with attachment", e);
            throw new RuntimeException("Failed to send email with attachment", e);
        }
    }

}
