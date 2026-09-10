package com.quantlab.common.utils.staticstore;

import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyStatus;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AppConstants {
    public static final String STATUS_STAND_BY = "standby";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_LIVE = "live";
    public static final String PLACING_ORDER = "Placing-Order";
    public static final String SIGNAL_STATUS_LIVE = "live";
    public static final String SIGNAL_STATUS_EXIT = "exit";
    public static final String MANUALLY_TRADED = "manually-traded";
    public static final String EXITED_MANUALLY = "Exited-Manually";
    public static final String CANCELLED = "cancelled";

    public static final List<String> STRATEGY_STATUS_RETRY_LIST = new ArrayList<>(List.of(SIGNAL_STATUS_EXIT,STATUS_STAND_BY,CANCELLED,EXITED_MANUALLY, Status.PAUSED.getKey()));
    public static final List<String> LIVE_ERROR = new ArrayList<>(List.of(Status.LIVE.getKey(), Status.ERROR.getKey(), Status.PENDING.getKey(), Status.EXIT_PENDING.getKey()));
    public static final String LEG_STATUS_TYPE_OPEN = "open";
    public static final Long AMOUNT_MULTIPLIER = 1000L;
    public static final Long GREEK_MULTIPLIER = 100000L;
    public static final int STRIKE_INTERVAL = 100;
    public static final String MASTERDATA = "MasterData" ;
    public static final ArrayList<String> EXPIRYVALUE = new ArrayList<>(List.of("EXP_SENSEX_IO", "EXP_NIFTY_FUTIDX", "EXP_NIFTY_OPTIDX", "EXP_BANKEX_IF", "EXP_BANKNIFTY_FUTIDX", "EXP_FINNIFTY_OPTIDX", "EXP_BANKNIFTY_OPTIDX", "EXP_FINNIFTY_FUTIDX", "EXP_BANKEX_IO", "EXP_SENSEX_IF"));
    public static final ArrayList<String> SAFE_STATUS_LIST = new ArrayList<>(List.of("open", "pending", "complete", "placed", "PendingReplace", "partially-filled", "complete", "retrying", "exit-pending"));
    public static final ArrayList<String> FAILED_STATUS_LIST = new ArrayList<>(List.of(StrategyStatus.REJECTED.getKey(), StrategyStatus.CANCELLED.getKey()));
    public static final ArrayList<String> FETCH_STRATEGIES_BY_STATUS = new ArrayList<>(List.of(Status.ACTIVE.getKey(), Status.LIVE.getKey(), Status.EXIT.getKey()));
    public static final ArrayList<String> FETCH_PENDING_STRATEGIES_BY_STATUS = new ArrayList<>(List.of(Status.PENDING.getKey(), Status.EXIT_PENDING.getKey()));
    public static final String []   ADMIN_EMAILS = {"rsriwastava@gmail.com", "mahir@quantlab.tech"};
    public static final String PERCENTOFCAPITAL =  "PercentOfCapital";
    public static final String DEFAULT_UNDERLING_TYPE =  "NA";

    public static final String [] BOD_EMAILS = {"rsriwastava@gmail.com", "mahir@quantlab.tech", "gopi@quantlab.tech"};
    public static final String [] TO_BOD_EMAILS = {"rajesh_quanthub@indiabulls.com","Parth.shah@indiabulls.com", "Mayur.d@indiabulls.com", "misreporting@indiabulls.com"};
    public static final String [] BOD_ADMIN_EMAILS = {"rajesh_quanthub@indiabulls.com", "rsriwastava@gmail.com", "Parth.shah@indiabulls.com", "Mayur.d@indiabulls.com", "misreporting@indiabulls.com", "venkatamohanreddygopireddy@gmail.com"};
    public static final String  CLIENT_NAME = "Indiabulls";

    public static final Long AUTH_SUCCESS =  0L;
    public static final String OK_200 =  "200 OK";
    public static final String TOKEN =  "token";
    public static final String OTP_SESSION_ID =  "otpSessionId";
    public static final String MOBILE_NUMBER =  "mobileNumber";

    public static final String APP_ID =  "app-id";
    public static final String APP_KEY =  "app-key";
//  public static final String CLIENT_PROFILE_ENDPOINT_UAT =  "https://internal-partner-uat.ibullssecurities.com/client-profile?clientId=";
//   public static final String CLIENT_PROFILE_ENDPOINT_PROD =  "https://ssologin.dhanistocks.com/v1/partner/client-profile?clientId=";
//   public static final String CLIENT_VALIDATE_ENDPOINT_UAT =  "https://ssologin-uat.dhanistocks.com/v1/partner/validate-session?clientId=";
//   public static final String CLIENT_VALIDATE_ENDPOINT_PROD =  "https://ssologin.dhanistocks.com/v1/partner/validate-session?clientId=";
//   public static final String APP_KEY_VALUE_UAT =  "mtOwyowygskpv8r6is6kddxhjanvsbsl";
//   public static final String APP_ID_VALUE =  "quantlab";

    public static final String CUG_URL = "https://omsmnm.dhanistocks.com/NestHtml5Mobile/rest";
    public static final String NON_CUG_URL = "https://omsmprod.dhanistocks.com/NestHtml5Mobile/rest";

    public static final String UAT_CUG_URL = "http://172.16.103.186/NestHtml5Mobile/rest";
    public static final String UAT_NON_CUG_URL = "https://dsomsmobileuat.dhanistocks.com/NestHtml5Mobile/rest";

    public static final Boolean SHOULD_AUTHENTICATE = false;
    public static final String SYSTEM =  "system";
    public static final Long ZERO_LONG =  0L;
    public static final Long DEFAULT_MULTIPLIER =  1L;
    public static final Long DEFAULT_MIN_MAX_VALUE = 0L;
    public static final String RUN_TIME_EXCEPTION = "Run time exception";
    public static final int SECONDS_TO_EOD = 86400;
    public static final String TOGGLE_TRUE = "true";
    public static final String XTS_COMMON_URL = "https://xts-uat.ibullssecurities.com";
    public static final String LOGIN_INTERACTIVE = "/interactive/user/session";
    public static final String ERROR_PLACING_ORDER_DESCRIPTION = "Error placing order, ";
    public static final String ERROR_PLACING_EXIT_ORDER_DESCRIPTION = "Error placing exit order, please contact Admin. ";
    public static final String ERROR_SIGNAL_LIVE_AFTER_EOD_DESCRIPTION = "Signal processing error, RMS square off";
    public static final String ERROR_STRATEGY_LIVE_AFTER_EOD_DESCRIPTION = "Strategy processing error, RMS square off";
    public static final String ERROR_SIGNAL_EOD_DESCRIPTION = "Intraday signal found in error, RMS Close";
    public static final String ERROR_SIGNAL_PLACING_EXIT = "Signal processing error, while exiting the signal, please contact Admin. ";
    public static final String HOLIDAY_REDIS_KEY = "HOLIDAY";
    public static final long NO_USER_TOKEN = 100L;
    public static  String ACTIVE_PROFILE = "PROD";
    public static final List<String> VALID_LEG_FOR_PNL = new ArrayList<>(List.of(SIGNAL_STATUS_LIVE, MANUALLY_TRADED, LEG_STATUS_TYPE_OPEN, PLACING_ORDER));
    public static  String CUG_USERS_LIST = "CUG_USERS_LIST";
    public static final List<String> CUG_USERS = new ArrayList<>(List.of(
            "614598", "1045454", "1055307", "614627", "1052888",
            "1061643", "1052057", "1054754", "1059905", "1067941", "1070680",
            "1065761", "1062554", "1065077", "1060260", "1056348", "336814",
            "1060434", "1060644", "1055346", "1054295", "1067527",
            "1014640", "1051000", "1053886", "1056202", "108263",
            "122860", "207550", "210678", "211877", "226501",
            "3426", "364022", "396584", "414046", "42693",
            "432898", "441303", "532178", "547280", "560310",
            "560451", "578759", "604159", "614635", "74459",
            "810573", "93454", "BY1357", "BY4375", "BY4420",
            "JM284", "PU1464"
    ));
    public static final String PENDING_ERROR_MAIL_SUBJECT = "Strategy status is Pending for Extended Duration.";

    public static final String ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND = "Error processing strategy invalid data found, please contact Admin. ";
    public static final String ACCEPT_TOKEN_GENERATION = "y";
    public static final String DECLINE_TOKEN_GENERATION = "n";
    public static final String XTS_TOKEN_GENERATION_SUCCESS = "XTS Token generated successfully";
    public static final String XTS_TOKEN_GENERATION_FAILURE = "XTS Token Generation Failed";
    public static final String TR_TOKEN_VALIDATION_SUCCESS = "TR Token validated successfully";
    public static final String TR_TOKEN_VALIDATION_FAILURE = "XTS Token generated successfully";
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public static final double MAX_STOP_LOSS_DUALBAND_STRADDLE = 10000.0;
    public static final double MAX_STOP_LOSS_IN_HOUSE = 5000.0;

    public static final int RATE_LIMIT_MAX_CALLS = 30; // allowed calls for user per RATE_LIMIT_WINDOW_SECONDS
    public static final long RATE_LIMIT_WINDOW_SECONDS = 1L; // api throttle reset time in seconds
    /** IST timezone — replaces ZoneId.systemDefault() throughout */
    public static final String IST_ZONE_ID = "Asia/Kolkata";

    public static final Pattern LEG_NAME_EXPIRY_PATTERN = Pattern.compile(".*(\\d{4}-\\d{2}-\\d{2}).*");
    public static final String ERROR_POSITIONAL_EXPIRED_LEG_LIVE_DESCRIPTION = "Positional strategy has expired live leg please manually close any open orders.";
    public static final String DELETE_INDICATOR_TRUE = "Y";

}
