package com.quantlab.signal.utils;
import com.quantlab.common.dao.ExecutedOrdersDao;
import com.quantlab.common.dto.TokenLogDto;
import com.quantlab.signal.dto.UserDataDownloadDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;


public class ExcelExporter {

    private static final Logger logger = LogManager.getLogger(ExcelExporter.class);

    public static ByteArrayInputStream exportToExcel(List<UserDataDownloadDto> data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("StrategyLeg Data");

            Row header = sheet.createRow(0);
            String[] headers = {
                    "Client ID",
                    "Name",
                    "Email",
                    "Phone Number",
                    "Total Strategies",
                    "Live Strategies Count",
                    "Live Strategies",
                    "Forward Strategies Count",
                    "Forward Strategies",
                    "Last Login",
                    "Created At",
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }



            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                UserDataDownloadDto dto = data.get(i);

                row.createCell(0).setCellValue(dto.getClientId());
                row.createCell(1).setCellValue(dto.getName());
                row.createCell(2).setCellValue(dto.getEmail());
                row.createCell(3).setCellValue(dto.getPhoneNumber());
                row.createCell(4).setCellValue(dto.getTotalStrategies());
                row.createCell(5).setCellValue(dto.getLiveStrategiesCount());
                row.createCell(6).setCellValue(String.join(", ", dto.getLiveStrategies()));
                row.createCell(7).setCellValue(dto.getForwardStrategiesCount());
                row.createCell(8).setCellValue(String.join(", ", dto.getForwardStrategies()));
                row.createCell(9).setCellValue(dto.getLastLogin() != null ? dto.getLastLogin().toString() : "");
                row.createCell(10).setCellValue(dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : "");
            }
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public static ByteArrayInputStream exportOrdersToExcel(List<ExecutedOrdersDao> data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Executed Orders");

            // Header row
            Row header = sheet.createRow(0);
            String[] headers = {
                    "Order ID",
                    "Instrument Name",
                    "Order Side",
                    "Cumulative Quantity",
                    "Average Traded Price",
                    "Client ID",
                    "User Name",
                    "Strategy Name",
                    "Exchange Timestamp"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Data rows
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                ExecutedOrdersDao dto = data.get(i);

                row.createCell(0).setCellValue(dto.getOrderId() != null ? dto.getOrderId() : "");
                row.createCell(1).setCellValue(dto.getInstrumentName() != null ? dto.getInstrumentName() : "");
                row.createCell(2).setCellValue(dto.getOrderSide() != null ? dto.getOrderSide() : "");
                row.createCell(3).setCellValue(dto.getCumulativeQuantity() != null ? dto.getCumulativeQuantity().toString() : "");
                row.createCell(4).setCellValue(dto.getAverageTradedPrice() != null ? dto.getAverageTradedPrice() : "");
                row.createCell(5).setCellValue(dto.getClientID() != null ? dto.getClientID() : "");
                row.createCell(6).setCellValue(dto.getUserName() != null ? dto.getUserName() : "");
                row.createCell(7).setCellValue(dto.getStrategyName() != null ? dto.getStrategyName() : "");
                row.createCell(8).setCellValue(dto.getExchangeTimeStamp() != null ? dto.getExchangeTimeStamp() : "");
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
    public static ByteArrayInputStream exportUserTokenLogsToExcel(List<TokenLogDto> tokenLogs) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("User Login Information");

            String[] headers = {
                    "Client ID", "User Name", "Machine ID", "Welcome Time", "Welcome Accepted"
            };

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle dateTimeStyle = workbook.createCellStyle();
            CreationHelper helper = workbook.getCreationHelper();
            dateTimeStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowNum = 1;
            for (TokenLogDto log : tokenLogs) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(log.getClientId() != null ? log.getClientId() : "N/A");
                row.createCell(1).setCellValue(log.getUserName() != null ? log.getUserName() : "N/A");
                row.createCell(2).setCellValue(log.getMachineId() != null ? log.getMachineId() : "N/A");
                Cell timeCell = row.createCell(3);
                if (log.getWelcomeAcknowledgedTime() != null) {
                    timeCell.setCellValue(java.util.Date.from(log.getWelcomeAcknowledgedTime()));
                    timeCell.setCellStyle(dateTimeStyle);
                }
                row.createCell(4).setCellValue(log.isWelcomeAccepted() ? "Yes" : "No");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

}
