package com.quantlab.signal.utils;

import lombok.Getter;

@Getter
public enum StrategyMapper {


    DELTA_NEUTRAL("DN","DeltaNeutralStrategy"),
    DELTA_NEUTRAL_HEDGE("DNH","DeltaNeutralStrategyHedge"),
    DIY("DIY","DiyStrategy"),
    ROLLING_STRADDLE("RS","RollingStraddle"),
    PHOENIX("PHOENIX", "Phoenix"),
    ULTA_DELTA_HEDGE("UDH", "DeltaNeutralStrategyHedge"),
    ATM_ACCUMULATION("ATM_ACCUMULATION", "AtmOptionAccumulationStrategy"),
    JODI_STRATEGY("JS","JodiStrategy"),
    NIFTY_OPTION_SELL("NIFTY_SELLING", "OptionSellingStrategy"),
    NIFTY_OPTION_BUY("NIFTY_BUYING", "OptionBuyingStrategy"),
    SENSEX_OPTION_SELL("SENSEX_SELLING", "OptionSellingStrategy"),
    SENSEX_OPTION_BUY("SENSEX_BUYING", "OptionBuyingStrategy"),
    SENSEX_DELTA_NEUTRAL("SENSEX_DN","DeltaNeutralStrategy"),
    SENSEX_DELTA_NEUTRAL_HEDGE("SENSEX_DNH","DeltaNeutralStrategyHedge"),
    SENSEX_ROLLING_STRADDLE("SENSEX_RS","RollingStraddle"),
    SENSEX_ULTA_DELTA_HEDGE("SENSEX_UDH", "DeltaNeutralStrategyHedge"),
    SENSEX_PHOENIX("SENSEX_PHOENIX", "Phoenix"),
    SENSEX_SCALPER("SENSEX_SCALPER", "AtmOptionAccumulationStrategy"),
    SENSEX_JODI_STRATEGY("SENSEX_JS","JodiStrategy"),
    JODI_STRATEGY_V2("JODI_ARBITRAGE_V2","JodiStrategy"),
    JB_BANKNIFTY_EXPIRY("JB_BNF_EXPIRY","JbBankniftyExpiryStrategy"),
    JB_NIFTY_EXPIRY("JB_NIFTY_EXPIRY","JbBankniftyExpiryStrategy"),
    JB610_SUPERTREND("JB610_SUPERTREND","Jb610SupertrendStrategy"),
    EMA_EXHAUSTION_BB_SELL("EMA_EXHAUSTION_BB_SELL","EmaExhaustionBollingerSellStrategy"),
    BRR_NIFTY("BRR_NIFTY","BhuvasTrendFollowingStrategy"),
    BRR_BNF("BRR_BNF","BhuvasTrendFollowingStrategy");

    private final String key;
    private final String label;

    StrategyMapper(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
