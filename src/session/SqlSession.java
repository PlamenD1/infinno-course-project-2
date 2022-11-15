package session;

import com.sun.jdi.connect.spi.ClosedConnectionException;
import config.mapper.CacheKey;
import config.mapper.Mapper;
import config.mapper.Query;
import config.mapper.ResultMap;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlSession implements Closeable {
    Connection conn;
    Map<String, Mapper> mappers;
    boolean closed = false;

    SqlSession(Connection conn, Map<String, Mapper> mappers) {
        this.conn = conn;
        this.mappers = mappers;
    }

    private final Pattern paramPattern = Pattern.compile("\\#\\{([A-Za-z0-9]+)\\}");
    final static Set<Class<?>> wrapperClasses = Set.of(Double.class, Float.class, Integer.class, String.class);

    public static boolean isPrimitiveOrWrapperOrString(Class<?> type) {
        return type.isPrimitive() || wrapperClasses.contains(type);
    }

    @SuppressWarnings("unchecked")
    public <T> T selectOne(String methodId, Object params) throws Exception {
        if (closed)
            throw new ClosedConnectionException("The sql session is closed!");

        Mapper mapper = getMapper(methodId);
        Query query = mapper.queries.get(methodId);
        PreparedStatement ps = getPreparedStatement(query.sql, query.paramNames, params, conn);
        CacheKey cacheKey = new CacheKey(methodId, params);

        if (mapper.cache != null && mapper.cache.get(cacheKey) != null && query.useCache) {
            return (T) mapper.cache.get(cacheKey);
        }

        System.out.println("GETTING FROM DATABASE...");
        ResultSet rs = ps.executeQuery();
        T result = null;
        Map<String, Field> fieldNamePairs = null;
        if (query.returnType == null) {
            if (query.resultMap == null)
                throw new IllegalStateException("There must be returnType or resultMap on queries!");

            ResultMap rm = mapper.resultMaps.get(query.resultMap);
            if (rs.next()) {
                result = (T) setObjectFieldsThruResultMap(rs, rm);
            }
        }
        else {
            fieldNamePairs = getNameFieldPairs(query.returnType);

            if (rs.next()) {
                result = (T) setObjectFields(rs, query.returnType, fieldNamePairs);
            }
        }

        if (mapper.cache != null) {
            mapper.cache.set(cacheKey, result);
        }

        if (rs.next())
            throw new IllegalStateException("Result set must have 1 row!");

        return result;
    }

    public <T> T selectOne(String methodId) throws Exception {
        return selectOne(methodId, null);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> selectList(String methodId, Object params) throws Exception {
        if (closed)
            throw new ClosedConnectionException("The sql session is closed!");

        Mapper mapper = getMapper(methodId);
        Query query = mapper.queries.get(methodId);
        PreparedStatement ps = getPreparedStatement(query.sql, query.paramNames, params, conn);
        CacheKey cacheKey = new CacheKey(methodId, params);

        if (mapper.cache != null && mapper.cache.get(cacheKey) != null && query.useCache) {
            return (List<T>) mapper.cache.get(cacheKey);
        }

        ResultSet rs = ps.executeQuery();

        List<T> result = new ArrayList<>();
        if (query.resultMap == null)
            throw new IllegalStateException("There must be resultMap on queries with List result!");

        ResultMap rm = mapper.resultMaps.get(query.resultMap);
        while (rs.next()) {
            result.add((T) setObjectFieldsThruResultMap(rs, rm));
        }

        if (mapper.cache != null) {
            mapper.cache.set(cacheKey, result);
        }

        return result;
    }

    public <T> List<T> selectList(String methodId) throws Exception {
        return selectList(methodId, null);
    }

    Mapper getMapper(String methodId) throws Exception {
        if (closed)
            throw new ClosedConnectionException("The sql session is closed!");

        String[] methodArgs = methodId.split("\\.");
        Mapper mapper = null;
        if (methodArgs.length > 1) {
            if (!mappers.containsKey(methodArgs[0]))
                throw new IllegalArgumentException("There is no mapper with namespace: " + methodArgs[0]);

            mapper = mappers.get(methodArgs[0]);
            if (!mapper.queries.containsKey(methodId))
                throw new IllegalArgumentException("There is no method with id: " + methodId);
        } else {
            if (mappers.size() > 1) {
                for (Mapper m : mappers.values()) {
                    if (!m.queries.containsKey(methodId) || m.fileName == null)
                        continue;

                    if (mapper != null)
                        throw new IllegalArgumentException(methodId + " is ambiguous!");

                    mapper = m;
                }
            } else {
                mapper = mappers.values().iterator().next();
            }
        }

        return mapper;
    }

    @SuppressWarnings("unchecked")
    public <T> T getMapper(Class<T> clazz) throws Exception {
        if (closed)
            throw new ClosedConnectionException("The sql session is closed!");

        MapperHandler handler = new MapperHandler(this);
        return (T) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class[] { clazz } , handler);
    }

    String getTransformedSql(String sql, List<String> paramNames) {
        Matcher matcher = paramPattern.matcher(sql);

        return matcher.replaceAll(matchResult -> {
            paramNames.add(matchResult.group(1));
            return "?";
        });
    }

    Map<String, Field> getNameFieldPairs(Class<?> c) {
        Map<String, Field> nameFieldPairs = new HashMap<>();
        for (Field f : c.getDeclaredFields()) {
            String fieldName = normalizeName(f.getName());
            nameFieldPairs.put(fieldName, f);
        }

        return nameFieldPairs;
    }

    int alterQueries(String methodId, Object params) throws Exception {
        if (closed)
            throw new ClosedConnectionException("The sql session is closed!");

        Mapper mapper = getMapper(methodId);
        if (mapper.cache != null)
            mapper.cache.reset();

        Query query = mapper.queries.get(methodId);
        List<String> paramNames = new ArrayList<>();
        PreparedStatement ps = getPreparedStatement(query.sql, query.paramNames, params, conn);

        return ps.executeUpdate();
    }

    public int update(String methodId, Object params) throws Exception {
        return alterQueries(methodId, params);
    }

    public int insert(String methodId, Object params) throws Exception {
        return alterQueries(methodId, params);
    }

    public int delete(String methodId, Object params) throws Exception {
        return alterQueries(methodId, params);
    }

    static <T> T setObjectFields(ResultSet rs, Class<T> c, Map<String, Field> nameFieldPairs) throws Exception {
        int columnsCount = rs.getMetaData().getColumnCount();
        T object = c.getDeclaredConstructor().newInstance();
        for (int j = 1; j <= columnsCount; j++) {
            String fieldName = rs.getMetaData().getColumnName(j);
            fieldName = normalizeName(fieldName);
            Field f = nameFieldPairs.get(fieldName);
            if (f != null) {
                f.set(object, rs.getObject(j));
            }
        }

        return object;
    }

    @SuppressWarnings("unchecked")
    static <T> T setObjectFieldsThruResultMap(ResultSet rs, ResultMap rm) throws Exception {
        Class<?> returnType = rm.returnType;
        T object = (T) returnType.getDeclaredConstructor().newInstance();

        for (var entry : rm.propsToColumns.entrySet()) {
            returnType.getField(entry.getKey()).set(object, rs.getObject(entry.getValue()));
        }

        return object;
    }

    static String normalizeName(String s) {
        return s.replace("_", "").toLowerCase();
    }

    public PreparedStatement getPreparedStatement(String sql, List<String> paramNames, Object params, Connection connection) throws NoSuchFieldException, IllegalAccessException, SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        if (params != null) {
            Class<?> paramsClass = params.getClass();
            for (int i = 0; i < paramNames.size(); i++) {
                String paramName = paramNames.get(i);
                if (isPrimitiveOrWrapperOrString(paramsClass)) {
                    if (!Objects.equals(paramName, "value"))
                        throw new IllegalArgumentException("Invalid parameter name!");

                    preparedStatement.setObject(i + 1, params);
                } else {
                    Field f = paramsClass.getDeclaredField(paramName);
                    Object paramValue = f.get(params);
                    preparedStatement.setObject(i + 1, paramValue);
                }
            }
        }

        return preparedStatement;
    }

    @Override
    public void close() throws IOException {
        try {
            conn.close();
            closed = true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

class MapperHandler implements InvocationHandler {
    SqlSession sqlSession;

    MapperHandler(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (!sqlSession.mappers.containsKey(method.getDeclaringClass().toString()))
            throw new IllegalArgumentException("Mapper: " + method.getDeclaringClass().toString() + " does not exist!");

        Mapper mapper = sqlSession.mappers.get(method.getDeclaringClass().toString());

        if (!mapper.queries.containsKey(method.getName()))
            throw new IllegalArgumentException("Query: " + method.getName() + " does not exist in mapper: " + mapper.namespace);

        Query query = mapper.queries.get(method.getName());
        if (query.returnType.isArray()) {
            return sqlSession.selectList(method.getName(), args[0]);
        }

        switch (query.query_type) {
            case SELECT:
                return sqlSession.selectOne(method.getName(), args[0]);
            case INSERT, UPDATE, DELETE:
                return sqlSession.alterQueries(method.getName(), args[0]);
        }

        return null;
    }
}
