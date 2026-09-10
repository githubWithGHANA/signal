package com.quantlab.signal.utils.excel;

import com.quantlab.signal.dto.XtsOrdersDto;
import com.quantlab.signal.dto.XtsPlaceOrderDto;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ExcelExporter {

    private static final Logger logger = LogManager.getLogger(ExcelExporter.class);
    public static void exportToExcel(List<MasterResponseFO> instruments, String filePath) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Instruments");
        // Create header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Exchange Segment", "Exchange Instrument ID", "Instrument Type", "Exchange Segment ID", "Name",
                "Description", "Series", "Name with Series", "Instrument ID", "Price Band High",
                "Price Band Low", "Freeze Qty", "Tick Size", "Lot Size", "Multiplier",
                "Underlying Instrument ID", "Underlying Index Name", "Strike Price", "Contract Expiration",
                "Option Type", "Price Numerator", "Price Denominator", "Display Name", "Instrument Key"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // Populate data rows
        int rowNum = 1;
        for (MasterResponseFO instrument : instruments) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(instrument.getExchangeSegment());
            row.createCell(1).setCellValue(instrument.getExchangeInstrumentID());
            row.createCell(2).setCellValue(instrument.getInstrumentType());
            row.createCell(3).setCellValue(instrument.getExchangeSegmentId());
            row.createCell(4).setCellValue(instrument.getName());
            row.createCell(5).setCellValue(instrument.getDescription() != null ? instrument.getDescription() : "null");
            row.createCell(6).setCellValue(instrument.getSeries());
            row.createCell(7).setCellValue(instrument.getNameWithSeries());
            row.createCell(8).setCellValue(instrument.getInstrumentID());
            row.createCell(9).setCellValue(instrument.getPriceBandHigh());
            row.createCell(10).setCellValue(instrument.getPriceBandLow());
            row.createCell(11).setCellValue(instrument.getFreezeQty());
            row.createCell(12).setCellValue(instrument.getTickSize());
            row.createCell(13).setCellValue(instrument.getLotSize());
            row.createCell(14).setCellValue(instrument.getMultiplier());
            row.createCell(15).setCellValue(instrument.getUnderlyingInstrumentId());
            row.createCell(16).setCellValue(instrument.getUnderlyingIndexName());
            row.createCell(17).setCellValue(instrument.getStrikePrice());
            row.createCell(18).setCellValue(instrument.getContractExpiration());
            row.createCell(19).setCellValue(instrument.getOptionType() != null ? instrument.getOptionType() : "null");
            row.createCell(20).setCellValue(instrument.getPriceNumerator());
            row.createCell(21).setCellValue(instrument.getPriceDenominator());
            row.createCell(22).setCellValue(instrument.getDisplayName());
            row.createCell(23).setCellValue(instrument.getInstrumentKey());
            logger.info(rowNum);
        }

        // Write to file
        try (
                FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        } catch (IOException e) {
//            e.printStackTrace();
            logger.info(e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
//                e.printStackTrace();
                logger.info(e.getMessage());
            }
        }
    }


    public static void exportPlaceOrderToExcel(XtsPlaceOrderDto placeOrder, String filePath) {
        Workbook workbook;
        Sheet sheet;

        try (FileInputStream fileIn = new FileInputStream(filePath)) {
            workbook = new XSSFWorkbook(fileIn);
            sheet = workbook.getSheet("PlaceOrderSheet");


            // If the sheet does not exist, create it and add headers
            if (sheet == null) {
                sheet = workbook.createSheet("PlaceOrderSheet");
                createHeaderRow(sheet);
            }else {
                sheet = workbook.getSheet("PlaceOrderSheet");
            }

        } catch (IOException e) {
//            e.printStackTrace();
            logger.error(e);
            return; // Exit if file cannot be read
        }
       // sheet= workbook.getSheetAt(0);


        // Create a new row at the end of the sheet
        int lastRowNum = sheet.getLastRowNum();

        int rowNum = sheet.getLastRowNum()+1;
        List<XtsOrdersDto> orders = placeOrder.getOrders();
        for (XtsOrdersDto order : orders) {
            LocalDateTime time = LocalDateTime.now();
            String formatedTime = time.format(DateTimeFormatter.ofPattern("hh:mm:ss a"));
            // Create a new row for each order
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(order.getExchangeSegment() != null ? order.getExchangeSegment() : "null");
            row.createCell(1).setCellValue(order.getExchangeInstrumentId());
            row.createCell(2).setCellValue(order.getOrderType() != null ? order.getOrderType() : "null");
            row.createCell(3).setCellValue(order.getOrderSide() != null ? order.getOrderSide() : "null");
            row.createCell(4).setCellValue(order.getTimeInForce() != null ? order.getTimeInForce() : "null");
            row.createCell(5).setCellValue(order.getDisclosedQuantity());
            row.createCell(6).setCellValue(order.getOrderQuantity());
            row.createCell(7).setCellValue(order.getLimitPrice());
            row.createCell(8).setCellValue(order.getStopPrice());
            row.createCell(9).setCellValue(order.getOrderUniqueIdentifier() != null ? order.getOrderUniqueIdentifier() : "null");
            row.createCell(10).setCellValue(order.getProductType() != null ? order.getProductType() : "null");
            row.createCell(11).setCellValue(placeOrder.getSignalID());
            row.createCell(12).setCellValue(placeOrder.getTenantID());
            row.createCell(13).setCellValue(formatedTime);
        }

        // Write to file
        try (
                FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
        } catch (IOException e) {
//            e.printStackTrace();
            logger.error(e);
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
//                e.printStackTrace();
                logger.error(e);
            }
        }
    }


    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Exchange Segment", "Exchange Instrument ID", "Instrument Type", "Exchange Segment ID", "Name",
                "Description", "Series", "Name with Series", "Instrument ID", "Price Band High",
                "Price Band Low", "Freeze Qty", "Tick Size", "Lot Size", "Multiplier",
                "Underlying Instrument ID", "Underlying Index Name", "Strike Price", "Contract Expiration",
                "Option Type", "Price Numerator", "Price Denominator", "Display Name", "Instrument Key", "signalId", "tenentId","time"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }
    }


}

