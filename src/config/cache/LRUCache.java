package config.cache;

import java.util.*;

public class LRUCache<K, V> implements Cache<K, V> {
        static class Element<K, V> {
            V value;
            int currentReads;
            int previousReads;
            int generation;
            LRUCache cache;
            K key;

            Element(LRUCache cache,K key, V value, int currentReads, int previousReads, int generation) {
                this.key = key;
                this.value = value;
                this.currentReads = currentReads;
                this.previousReads = previousReads;
                this.generation = generation;
                this.cache = cache;
            }

            V getValue() {
                checkGen();
                return value;
            }

            void checkGen() {
                if (generation == cache.currentGen - 1) {
                    previousReads = currentReads;
                    currentReads = 0;
                    generation = cache.currentGen;
                } else if (generation < cache.currentGen) {
                    previousReads = 0;
                    currentReads = 0;
                    generation = cache.currentGen;
                }
            }
        }

        Map<K, Element<K, V>> elements;

        Queue<Element<K, V>> priorityQueue = new PriorityQueue<>(10, (e1, e2) -> {
            int e1TotalReads = e1.currentReads + e1.previousReads;
            int e2TotalReads = e2.currentReads + e2.previousReads;
            return e1TotalReads - e2TotalReads;
        });

        int capacity;
        int totalReads;
        public int currentGen = 0;

        public LRUCache(int capacity, long flushInterval) {
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

        public V get(K key) {
            Element<K, V> e = elements.get(key);
            if (e == null)
                return null;

            priorityQueue.remove(e);
            e.currentReads++;
            totalReads++;
            if (totalReads > capacity) {
                currentGen++;
                totalReads = 0;
            }
            priorityQueue.add(e);

            return e.getValue();
        }

        public void set(K key, V value) {
            Element<K, V> element = elements.get(key);
            if (elements.containsKey(key)) {
                Element<K, V> e = new Element<>(this, key, value, element.currentReads, element.currentReads, element.generation);
                elements.put(key, e);
                priorityQueue.add(e);
                return;
            }

            if (elements.size() == capacity) {
                Element<K, V> eToEvict = priorityQueue.poll();
                elements.remove(eToEvict.key);
            }

            Element<K, V> e = new Element<>(this, key, value, 0,0, 0);
            elements.put(key, e);
            priorityQueue.add(e);
        }

    @Override
    public void reset() {
        priorityQueue = new PriorityQueue<>(10, (e1, e2) -> {
            int e1TotalReads = e1.currentReads + e1.previousReads;
            int e2TotalReads = e2.currentReads + e2.previousReads;
            return e1TotalReads - e2TotalReads;
        });
        elements = new LinkedHashMap<>();
    }

    @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (var key : elements.keySet()) {
                sb.append("key: ").append(key).append(", currentReads: ").append(elements.get(key).currentReads).append(", previousReads: ").append(elements.get(key).previousReads).append(" | ");
            }

            return sb.toString();
        }


}
