package config.cache;

import java.util.HashMap;
import java.util.Map;

public interface  Cache<K, V> {
    public Map elements = new HashMap();
    public V get(K key);
    public void set(K key, V value);

    public void reset();
}
