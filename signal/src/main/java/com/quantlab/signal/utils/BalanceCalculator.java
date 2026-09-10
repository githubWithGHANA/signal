package com.quantlab.signal.utils;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class BalanceCalculator {

    public Map<String, Integer> calculateBalanceDelta(double callDelta, double putDelta, String underlying, boolean isHedge) {
        // Round the input deltas to 2 decimal places for consistent calculations
        callDelta = Math.round(callDelta * 100.0) / 100.0;
        putDelta = Math.round(putDelta * 100.0) / 100.0;

        final int MAX_QUANTITY = 10;

        int bestCallQty = 0;
        int bestPutQty = 0;
        double minDeltaDiff = Double.MAX_VALUE;

        for (int callQty = 1; callQty <= MAX_QUANTITY; callQty++) {
            for (int putQty = 1; putQty <= MAX_QUANTITY; putQty++) {
                double netDelta = (callDelta * callQty) + (putDelta * putQty);
                netDelta = Math.round(netDelta * 100.0) / 100.0;

                if (Math.abs(netDelta) < minDeltaDiff) {
                    minDeltaDiff = Math.abs(netDelta);
                    bestCallQty = callQty;
                    bestPutQty = putQty;
                }
            }
        }

        // smaller delta → higher quantity
        int finalCallQty = bestCallQty;
        int finalPutQty = bestPutQty;

        if (Math.abs(callDelta) > Math.abs(putDelta)) {
            // if call has larger delta, give call the smaller quantity
//            finalCallQty = Math.min(bestCallQty, bestPutQty);
//            finalPutQty = Math.max(bestCallQty, bestPutQty);
            if ("BANKNIFTY".equalsIgnoreCase(underlying) && !isHedge) {
                finalCallQty = 3;
                finalPutQty = 2;
            } else {
                finalCallQty = 1;
                finalPutQty = 2;
            }
        } else if (Math.abs(putDelta) > Math.abs(callDelta)) {
//            // if put has larger delta, give put the smaller quantity
//            finalPutQty = Math.min(bestCallQty, bestPutQty);
//            finalCallQty = Math.max(bestCallQty, bestPutQty);
            if ("BANKNIFTY".equalsIgnoreCase(underlying) && !isHedge) {
                finalPutQty = 3;
                finalCallQty = 2;
            } else {
                finalCallQty = 2;
                finalPutQty = 1;
            }
        }

        System.out.println("Call Delta: " + callDelta);
        System.out.println("Put Delta: " + putDelta);
        System.out.println("Best Call Qty (adjusted): " + finalCallQty);
        System.out.println("Best Put Qty (adjusted): " + finalPutQty);
        System.out.println("Smallest Net Delta Difference: " + minDeltaDiff);

        Map<String, Integer> result = new HashMap<>();
        result.put("call", finalCallQty);
        result.put("put", finalPutQty);
        result.put("difference", (int) (minDeltaDiff * 100));
        return result;
    }


//    public Map<String, Integer> calculateBalanceDelta(double callDelta, double putDelta) {
//        // Round the input deltas to 2 decimal places for consistent calculations
//        callDelta = Math.round(callDelta * 100.0) / 100.0;
//        putDelta = Math.round(putDelta * 100.0) / 100.0;
//
//        final int MAX_QUANTITY = 10; // Limit search to quantities from 1 to 10
//
//        int bestCallQty = 0;
//        int bestPutQty = 0;
//        double minDeltaDiff = Double.MAX_VALUE; // Start with the highest possible difference
//
//        // Try all combinations of call and put quantities to find the best delta balance
//        for (int callQty = 1; callQty <= MAX_QUANTITY; callQty++) {
//            for (int putQty = 1; putQty <= MAX_QUANTITY; putQty++) {
//                // Calculate the net delta for this combination
//                double netDelta = (callDelta * callQty) + (putDelta * putQty);
//                netDelta = Math.round(netDelta * 100.0) / 100.0; // Round to 2 decimals
//                // Check if this combination gives a smaller delta difference
//                if (Math.abs(netDelta) < minDeltaDiff) {
//                    minDeltaDiff = Math.abs(netDelta);
//                    bestCallQty = callQty;
//                    bestPutQty = putQty;
//                }
//            }
//        }
//
//        // Print the best combination and the difference
//        System.out.println("Call Delta: " + callDelta);
//        System.out.println("Put Delta: " + putDelta);
//        System.out.println("Best Call Qty: " + bestCallQty);
//        System.out.println("Best Put Qty: " + bestPutQty);
//        System.out.println("Smallest Net Delta Difference: " + minDeltaDiff);
//        // Prepare and return the result as a map
//        Map<String, Integer> result = new HashMap<>();
//        result.put("call", bestCallQty);
//        result.put("put", bestPutQty);
//        result.put("difference", (int) (minDeltaDiff * 100)); // scaled to avoid decimal issues
//        return result;
//    }


//    public Map<String, Integer> calculateBalanceDelta(int callDelta, int putDelta) {
//        int gcdValue = gcd(callDelta, putDelta);
//        int newCallDelta = callDelta;
//        int newPutDelta = putDelta;
//        if (gcdValue == 1) {
//            while (gcdValue == 1) {
//                if (callDelta>putDelta){
//                    if (putDelta %2 ==1){
//                        newPutDelta--;
//                    }
//                    newCallDelta--;
//                }else {
//                    if (callDelta %2 == 1){
//                        newCallDelta--;
//                    }
//                    newPutDelta--;
//                }
//                gcdValue = gcd(newCallDelta , newPutDelta);
//            }
//        }
//
//        int callAverage = (int) Math.ceil((double) newCallDelta / gcdValue);
//        int putAverage = (int) Math.ceil((double) newPutDelta / gcdValue);
//
//        while (callAverage >= 6) {
//            callAverage = (int) Math.ceil((double) callAverage / gcdValue);
//        }
//
//        while (putAverage >= 6) {
//            putAverage = (int) Math.ceil((double) putAverage / gcdValue);
//        }
//
//        Map<String, Integer> result = new HashMap<>();
//        if (callAverage == putAverage ) {
//            if (callDelta<putDelta){
//                result.put("call", callAverage+1);
//                result.put("put", putAverage);
//                result.put("gcd_value", gcdValue);
//                return result;
//            }else {
//                result.put("call", callAverage);
//                result.put("put", putAverage+1);
//                result.put("gcd_value", gcdValue);
//                return result;
//            }
//        }else {
//            result.put("put", callAverage);
//            result.put("call", putAverage);
//            result.put("gcd_value", gcdValue);
//            return result;
//        }
//    }

    private int gcd(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }
}
