/*
 * Stackmania - Valonia Games
 * Copyright (C) 2024-2025.
 */

package com.stackmania.compatibility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of translators between Forge and Bukkit types.
 * Provides seamless conversion between equivalent types from both APIs.
 */
public class TranslatorRegistry {
    
    private static final Logger LOGGER = LogManager.getLogger("Stackmania/Translators");
    
    private static final Map<TranslatorKey, Translator> translators = new ConcurrentHashMap<>();
    
    static {
        initializeBuiltinTranslators();
    }
    
    private static void initializeBuiltinTranslators() {
        // Item translators
        // register(ForgeItem.class, ItemStack.class, new ForgeItemToItemStackTranslator());
        
        // Entity translators
        // register(ForgeEntity.class, Entity.class, new ForgeEntityToEntityTranslator());
        
        // World translators
        // register(ForgeLevel.class, World.class, new ForgeLevelToWorldTranslator());
        
        // Block translators
        // register(ForgeBlock.class, Block.class, new ForgeBlockToBlockTranslator());
        
        LOGGER.info("Built-in translators initialized");
    }
    
    public static <S, T> void register(Class<S> sourceClass, Class<T> targetClass, Translator translator) {
        TranslatorKey key = new TranslatorKey(sourceClass, targetClass);
        translators.put(key, translator);
        LOGGER.debug("Registered translator: {} -> {}", sourceClass.getSimpleName(), targetClass.getSimpleName());
    }
    
    public static Translator getTranslator(Class<?> sourceClass, Class<?> targetClass) {
        TranslatorKey key = new TranslatorKey(sourceClass, targetClass);
        Translator translator = translators.get(key);
        
        if (translator == null) {
            // Try to find a translator for a superclass
            translator = findSuperclassTranslator(sourceClass, targetClass);
        }
        
        return translator;
    }
    
    private static Translator findSuperclassTranslator(Class<?> sourceClass, Class<?> targetClass) {
        Class<?> current = sourceClass.getSuperclass();
        while (current != null && current != Object.class) {
            TranslatorKey key = new TranslatorKey(current, targetClass);
            Translator translator = translators.get(key);
            if (translator != null) {
                return translator;
            }
            current = current.getSuperclass();
        }
        
        // Try interfaces
        for (Class<?> iface : sourceClass.getInterfaces()) {
            TranslatorKey key = new TranslatorKey(iface, targetClass);
            Translator translator = translators.get(key);
            if (translator != null) {
                return translator;
            }
        }
        
        return null;
    }
    
    public static void unregister(Class<?> sourceClass, Class<?> targetClass) {
        TranslatorKey key = new TranslatorKey(sourceClass, targetClass);
        translators.remove(key);
    }
    
    public static int getRegisteredCount() {
        return translators.size();
    }
    
    private static class TranslatorKey {
        private final Class<?> sourceClass;
        private final Class<?> targetClass;
        
        public TranslatorKey(Class<?> source, Class<?> target) {
            this.sourceClass = source;
            this.targetClass = target;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TranslatorKey that = (TranslatorKey) o;
            return sourceClass.equals(that.sourceClass) && targetClass.equals(that.targetClass);
        }
        
        @Override
        public int hashCode() {
            return 31 * sourceClass.hashCode() + targetClass.hashCode();
        }
    }
}

/**
 * Interface for type translators
 */
interface Translator {
    Object translate(Object source);
}
