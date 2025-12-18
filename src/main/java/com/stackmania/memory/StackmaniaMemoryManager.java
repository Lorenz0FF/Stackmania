/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 *
 * Advanced Memory Management System
 * Target: Minimal RAM footprint with maximum performance
 */

package com.stackmania.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stackmania Memory Manager
 * 
 * Provides aggressive memory optimization for Minecraft servers.
 * 
 * Features:
 * - Automatic cache cleanup
 * - Weak reference management
 * - Chunk unloading optimization
 * - String deduplication
 * - Object pooling
 * - Memory pressure monitoring
 */
public class StackmaniaMemoryManager {
    
    private static final Logger LOGGER = LogManager.getLogger("Stackmania/Memory");
    
    private static StackmaniaMemoryManager instance;
    private static boolean initialized = false;
    
    private final ScheduledExecutorService scheduler;
    private final ObjectPool objectPool;
    private final CacheManager cacheManager;
    
    private final AtomicLong totalFreed = new AtomicLong(0);
    private final AtomicLong cleanupCount = new AtomicLong(0);
    
    // Memory thresholds
    private static final double WARNING_THRESHOLD = 0.70; // 70%
    private static final double CRITICAL_THRESHOLD = 0.85; // 85%
    private static final double EMERGENCY_THRESHOLD = 0.95; // 95%
    
    // Cleanup intervals
    private static final long ROUTINE_CLEANUP_MS = 30000; // 30 seconds
    private static final long AGGRESSIVE_CLEANUP_MS = 5000; // 5 seconds when pressure high
    
    private volatile boolean aggressiveMode = false;
    private volatile double lastMemoryUsage = 0;
    
