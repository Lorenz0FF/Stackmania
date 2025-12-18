/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.crash;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Predicts potential crashes before they occur using pattern analysis.
 */
public class CrashPredictor {
    
    private static final Logger LOGGER = LogManager.getLogger("Stackmania/CrashPredictor");
    
    private final Map<String, Integer> crashPatterns = new ConcurrentHashMap<>();
    private final Set<String> knownCrashingPatterns = ConcurrentHashMap.newKeySet();
    
    public CrashPredictor() {
        initializeKnownPatterns();
    }
    
    private void initializeKnownPatterns() {
        // Known patterns that cause crashes
        knownCrashingPatterns.add("ConcurrentModificationException");
        knownCrashingPatterns.add("StackOverflowError");
        knownCrashingPatterns.add("OutOfMemoryError");
        knownCrashingPatterns.add("registry_corruption");
        knownCrashingPatterns.add("infinite_loop_detected");
        knownCrashingPatterns.add("deadlock_detected");
    }
    
    public CrashPrediction predict(Runnable operation, String context) {
        // Analyze the context for known crash patterns
        for (String pattern : knownCrashingPatterns) {
            if (context.toLowerCase().contains(pattern.toLowerCase())) {
                return new CrashPrediction(true, 95, 
                    "Known crash pattern detected: " + pattern);
            }
        }
        
        // Check historical crash frequency
        int frequency = crashPatterns.getOrDefault(context, 0);
        if (frequency > 5) {
            return new CrashPrediction(true, 80, 
                "High crash frequency for context: " + context);
        } else if (frequency > 2) {
            return new CrashPrediction(false, 50, 
                "Moderate crash risk for context: " + context, true);
        }
        
        // Check resource availability
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsage = 1.0 - ((double) freeMemory / maxMemory);
        
        if (memoryUsage > 0.95) {
            return new CrashPrediction(true, 90, 
                "Memory nearly exhausted: " + String.format("%.1f%%", memoryUsage * 100));
        } else if (memoryUsage > 0.85) {
            return new CrashPrediction(false, 40, 
                "Memory usage high: " + String.format("%.1f%%", memoryUsage * 100), true);
        }
        
        // Safe to proceed
        return new CrashPrediction(false, 0, null);
    }
    
    public void recordCrash(String context) {
        crashPatterns.merge(context, 1, Integer::sum);
        LOGGER.debug("Recorded crash for pattern: {} (total: {})", 
            context, crashPatterns.get(context));
    }
    
    public void recordSuccess(String context) {
        // Decay crash counter on success
        crashPatterns.computeIfPresent(context, (k, v) -> Math.max(0, v - 1));
    }
}

class CrashPrediction {
    private final boolean willCrash;
    private final int confidence;
    private final String reason;
    private final boolean risky;
    
    public CrashPrediction(boolean willCrash, int confidence, String reason) {
        this(willCrash, confidence, reason, false);
    }
    
    public CrashPrediction(boolean willCrash, int confidence, String reason, boolean risky) {
        this.willCrash = willCrash;
        this.confidence = confidence;
        this.reason = reason;
        this.risky = risky;
    }
    
    public boolean willCrash() { return willCrash; }
    public int getConfidence() { return confidence; }
    public String getReason() { return reason; }
    public boolean isRisky() { return risky || (confidence > 30 && confidence < 70); }
}
