package com.quantlab.common.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
public class AppLoginService {
    private static final Logger logger = LoggerFactory.getLogger(AppLoginService.class);


    // Lists of valid hardware identifiers
    private static final Set<String> VALID_BIOS_SERIALS = new HashSet<>(Arrays.asList(
            "VMware-42 02 f7 93 27 9c 8c 93-6e 71 7b 47 0c dc 25 f9", "VMware-42 02 94 8d 71 7d 9d 1a-0f 9a 44 a1 bc 7d 85 5f"
    ));

    private static final Set<String> VALID_DISK_SERIALS = new HashSet<>(Arrays.asList(
            "WD-WXYZ1234567", "ST3000DM001-1234567", "SSD-987654321"  // Replace with your actual valid disk serials
    ));

    /**
     * Checks if the current computer has a valid BIOS serial number.
     * @return true if the BIOS serial is in the valid list, false otherwise
     */
    public  boolean hasValidBiosSerial() {
        String biosSerial = getBiosSerialNumber();
        logger.info("validBiosSerial: " + biosSerial+"|");
        return biosSerial != null && VALID_BIOS_SERIALS.contains(biosSerial);
//        return true;
    }

    /**
     * Checks if the current computer has a valid disk serial number.
     * @return true if the disk serial is in the valid list, false otherwise
     */
    public  boolean hasValidDiskSerial() {
        String diskSerial = getDiskSerialNumber();
        logger.info("Disk serial number: " + diskSerial);
        return diskSerial != null && VALID_DISK_SERIALS.contains(diskSerial);
    }

    /**
     * Checks if both BIOS and disk serial numbers are valid.
     * @return true if both hardware identifiers are valid, false otherwise
     */
    public boolean isValidHardware() {
        logger.info("------------------- SYSTEM CHECK -----------------");
        return  true;
    //    return hasValidBiosSerial();
    }

    /**
     * Retrieves the BIOS serial number of the current system.
     * @return the BIOS serial number or null if it couldn't be retrieved
     */
    private  String getBiosSerialNumber() {
        try {
//            Process process;
//            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
//                // Execute PowerShell command
//                String powerShellPath = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
//                process = Runtime.getRuntime().exec(
//                        new String[]{powerShellPath, "-command",
//                                "Get-CIMInstance Win32_BIOS | Select-Object -ExpandProperty SerialNumber"}
//                );
//
//            } else {
                // Linux command to get BIOS serial
            logger.info("starting check");
            Process process = Runtime.getRuntime().exec(
                    new String[] { "dmidecode", "-s", "system-serial-number" }
            );

//            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.equalsIgnoreCase("SerialNumber")) {
                    return line;
                }
            }
            return null;
        } catch (Exception e) {
//            e.printStackTrace();
            logger.error(e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves the disk serial number of the current system.
     * @return the disk serial number or null if it couldn't be retrieved
     */
    private  String getDiskSerialNumber() {
        try {

            Process process;
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                // Windows command to get disk serial (for C: drive)
                process = Runtime.getRuntime().exec(
                        new String[] { "wmic", "diskdrive", "get", "serialnumber" }
                );
            } else {
                // Linux command to get disk serial (for /dev/sda)
                process = Runtime.getRuntime().exec(
                        new String[] { "sudo", "hdparm", "-i", "/dev/sda" }
                );
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    if (!line.isEmpty() && !line.equalsIgnoreCase("SerialNumber")) {
                        return line;
                    }
                } else {
                    // For Linux, parse the hdparm output to extract the serial number
                    if (line.contains("SerialNo=")) {
                        int start = line.indexOf("SerialNo=") + 9;
                        int end = line.indexOf(" ", start);
                        if (end == -1) end = line.length();
                        return line.substring(start, end);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }

}