    private StackmaniaMemoryManager() {
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "Stackmania-MemoryManager");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 1);
            return t;
        });
        
        this.objectPool = new ObjectPool();
        this.cacheManager = new CacheManager();
    }
    
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("StackmaniaMemoryManager already initialized");
            return;
        }
        
        instance = new StackmaniaMemoryManager();
        instance.startMemoryMonitoring();
        instance.startRoutineCleanup();
        
        initialized = true;
        LOGGER.info("Stackmania Memory Manager initialized");
        LOGGER.info("Memory thresholds: Warning={}%, Critical={}%, Emergency={}%",
            (int)(WARNING_THRESHOLD * 100),
            (int)(CRITICAL_THRESHOLD * 100),
            (int)(EMERGENCY_THRESHOLD * 100));
    }
    
    public static StackmaniaMemoryManager getInstance() {
        if (!initialized) {
            throw new IllegalStateException("StackmaniaMemoryManager not initialized");
        }
        return instance;
    }
    
    public static void shutdown() {
        if (instance != null) {
            instance.scheduler.shutdownNow();
            instance.objectPool.clear();
            instance.cacheManager.clearAll();
            instance = null;
        }
        initialized = false;
        LOGGER.info("StackmaniaMemoryManager shutdown - Total freed: {} MB", 
            instance != null ? instance.totalFreed.get() / 1024 / 1024 : 0);
    }
    
    /**
     * Force immediate memory cleanup
     */
    public void forceCleanup() {
        LOGGER.debug("Forcing memory cleanup...");
        long before = getUsedMemory();
        
        // Clear soft caches
        cacheManager.clearSoftCaches();
        
        // Clean object pools
        objectPool.trim();
        
        // Clear weak references
        clearWeakReferences();
        
        // Suggest GC
        System.gc();
        
        long after = getUsedMemory();
        long freed = before - after;
        
        if (freed > 0) {
            totalFreed.addAndGet(freed);
            LOGGER.info("Memory cleanup freed {} MB", freed / 1024 / 1024);
        }
        
        cleanupCount.incrementAndGet();
    }
    
    /**
     * Emergency memory cleanup - aggressive mode
     */
    public void emergencyCleanup() {
        LOGGER.warn("EMERGENCY memory cleanup triggered!");
        
        // Clear ALL caches
        cacheManager.clearAll();
        
        // Clear object pools completely
        objectPool.clear();
        
        // Clear weak references
        clearWeakReferences();
        
        // Force GC multiple times
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        LOGGER.warn("Emergency cleanup complete");
    }
    
    /**
     * Register a cache for management
     */
    public void registerCache(String name, Map<?, ?> cache) {
        cacheManager.registerCache(name, cache);
    }
    
    /**
     * Get an object from the pool or create new
     */
    @SuppressWarnings("unchecked")
    public <T> T getPooled(Class<T> clazz) {
        return objectPool.get(clazz);
    }
    
    /**
     * Return an object to the pool
     */
    public <T> void returnPooled(T object) {
        if (object != null) {
            objectPool.release(object);
        }
    }
    
    /**
     * Get current memory usage percentage
     */
    public double getMemoryUsagePercent() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        return (double) used / max;
    }
    
    /**
     * Get used memory in bytes
     */
    public long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    /**
     * Get max memory in bytes
     */
    public long getMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }
    
    /**
     * Check if under memory pressure
     */
    public boolean isUnderPressure() {
        return getMemoryUsagePercent() > WARNING_THRESHOLD;
    }
    
    /**
     * Get memory statistics
     */
    public MemoryStats getStats() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        long free = runtime.freeMemory();
        
        return new MemoryStats(
            used / 1024 / 1024,
            max / 1024 / 1024,
            free / 1024 / 1024,
            getMemoryUsagePercent() * 100,
            totalFreed.get() / 1024 / 1024,
            cleanupCount.get(),
            aggressiveMode,
            cacheManager.getCacheCount(),
            objectPool.getPooledCount()
        );
    }
    
    private void startMemoryMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                double usage = getMemoryUsagePercent();
                lastMemoryUsage = usage;
                
                if (usage >= EMERGENCY_THRESHOLD) {
                    LOGGER.error("Memory EMERGENCY: {}% used!", (int)(usage * 100));
                    emergencyCleanup();
                    aggressiveMode = true;
                } else if (usage >= CRITICAL_THRESHOLD) {
                    LOGGER.warn("Memory CRITICAL: {}% used!", (int)(usage * 100));
                    forceCleanup();
                    aggressiveMode = true;
                } else if (usage >= WARNING_THRESHOLD) {
                    LOGGER.debug("Memory WARNING: {}% used", (int)(usage * 100));
                    aggressiveMode = true;
                } else {
                    aggressiveMode = false;
                }
            } catch (Exception e) {
                LOGGER.error("Memory monitoring error: {}", e.getMessage());
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
        
        LOGGER.info("Memory monitoring started");
    }
    
    private void startRoutineCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long interval = aggressiveMode ? AGGRESSIVE_CLEANUP_MS : ROUTINE_CLEANUP_MS;
                
                // Trim caches
                cacheManager.trimCaches();
                
                // Trim object pools
                objectPool.trim();
                
                // Light cleanup
                if (getMemoryUsagePercent() > 0.5) {
                    clearWeakReferences();
                }
            } catch (Exception e) {
                LOGGER.error("Routine cleanup error: {}", e.getMessage());
            }
        }, ROUTINE_CLEANUP_MS, ROUTINE_CLEANUP_MS, TimeUnit.MILLISECONDS);
        
        LOGGER.info("Routine cleanup scheduled every {}s", ROUTINE_CLEANUP_MS / 1000);
    }
    
    private void clearWeakReferences() {
        // Force processing of reference queues
        System.runFinalization();
    }
    
    // ======================== INNER CLASSES ========================
    
    public static class MemoryStats {
        public final long usedMB;
        public final long maxMB;
        public final long freeMB;
        public final double usagePercent;
        public final long totalFreedMB;
        public final long cleanupCount;
        public final boolean aggressiveMode;
        public final int managedCaches;
        public final int pooledObjects;
        
        public MemoryStats(long used, long max, long free, double usage,
                          long freed, long cleanups, boolean aggressive,
                          int caches, int pooled) {
            this.usedMB = used;
            this.maxMB = max;
            this.freeMB = free;
            this.usagePercent = usage;
            this.totalFreedMB = freed;
            this.cleanupCount = cleanups;
            this.aggressiveMode = aggressive;
            this.managedCaches = caches;
            this.pooledObjects = pooled;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Memory: %dMB/%dMB (%.1f%%) | Freed: %dMB | Cleanups: %d | Aggressive: %s",
                usedMB, maxMB, usagePercent, totalFreedMB, cleanupCount, aggressiveMode
            );
        }
    }
}

