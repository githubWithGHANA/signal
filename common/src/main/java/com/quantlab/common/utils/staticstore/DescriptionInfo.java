package com.quantlab.common.utils.staticstore;

public class DescriptionInfo {

    public static final String STRATEGY_NAME = "Strategy Name is a descriptive name assigned to a trading strategy";
    public static final String INDEX = "Index represents the underlying market index associated with the trading strategy";
    public static final String CAPITAL = "Capital represents the total amount of funds allocated for executing the strategy";
    public static final String EXECUTION_TYPE = "Execution Type of execution for an order in the market";
    public static final String STRATEGY_TYPE = "Strategy Type defines the category of the trading strategy based on its execution logic and market approach";
    public static final String ENTRY_TIME = "Entry Time specifies the exact time at which the strategy is set to enter a trade";
    public static final String ENTER_ON_DAYS = "Enter on Days specifies the trading days on which the strategy will be executed";
    public static final String EXIT_TIME = "Exit Time specifies the exact time at which the strategy will close an open position";
    public static final String EXIT_ON_EXPIRY = "Exit on Expiry determines whether the strategy will automatically exit open positions on the expiry date";
    public static final String EXIT_AFTER_ENTRY_DAYS = "Exit After Entry Days specifies the number of days after entry when the strategy will automatically exit the position";
    public static final String PROFIT_MTM = "Profit MTM represents the real-time unrealized profit of the strategy based on the current market price strategy exits when a predefined profit amount is reached";
    public static final String STOPlOSS_MTM = "StopLoss MTM represents the real-time unrealized loss threshold at which the strategy will automatically exit positions to prevent further losses";
    public static final String SEGMENT = "Segment refers to a specific market category where financial instruments like stocks, derivatives, commodities are traded";
    public static final String POSITION = "Position defines the type and quantity of market exposure taken within the strategy it determines whether the strategy involves buying, selling";
    public static final String OPTION_TYPE = "Option Type specifies the type of options contract used in the strategy this helps define whether the strategy involves Call or Put options";
    public static final String LOTS = "Lots represents the number of contract lots to be traded in the strategy";
    public static final String EXPIRY = "Expiry specifies the expiration date of the options or futures contract used in the strategy this determines the last date on which the contract can be settled";
    public static final String STRIKE_SELECTION = "Strike Selection determines how the strike price of an options contract is selected based on market conditions";
    public static final String STRIKE_TYPE = "Strike Type specifies how the strike price is selected relative to the underlying asset's price in an options strategy";
    public static final String TARGET = "TGT specifies the profit target at which the strategy will automatically exit the position to book gains";
    public static final String STOPLOSS = "StopLoss is the maximum loss threshold at which the strategy will exit to prevent further losses";
    public static final String TSL = "TSL is a dynamic stop-loss mechanism that adjusts automatically as the trade moves in a favorable direction it helps lock in profits while limiting potential losses";
    public static final String TRAILING_DISTANCE = "Trailing distance defines the gap between the current market price and the trailing stop loss this determines how much the price can move before the stop loss is adjusted";

}
