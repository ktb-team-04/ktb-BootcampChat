package com.ktb.chatapp.websocket.socketio;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local in-memory implementation of ChatDataStore using ConcurrentHashMap.
 * Thread-safe storage for chat-related data without external dependencies.
 */
public class LocalChatDataStore implements ChatDataStore {
    
    private final ConcurrentHashMap<String, Object> storage = new ConcurrentHashMap<>();
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = storage.get(key);
        if (value == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(type.cast(value));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public void set(String key, Object value) {
        storage.put(key, value);
    }
    
    @Override
    public void delete(String key) {
        storage.remove(key);
    }
    
    @Override
    public int size(String keyPrefix) {
        return Math.toIntExact(storage.keySet().stream()
                .filter(key -> key.startsWith(keyPrefix))
                .count());
    }

    @Override
    public Set<String> getSet(String key) {
        Object value = storage.get(key);
        if (!(value instanceof Set<?> values)) {
            return Set.of();
        }
        return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public void addToSet(String key, String value) {
        storage.compute(key, (ignored, current) -> {
            Set<String> values = ConcurrentHashMap.newKeySet();
            if (current instanceof Set<?> currentValues) {
                currentValues.forEach(item -> values.add(String.valueOf(item)));
            }
            values.add(value);
            return values;
        });
    }

    @Override
    public void removeFromSet(String key, String value) {
        storage.computeIfPresent(key, (ignored, current) -> {
            if (!(current instanceof Set<?> currentValues)) {
                return current;
            }
            Set<String> values = ConcurrentHashMap.newKeySet();
            currentValues.forEach(item -> values.add(String.valueOf(item)));
            values.remove(value);
            return values.isEmpty() ? null : values;
        });
    }
}
