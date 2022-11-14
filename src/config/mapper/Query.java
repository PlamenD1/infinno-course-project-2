package config.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Query {
    private final Pattern paramPattern = Pattern.compile("#\\{([A-Za-z0-9]+)\\}");

    public enum QUERY_TYPE {SELECT, INSERT, UPDATE, DELETE};
    public QUERY_TYPE query_type;
    public List<String> paramNames;
    public Class<?> returnType;
    public String resultMap;
    public String sql;

    public Query(QUERY_TYPE query_type, String sql, Class<?> returnType, String resultMap) {
        this.query_type = query_type;
        this.paramNames = new ArrayList<>();
        this.sql = getTransformedSql(sql);
        this.returnType = returnType;
        this.resultMap = resultMap;
    }

    String getTransformedSql(String sql) {
        Matcher matcher = paramPattern.matcher(sql);

        return matcher.replaceAll(matchResult -> {
            paramNames.add(matchResult.group(1));
            return "?";
        });
    }
}