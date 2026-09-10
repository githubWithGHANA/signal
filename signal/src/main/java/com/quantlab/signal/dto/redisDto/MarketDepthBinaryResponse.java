package com.quantlab.signal.dto.redisDto;

public class MarketDepthBinaryResponse {

    int messageVersion;
    int applicationType;
    long tokenID  ;
    long sequenceNumber ;
    int skipBytes ;
    int exchangeSegment;
    int exchangeInstrumentId ;
    long exchangeTimestamp  ;
    int bidCount;
    int askCount;
    MarketDeptRowInfo bidMarketDeptRow;
    MarketDeptRowInfo askMarketDeptRow;
    long lut ;
    double LTP;
    int ltq ;
    long totalBuyQuantity ;
    long totalSellQuantity ;
    long totalTradedQuantity ;
    double averageTradedPrice;
    long lastTradedTime  ;
    double percentChange;
    double open;
    double high;
    double low;
    double close;
    double totalValueTraded;
    int bbTotalBuy ;
    int bbTotalSell;
    int bookType;
    int marketType;

    public int getMessageVersion() {
        return messageVersion;
    }

    public void setMessageVersion(int messageVersion) {
        this.messageVersion = messageVersion;
    }

    public int getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(int applicationType) {
        this.applicationType = applicationType;
    }

    public long getTokenID() {
        return tokenID;
    }

    public void setTokenID(long tokenID) {
        this.tokenID = tokenID;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public int getSkipBytes() {
        return skipBytes;
    }

    public void setSkipBytes(int skipBytes) {
        this.skipBytes = skipBytes;
    }

    public int getExchangeSegment() {
        return exchangeSegment;
    }

    public void setExchangeSegment(int exchangeSegment) {
        this.exchangeSegment = exchangeSegment;
    }

    public int getExchangeInstrumentId() {
        return exchangeInstrumentId;
    }

    public void setExchangeInstrumentId(int exchangeInstrumentId) {
        this.exchangeInstrumentId = exchangeInstrumentId;
    }

    public long getExchangeTimestamp() {
        return exchangeTimestamp;
    }

    public void setExchangeTimestamp(long exchangeTimestamp) {
        this.exchangeTimestamp = exchangeTimestamp;
    }

    public int getBidCount() {
        return bidCount;
    }

    public void setBidCount(int bidCount) {
        this.bidCount = bidCount;
    }

    public int getAskCount() {
        return askCount;
    }

    public void setAskCount(int askCount) {
        this.askCount = askCount;
    }

    public MarketDeptRowInfo getBidMarketDeptRow() {
        return bidMarketDeptRow;
    }

    public void setBidMarketDeptRow(MarketDeptRowInfo bidMarketDeptRow) {
        this.bidMarketDeptRow = bidMarketDeptRow;
    }

    public MarketDeptRowInfo getAskMarketDeptRow() {
        return askMarketDeptRow;
    }

    public void setAskMarketDeptRow(MarketDeptRowInfo askMarketDeptRow) {
        this.askMarketDeptRow = askMarketDeptRow;
    }

    public long getLut() {
        return lut;
    }

    public void setLut(long lut) {
        this.lut = lut;
    }

    public double getLTP() {
        return LTP;
    }

    public void setLTP(double LTP) {
        this.LTP = LTP;
    }

    public int getLtq() {
        return ltq;
    }

    public void setLtq(int ltq) {
        this.ltq = ltq;
    }

    public long getTotalBuyQuantity() {
        return totalBuyQuantity;
    }

    public void setTotalBuyQuantity(long totalBuyQuantity) {
        this.totalBuyQuantity = totalBuyQuantity;
    }

    public long getTotalSellQuantity() {
        return totalSellQuantity;
    }

    public void setTotalSellQuantity(long totalSellQuantity) {
        this.totalSellQuantity = totalSellQuantity;
    }

    public long getTotalTradedQuantity() {
        return totalTradedQuantity;
    }

    public void setTotalTradedQuantity(long totalTradedQuantity) {
        this.totalTradedQuantity = totalTradedQuantity;
    }

    public double getAverageTradedPrice() {
        return averageTradedPrice;
    }

    public void setAverageTradedPrice(double averageTradedPrice) {
        this.averageTradedPrice = averageTradedPrice;
    }

    public long getLastTradedTime() {
        return lastTradedTime;
    }

    public void setLastTradedTime(long lastTradedTime) {
        this.lastTradedTime = lastTradedTime;
    }

    public double getPercentChange() {
        return percentChange;
    }

    public void setPercentChange(double percentChange) {
        this.percentChange = percentChange;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public double getTotalValueTraded() {
        return totalValueTraded;
    }

    public void setTotalValueTraded(double totalValueTraded) {
        this.totalValueTraded = totalValueTraded;
    }

    public int getBbTotalBuy() {
        return bbTotalBuy;
    }

    public void setBbTotalBuy(int bbTotalBuy) {
        this.bbTotalBuy = bbTotalBuy;
    }

    public int getBbTotalSell() {
        return bbTotalSell;
    }

    public void setBbTotalSell(int bbTotalSell) {
        this.bbTotalSell = bbTotalSell;
    }

    public int getBookType() {
        return bookType;
    }

    public void setBookType(int bookType) {
        this.bookType = bookType;
    }

    public int getMarketType() {
        return marketType;
    }

    public void setMarketType(int marketType) {
        this.marketType = marketType;
    }
}


