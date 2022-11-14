package config.environment;

import java.util.HashMap;
import java.util.Map;

public class Environments {
    public static Environments environments = getInstance();
    public static String defaultEnvironmentId;
    public static Map<String, Environment> environmentsMap = new HashMap<>();

    Environments() {}

    public static Environments getInstance() {
        if (environments == null)
            environments = new Environments();

        return environments;
    }
}
