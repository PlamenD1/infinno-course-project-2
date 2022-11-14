package config.environment;

import java.util.HashMap;
import java.util.Map;

public class DataSource {
    public String type;
    public Map<String, String> activeProperties;

    public DataSource() {
        activeProperties = new HashMap<>();
    }
}
