/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.crash;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages server state snapshots for crash recovery.
 * Enables instant rollback to last known good state.
 */
public class StateManager {
    
    private static final Logger LOGGER = LogManager.getLogger("Stackmania/StateManager");
    
    private final AtomicLong snapshotIdCounter = new AtomicLong(0);
    private final Map<String, Object> currentState = new ConcurrentHashMap<>();
    private volatile boolean corrupted = false;
    private StateSnapshot lastValidSnapshot;
    
    public StateSnapshot captureSnapshot(String reason) {
        long id = snapshotIdCounter.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        
        // Capture current state
        Map<String, Object> stateCopy = new HashMap<>(currentState);
        
        // Capture memory info
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Capture thread info
        int activeThreads = Thread.activeCount();
        
        StateSnapshot snapshot = new StateSnapshot(
            id, 
            timestamp, 
            reason, 
            stateCopy, 
            usedMemory, 
            activeThreads
        );
        
        // Validate snapshot
        if (validateSnapshot(snapshot)) {
            lastValidSnapshot = snapshot;
        }
        
        return snapshot;
    }
    
    public void rollback(StateSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Cannot rollback to null snapshot");
        }
        
        LOGGER.info("Rolling back to snapshot {} ({})", snapshot.getId(), snapshot.getReason());
        
        long startTime = System.currentTimeMillis();
        
        // Restore state
        currentState.clear();
        currentState.putAll(snapshot.getState());
        
        // Clear corruption flag
        corrupted = false;
        
        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("Rollback completed in {}ms", elapsed);
    }
    
    public void setState(String key, Object value) {
        currentState.put(key, value);
    }
    
    public Object getState(String key) {
        return currentState.get(key);
    }
    
    public void markCorrupted(String reason) {
        corrupted = true;
        LOGGER.error("State marked as corrupted: {}", reason);
    }
    
    public boolean isCorrupted() {
        return corrupted;
    }
    
    public StateSnapshot getLastValidSnapshot() {
        return lastValidSnapshot;
    }
    
    private boolean validateSnapshot(StateSnapshot snapshot) {
        // Basic validation
        if (snapshot.getState() == null) {
            return false;
        }
        
        // Check memory sanity
        if (snapshot.getUsedMemory() < 0) {
            return false;
        }
        
        return true;
    }
}

class StateSnapshot {
    private final long id;
    private final long timestamp;
    private final String reason;
    private final Map<String, Object> state;
    private final long usedMemory;
    private final int activeThreads;
    private boolean valid = true;
    
    public StateSnapshot(long id, long timestamp, String reason, 
                         Map<String, Object> state, long usedMemory, int activeThreads) {
        this.id = id;
        this.timestamp = timestamp;
        this.reason = reason;
        this.state = state;
        this.usedMemory = usedMemory;
        this.activeThreads = activeThreads;
    }
    
    public long getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public String getReason() { return reason; }
    public Map<String, Object> getState() { return state; }
    public long getUsedMemory() { return usedMemory; }
    public int getActiveThreads() { return activeThreads; }
    public boolean isValid() { return valid; }
    public void invalidate() { valid = false; }
    
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }
}
