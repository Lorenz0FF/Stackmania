/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.crash;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Isolated execution context for mods.
 * Provides memory limits, crash isolation, and controlled shutdown.
 */
public class IsolatedContext {
    
    private static final Logger LOGGER = LogManager.getLogger("Stackmania/IsolatedContext");
    
    private final String modId;
    private final ExecutorService executor;
    private long memoryLimit = 256 * 1024 * 1024; // 256MB default
    private BiConsumer<String, Throwable> crashHandler;
    private volatile boolean shutdown = false;
    
    public IsolatedContext(String modId) {
        this.modId = modId;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Stackmania-Isolated-" + modId);
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) -> handleCrash(ex));
            return t;
        });
    }
    
    public void setMemoryLimit(long bytes) {
        this.memoryLimit = bytes;
    }
    
    public void setCrashHandler(BiConsumer<String, Throwable> handler) {
        this.crashHandler = handler;
    }
    
    public void execute(Runnable action) {
        if (shutdown) {
            throw new IllegalStateException("Context has been shut down");
        }
        
        Future<?> future = executor.submit(() -> {
            try {
                checkMemoryLimit();
                action.run();
            } catch (Throwable t) {
                handleCrash(t);
            }
        });
        
        try {
            future.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOGGER.error("Mod {} execution timed out", modId);
            handleCrash(new TimeoutException("Execution timed out"));
        } catch (Exception e) {
            handleCrash(e);
        }
    }
    
    public <T> T executeWithResult(Callable<T> action) {
        if (shutdown) {
            throw new IllegalStateException("Context has been shut down");
        }
        
        Future<T> future = executor.submit(() -> {
            checkMemoryLimit();
            return action.call();
        });
        
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            handleCrash(e);
            return null;
        }
    }
    
    private void checkMemoryLimit() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        
        if (usedMemory > memoryLimit) {
            throw new OutOfMemoryError("Mod " + modId + " exceeded memory limit: " + 
                usedMemory + " > " + memoryLimit);
        }
    }
    
    private void handleCrash(Throwable t) {
        LOGGER.error("Crash in isolated context {}: {}", modId, t.getMessage());
        
        if (crashHandler != null) {
            crashHandler.accept(modId, t);
        }
    }
    
    public void shutdown() {
        shutdown = true;
        executor.shutdownNow();
        LOGGER.info("Isolated context {} shut down", modId);
    }
    
    public boolean isShutdown() {
        return shutdown;
    }
    
    public String getModId() {
        return modId;
    }
}
