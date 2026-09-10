package com.quantlab.signal.dto.redisDto;

public class OpenInterestBinaryResponse {

    int messageVersion ;
    int applicationType ;
    long tokenID ;
    long sequenceNumber = 0;
    int skipBytes = 0;
    int exchangeSegment ;
    int exchangeInstrumentId ;
    long exchangeTimestamp ;
    int marketType ;
    int openInterest ;
    int underlyingExchangeSegment ;
    long underlyingInstrumentID ;
    byte isStringExists ;
    byte stringLength ;

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

    public int getMarketType() {
        return marketType;
    }

    public void setMarketType(int marketType) {
        this.marketType = marketType;
    }

    public int getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(int openInterest) {
        this.openInterest = openInterest;
    }

    public int getUnderlyingExchangeSegment() {
        return underlyingExchangeSegment;
    }

    public void setUnderlyingExchangeSegment(int underlyingExchangeSegment) {
        this.underlyingExchangeSegment = underlyingExchangeSegment;
    }

    public long getUnderlyingInstrumentID() {
        return underlyingInstrumentID;
    }

    public void setUnderlyingInstrumentID(long underlyingInstrumentID) {
        this.underlyingInstrumentID = underlyingInstrumentID;
    }

    public byte getIsStringExists() {
        return isStringExists;
    }

    public void setIsStringExists(byte isStringExists) {
        this.isStringExists = isStringExists;
    }

    public byte getStringLength() {
        return stringLength;
    }

    public void setStringLength(byte stringLength) {
        this.stringLength = stringLength;
    }

    public int getUnderlyingTotalOpenInterest() {
        return underlyingTotalOpenInterest;
    }

    public void setUnderlyingTotalOpenInterest(int underlyingTotalOpenInterest) {
        this.underlyingTotalOpenInterest = underlyingTotalOpenInterest;
    }

    int underlyingTotalOpenInterest ;


}
