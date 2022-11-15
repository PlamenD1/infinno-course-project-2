package config.cache;

import config.mapper.Mapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class FIFOCache<K, V> implements Cache<K, V> {
    static class Element<V> {
        V value;
        Element(V value) {
            this.value = value;
        }

        V getValue() {
            return value;
        }
    }

    public Map<K, Element<V>> elements;

    public int capacity;

    public FIFOCache(int capacity, long flushInterval) {
        this.elements = new LinkedHashMap<>();
        this.capacity = capacity;
        setNewTimer(flushInterval);
    }

    void setNewTimer(long flushInterval) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                reset();
            }
        }, flushInterval, flushInterval);
    }

    public void reset() {
        elements = new LinkedHashMap<>();
    }

    public V get(K key) {
        Element<V> e = elements.get(key);
        if (e == null)
            return null;

        return e.getValue();
    }

    public void set(K key, V value) {
        Element<V> element = elements.get(key);
        if (elements.containsKey(key)) {
            Element<V> e = new Element<>(value);
            elements.put(key, e);
            return;
        }

        if (elements.size() == capacity) {
            elements.remove(elements.keySet().iterator().next());
        }

        Element<V> e = new Element<>(value);
        elements.put(key, e);
    }
}