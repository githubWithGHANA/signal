package com.quantlab.client.utils;
import com.quantlab.client.dto.DeployedStratrgiesDto;
import com.quantlab.signal.dto.LegHoldingDTO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class StrategyLegExcelExporter {

    public static ByteArrayInputStream exportToExcel(DeployedStratrgiesDto data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("StrategyLeg Data");

            Row header = sheet.createRow(0);
            String[] headers = {
                    "Leg_Id", "Traded_Instrument", "LTP", "Traded_Lots", "Entry_Price", "Exit_Price", "Entry_Time",
                    "Exit_Time", "Entry_Iv", "Exit_Iv", "Entry_Delta", "Exit_Delta", "Index@Entry", "Index@Exit","P&L"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }

            List<LegHoldingDTO> legs = data.getActiveStrategiesResponse().get(0)
                    .getStrategyLegTableDTO().getData();


            for (int i = 0; i < legs.size(); i++) {
                LegHoldingDTO leg = legs.get(i);
                Row row = sheet.createRow(i + 1);

                row.createCell(0).setCellValue(leg.getLegId());
                row.createCell(1).setCellValue(leg.getName()  != null ? leg.getExitPrice() : 0.0);
                row.createCell(2).setCellValue(leg.getLegLTP() != null ? leg.getExitPrice() : 0.0);
                row.createCell(3).setCellValue(leg.getLegQuantity());
                row.createCell(4).setCellValue(leg.getExecutedPrice() != null ? leg.getExitPrice() : 0.0);
                row.createCell(5).setCellValue(leg.getExitPrice() != null ? leg.getExitPrice() : 0.0);
                row.createCell(6).setCellValue(leg.getDeployedTimeStamp() != null ? leg.getExitPrice() : 0.0);
                row.createCell(7).setCellValue(leg.getExitTime() != null ? leg.getExitPrice() : 0.0);
                row.createCell(8).setCellValue(leg.getCurrentIV() != null ? leg.getExitPrice() : 0.0);
                row.createCell(9).setCellValue(leg.getConstantIV() != null ? leg.getExitPrice() : 0.0);
                row.createCell(10).setCellValue(leg.getCurrentDelta() != null ? leg.getExitPrice() : 0.0);
                row.createCell(11).setCellValue(leg.getConstantDelta() != null ? leg.getExitPrice() : 0.0);
                row.createCell(12).setCellValue(leg.getIndexBasePrice() != null ? leg.getExitPrice() : 0.0);
                row.createCell(13).setCellValue(leg.getIndexCurrentPrice() != null ? leg.getExitPrice() : 0.0);
                row.createCell(14).setCellValue(leg.getPAndL() != null ? leg.getExitPrice() : 0.0);
            }
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}

