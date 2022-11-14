package config.mapper;

import config.cache.MapperCache;

import java.util.HashMap;
import java.util.Map;

public class Mapper {
    public Map<String, ResultMap> resultMaps;
    public Map<String, Query> queries;
    public String fileName;
    public String namespace;

    public MapperCache<String, Object> cache;

    public Mapper() {
        this.resultMaps = new HashMap<>();
        this.queries = new HashMap<>();
    }
}
