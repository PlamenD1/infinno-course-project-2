package config;

import config.environment.Environment;
import config.environment.Environments;
import config.mapper.Mapper;

import java.util.ArrayList;
import java.util.Map;

public class Configuration {
    public Properties properties;
    public Environment environment;
    public Map<String, Mapper> mappers;

    public Configuration(Environment e, Properties p, Map<String, Mapper> mappers) {
        this.environment = e;
        this.properties = p;
        this.mappers = mappers;
    }
}