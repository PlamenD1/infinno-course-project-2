package config.environment;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    public String id;

    public DataSource dataSource;

    public Environment(String id, DataSource dataSource) {
        this.id = id;
        this.dataSource = dataSource;
    }
}
