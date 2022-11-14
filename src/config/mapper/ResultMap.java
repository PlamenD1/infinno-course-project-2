package config.mapper;

import java.util.HashMap;
import java.util.Map;

public class ResultMap {
    public Class<?> returnType;
    public Map<String, String> propsToColumns;

    public ResultMap() {
        propsToColumns = new HashMap<>();
    }
}