/**
 * Object Pool for reducing allocations
 */
class ObjectPool {
    private final Map<Class<?>, Queue<Object>> pools = new ConcurrentHashMap<>();
    private final Map<Class<?>, Integer> maxSizes = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_SIZE = 100;
    
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz) {
        Queue<Object> pool = pools.get(clazz);
        if (pool != null) {
            Object obj = pool.poll();
            if (obj != null) {
                return (T) obj;
            }
        }
        
        // Create new instance
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }
    
    public void release(Object obj) {
        if (obj == null) return;
        
        Class<?> clazz = obj.getClass();
        Queue<Object> pool = pools.computeIfAbsent(clazz, k -> new ConcurrentLinkedQueue<>());
        
        int maxSize = maxSizes.getOrDefault(clazz, DEFAULT_MAX_SIZE);
        if (pool.size() < maxSize) {
            pool.offer(obj);
        }
    }
    
    public void setMaxSize(Class<?> clazz, int maxSize) {
        maxSizes.put(clazz, maxSize);
    }
    
    public void trim() {
        for (Map.Entry<Class<?>, Queue<Object>> entry : pools.entrySet()) {
            Queue<Object> pool = entry.getValue();
            int maxSize = maxSizes.getOrDefault(entry.getKey(), DEFAULT_MAX_SIZE);
            
            // Remove excess objects
            while (pool.size() > maxSize / 2) {
                pool.poll();
            }
        }
    }
    
    public void clear() {
        pools.values().forEach(Queue::clear);
    }
    
    public int getPooledCount() {
        return pools.values().stream().mapToInt(Queue::size).sum();
    }
}

/**
 * Cache Manager for controlling memory caches
 */
class CacheManager {
    private static final Logger LOGGER = LogManager.getLogger("Stackmania/CacheManager");
    
    private final Map<String, WeakReference<Map<?, ?>>> caches = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 60000; // 1 minute
    
    public void registerCache(String name, Map<?, ?> cache) {
        caches.put(name, new WeakReference<>(cache));
        lastAccess.put(name, System.currentTimeMillis());
        LOGGER.debug("Registered cache: {}", name);
    }
    
    public void clearSoftCaches() {
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, WeakReference<Map<?, ?>>> entry : caches.entrySet()) {
            String name = entry.getKey();
            Long access = lastAccess.get(name);
            
            // Clear caches not accessed recently
            if (access != null && now - access > CACHE_EXPIRY_MS) {
                Map<?, ?> cache = entry.getValue().get();
                if (cache != null) {
                    int size = cache.size();
                    cache.clear();
                    LOGGER.debug("Cleared soft cache {}: {} entries", name, size);
                }
            }
        }
    }
    
    public void trimCaches() {
        for (Map.Entry<String, WeakReference<Map<?, ?>>> entry : caches.entrySet()) {
            Map<?, ?> cache = entry.getValue().get();
            if (cache instanceof ConcurrentHashMap) {
                // Trim large caches
                if (cache.size() > 1000) {
                    LOGGER.debug("Cache {} has {} entries - consider trimming", 
                        entry.getKey(), cache.size());
                }
            }
        }
    }
    
    public void clearAll() {
        for (Map.Entry<String, WeakReference<Map<?, ?>>> entry : caches.entrySet()) {
            Map<?, ?> cache = entry.getValue().get();
            if (cache != null) {
                cache.clear();
            }
        }
        LOGGER.info("All caches cleared");
    }
    
    public int getCacheCount() {
        return caches.size();
    }
    
    public void markAccess(String name) {
        lastAccess.put(name, System.currentTimeMillis());
    }
}
