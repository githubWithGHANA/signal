package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum StatisticsMainTable {
    CAPITAL_REQUIRED("capital_required", "Capital Required"),
    TOTAL_TRADING_DAYS("total_trading_days", "Total Trading Days"),
    WIN_DAYS("win_days", "Win Days"),
    LOSS_DAYS("loss_days", "Loss Days"),
    MAX_WINNING_STREAK_DAYS("max_winning_streak_days", "Max Winning Streak Days"),
    MAX_LOSING_STREAK_DAYS("max_losing_streak_days", "Max Losing Streak Days"),
    WIN_RATE("win_rate", "Win Rate"),
    LOSS_RATE("loss_rate", "Loss Rate"),
    AVG_MONTHLY_PROFIT("avg_monthly_profit", "Avg Monthly Profit"),
    TOTAL_PROFIT("total_profit", "Total Profit"),
    AVG_MONTHLY_ROI("avg_monthly_roi", "Avg Monthly ROI"),
    TOTAL_ROI("total_roi", "Total ROI"),
    STANDARD_DEVIATION_ANNUALISED("standard_deviation_annualised", "Standard Deviation (Annualised)"),
    SHARPE_RATIO_ANNUALISED("sharpe_ratio_annualised", "Sharpe Ratio (Annualised)"),
    SORTING_RATIO_ANNUALISED("sorting_ratio_annualised", "Sorting Ratio (Annualised)"),
    MAX_PROFIT_IN_DAY("max_profit_in_day", "Max Profit In Day"),
    MAX_LOSS_IN_DAY("max_loss_in_day", "Max Loss In Day"),
    AVG_PROFIT_LOSS_DAILY("avg_profit_loss_daily", "Avg Profit/Loss Daily"),
    AVG_PROFIT_ON_PROFIT_DAYS("avg_profit_on_profit_days", "Avg Profit On Profit Days"),
    AVG_LOSS_ON_LOSS_DAYS("avg_loss_on_loss_days", "Avg Loss On Loss Days"),
    AVG_NO_OF_TRADES_PER_DAY("avg_no_of_trades_per_day", "Avg no.of trades (Buy + Sell) per trading day"),
    MAX_DRAWDOWN("max_drawdown", "Max Drawdown"),
    MAX_DRAWDOWN_PERCENT("max_drawdown_percent", "Max Drawdown %");

    private final String key;
    private final String label;

    StatisticsMainTable(String key, String label) {
        this.key = key;
        this.label = label;
    }
}