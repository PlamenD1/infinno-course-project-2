package config;

import config.cache.Cache;
import config.cache.FIFOCache;
import config.cache.LRUCache;
import config.environment.DataSource;
import config.environment.Environment;
import config.environment.Environments;
import config.mapper.Mapper;
import config.mapper.Query;
import config.mapper.ResultMap;
import org.w3c.dom.*;
import session.annotations.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XMLConfigParser {
    public Configuration configuration;
    String defaultEnv;
    private final Pattern propertyValuePattern = Pattern.compile("\\$\\{([A-Za-z0-9]+)\\}");

    public XMLConfigParser(String path) throws Exception {
        configuration = parseXMLtoConfig(path);
    }

    public Configuration parseXMLtoConfig(String path) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(path));
        Node configNode = doc.getDocumentElement();
        if (!configNode.getNodeName().equals("configuration"))
            throw new ParserConfigurationException("Could not find element: configuration!");

        Properties props = null;
        Environments envs = null;
        Map<String, Mapper> mappers = null;

        NodeList configChildren = configNode.getChildNodes();
        for (int i = 0; i < configChildren.getLength(); i++) {
            Node node = configChildren.item(i);

            switch (node.getNodeName()) {
                case "properties":
                    props = buildProperties(node);
                    break;
                case "environments":
                    envs = buildEnvironments(node, props);
                    break;
                case "mappers":
                    mappers = buildMappers(node);
                    break;
            }
        }

        if (props == null)
            throw new ParserConfigurationException("Could not find element: properties!");

        if (envs == null)
            throw new ParserConfigurationException("Could not find element: environments!");

        if (mappers == null)
            throw new ParserConfigurationException("Could not find element: mappers!");

        return new Configuration(Environments.environmentsMap.get(defaultEnv), props, mappers);
    }

    Properties buildProperties(Node propsNode) throws Exception {
        Node resourceAttr = propsNode.getAttributes().getNamedItem("resource");
        if (resourceAttr == null)
            throw new ParserConfigurationException("Could not find attribute: resource, on element: properties!");

        Properties props = new Properties();

        String propertiesPath = resourceAttr.getTextContent();
        Map<String, String> propertiesFileProps = loadPropsFromPropsFile(new FileReader(new File(propertiesPath)));

        NodeList listedProperties = propsNode.getChildNodes();
        for (int i = 0; i < listedProperties.getLength(); i++) {
            Node prop = listedProperties.item(i);
            if (prop.getNodeName().equals("#text"))
                continue;

            String key = getAttributeValue(prop, "name");
            if (!propertiesFileProps.containsKey(key))
                throw new ParserConfigurationException("");

            String valueParam = getAttributeValue(prop, "value");
            Matcher matcher = propertyValuePattern.matcher(valueParam);
            if (!matcher.find())
                throw new ParserConfigurationException("Invalid attribute value: value, on element: property!");
            String value = matcher.toMatchResult().group(1);

            props.propertiesMap.put(key, propertiesFileProps.get(value));
        }

        return props;
    }

    Environments buildEnvironments(Node envsNode, Properties props) throws Exception {
        Environments environments = Environments.getInstance();

        defaultEnv = getAttributeValue(envsNode, "default");
        if (Objects.equals(defaultEnv, ""))
            throw new ParserConfigurationException("Attribute default on element environments cannot be empty!");

        NodeList listedEnvs = envsNode.getChildNodes();
        for (int i = 0; i < listedEnvs.getLength(); i++) {
            Node envNode = listedEnvs.item(i);
            if (!envNode.getNodeName().equals("environment"))
                continue;

            String envId = getAttributeValue(envNode, "id");
            if (envId.equals(""))
                throw new ParserConfigurationException("Attribute: id on element: environment cannot be empty!");

            DataSource dataSource = null;
            NodeList envNodeChildren = envNode.getChildNodes();
            for (int j = 0; j < envNodeChildren.getLength(); j++) {
                Node envNodeChild = envNodeChildren.item(j);
                if (!envNodeChild.getNodeName().equals("dataSource"))
                    continue;

                dataSource = new DataSource();
                String type = getAttributeValue(envNodeChild, "type");
                if (type.equals(""))
                    throw new ParserConfigurationException("Attribute: type on element: dataSource cannot be empty!");
                dataSource.type = type;

                NodeList dataSourceChildren = envNodeChild.getChildNodes();
                for (int k = 0; k < dataSourceChildren.getLength(); k++) {
                    Node propNode = dataSourceChildren.item(k);
                    if (!propNode.getNodeName().equals("property")) {
                        if (propNode.getNodeName().equals("#text"))
                            continue;

                        throw new ParserConfigurationException("");
                    }

                    String valueParam = getAttributeValue(propNode, "value");
                    Matcher matcher = propertyValuePattern.matcher(valueParam);
                    if (!matcher.find())
                        throw new ParserConfigurationException("Invalid attribute value: value, on element: property!");

                    String value = matcher.toMatchResult().group(1);

                    if (!props.propertiesMap.containsKey(value))
                        throw new ParserConfigurationException("");

                    String key = getAttributeValue(propNode, "name");
                    dataSource.activeProperties.put(key, props.propertiesMap.get(value));
                }
            }

            if (dataSource == null)
                throw new ParserConfigurationException("Could not find element: dataSource!");

            Environment env = new Environment(envId, dataSource);
            Environments.environmentsMap.put(envId, env);
        }

        return environments;
    }

    Map<String, Mapper> buildMappers(Node mappersNode) throws Exception {
        Map<String, Mapper> mappers = new HashMap<>();

        NodeList mapperNodes = mappersNode.getChildNodes();
        for (int i = 0; i < mapperNodes.getLength(); i++) {
            Node mapper = mapperNodes.item(i);
            if (!mapper.getNodeName().equals("mapper"))
                continue;


            NamedNodeMap attrs = mapper.getAttributes();
            if (attrs == null) {
                throw new ParserConfigurationException("Could not find attribute: result/class on element: mapper");
            }

            Node resourceAttr = attrs.getNamedItem("resource");
            if (resourceAttr != null) {
                String resource = getAttributeValue(mapper, "resource");
                if (resource.equals(""))
                    throw new ParserConfigurationException("Attribute: resource on element: mapper cannot be empty!");

                addMapper(resource, mappers);
                continue;
            }

            Node classAttr = attrs.getNamedItem("class");
            if (classAttr == null)
                throw new ParserConfigurationException("Could not find attribute: result/class on element: mapper");

            String clazz = getAttributeValue(mapper, "class");
            if (clazz.equals(""))
                throw new ParserConfigurationException("Attribute: class on element: mapper cannot be empty!");

            addGenericMapper(Class.forName(clazz), mappers);
        }

        return mappers;
    }

    void addMapper(String path, Map<String, Mapper> mappers) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(path));

        Node mapperNode = doc.getDocumentElement();
        if (!mapperNode.getNodeName().equals("mapper"))
            throw new ParserConfigurationException("Could not find element: mapper");

        String namespace = getAttributeValue(mapperNode, "namespace");
        if (namespace.equals(""))
            throw new ParserConfigurationException("Attribute: namespace, on element: mapper cannot be empty!");

        if (mappers.containsKey(namespace))
            throw new ParserConfigurationException("Namespace: " + namespace + " is already assigned to a mapper!");

        Mapper mapper = new Mapper();
        mapper.fileName = Path.of(path).getFileName().toString();

        NodeList mapperChildren = mapperNode.getChildNodes();
        for (int i = 0; i < mapperChildren.getLength(); i++) {
            Node mapperChild = mapperChildren.item(i);
            switch (mapperChild.getNodeName()) {
                case "cache":
                    addCache(mapperChild, mapper);
                    break;
                case "resultMap":
                    addResultMap(mapperChild, mapper);
                    break;
                case "select", "insert", "update", "delete":
                    addQuery(mapper, mapperChild);
                    break;
            }
        }

        mappers.put(namespace, mapper);
    }

    void addCache(Node mapperChild, Mapper mapper) throws Exception {
        String eviction = getAttributeValue(mapperChild, "eviction");
        long flushInterval = Long.parseLong(getAttributeValue(mapperChild, "flushInterval"));
        int size = Integer.parseInt(getAttributeValue(mapperChild, "size"));

        mapper.cache = switch (eviction) {
            case "FIFO" -> new FIFOCache<>(size, flushInterval);
            case "LRU" -> new LRUCache<>(size, flushInterval);
            default -> throw new ParserConfigurationException("Eviction type must be either FIFO or LRU!");
        };
    }

    void addGenericMapper(Class<?> clazz, Map<String, Mapper> mappers) throws Exception {
        Mapper mapper = new Mapper();

        addGenericCache(clazz, mapper);

        Method[] methods = clazz.getDeclaredMethods();
        for (Method m : methods) {
            Query query = getGenericQuery(m);
            mapper.queries.put(m.getName(), query);
        }

        mappers.put(clazz.toString(), mapper);
    }

    Query getGenericQuery(Method method) {
        Query.QUERY_TYPE queryType = null;
        String sql = null;
        Class<?> returnType = method.getReturnType();
        boolean useCache = false;

        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            queryType = Query.QUERY_TYPE.SELECT;
            sql = select.value();
            useCache = select.useCache();
        }

        Insert insert = method.getAnnotation(Insert.class);
        if (insert != null) {
            queryType = Query.QUERY_TYPE.INSERT;
            sql = insert.value();
        }

        Update update = method.getAnnotation(Update.class);
        if (update != null) {
            queryType = Query.QUERY_TYPE.UPDATE;
            sql = update.value();
        }

        Delete delete = method.getAnnotation(Delete.class);
        if (delete != null) {
            queryType = Query.QUERY_TYPE.DELETE;
            sql = delete.value();
        }

        return new Query(queryType, sql, returnType, null, useCache);
    }

    private void addGenericCache(Class<?> clazz, Mapper mapper) throws Exception {
        CustomCache cacheAnnotation = clazz.getAnnotation(CustomCache.class);
        if (cacheAnnotation != null) {
            String eviction = cacheAnnotation.eviction();
            long flushInterval = cacheAnnotation.flushInterval();
            int size = cacheAnnotation.size();

            mapper.cache = switch (eviction) {
                case "FIFO" -> new FIFOCache<>(size, flushInterval);
                case "LRU" -> new LRUCache<>(size, flushInterval);
                default -> throw new ParserConfigurationException("Eviction type must be either FIFO or LRU!");
            };
        }
    }

    private void addQuery(Mapper mapper, Node mapperChild) throws Exception {
        String id = getAttributeValue(mapperChild, "id");
        if (id.equals(""))
            throw new ParserConfigurationException("Attribute: id on element: select cannot be empty!");

        Query query = buildQuery(mapperChild, mapperChild.getNodeName());
        if (query == null)
            return;

        mapper.queries.put(id, query);
    }

    void addResultMap(Node mapperChild, Mapper mapper) throws Exception {
        String resultMapId = getAttributeValue(mapperChild, "id");
        if (resultMapId.equals(""))
            throw new ParserConfigurationException("Attribute: id on element: resultMap cannot be empty!");

        String resultMapType = getAttributeValue(mapperChild, "type");
        if (resultMapType.equals(""))
            throw new ParserConfigurationException("Attribute: type on element: resultMap cannot be empty!");

        ResultMap resultMap = buildResultMap(mapperChild);
        resultMap.returnType = Class.forName(resultMapType);
        mapper.resultMaps.put(resultMapId, resultMap);
    }

    ResultMap buildResultMap(Node resultMapNode) throws Exception {
        ResultMap resultMap = new ResultMap();

        NodeList resultMapNodeChildren = resultMapNode.getChildNodes();
        for (int i = 0; i < resultMapNodeChildren.getLength(); i++) {
            Node resultMapNodeChild = resultMapNodeChildren.item(i);

            switch (resultMapNodeChild.getNodeName()) {
                case "id", "result": {
                    String column = getAttributeValue(resultMapNodeChild, "column");
                    if (column.equals(""))
                        throw new ParserConfigurationException("Attribute: column on element: result cannot be empty!");

                    String property = getAttributeValue(resultMapNodeChild, "property");
                    if (property.equals(""))
                        throw new ParserConfigurationException("Attribute: property on element: result cannot be empty!");

                    resultMap.propsToColumns.put(property, column);
                }
            }
        }

        return resultMap;
    }

    Query buildQuery(Node mapperChild, String queryTypeString) throws Exception {
        Query.QUERY_TYPE queryType = Query.QUERY_TYPE.valueOf(queryTypeString.toUpperCase());

        boolean useCache = false;
        String resultType = "java.lang.Integer";
        String resultMap = null;
        if (queryType.equals(Query.QUERY_TYPE.SELECT)) {
            NamedNodeMap attrs = mapperChild.getAttributes();
            if (attrs == null) {
                throw new ParserConfigurationException("Could not find attributes on element: select");
            }

            Node useCacheAttr = attrs.getNamedItem("useCache");
            if (useCacheAttr != null && useCacheAttr.getTextContent().equals("true")) {
                useCache = true;
            }

            Node attr = attrs.getNamedItem("resultType");
            if (attr == null) {
                resultType = null;
                resultMap = getAttributeValue(mapperChild, "resultMap");
                if (resultMap.equals(""))
                    throw new ParserConfigurationException("Attribute: resultType/resultMap on element: select cannot be empty!");
            } else
                resultType = attr.getTextContent();
        }

        Class<?> resultClass = null;
        if (resultType != null) {
            resultClass = Class.forName(resultType);
        }

        String sql = mapperChild.getTextContent();
        if (Objects.equals(sql, ""))
            throw new ParserConfigurationException("Text content of: " +  queryType.toString().toLowerCase() + " cannot be empty!");

        return new Query(queryType, sql, resultClass, resultMap, useCache);
    }

    String getAttributeValue(Node node, String name) throws Exception {
        NamedNodeMap attrs = node.getAttributes();
        if (attrs == null) {
            throw new ParserConfigurationException("Could not find attribute: " + name + ", on element: " + node.getNodeName());
        }

        Node attr = attrs.getNamedItem(name);
        if (attr == null)
            throw new ParserConfigurationException("Could not find attribute: " + name + ", on element: " + node.getNodeName());

        return attr.getTextContent();
    }

    private static Map<String, String> loadPropsFromPropsFile(Reader reader) throws IOException {
        Map<String, String> propertiesMap = new HashMap<>();
        BufferedReader bufferedReader = new BufferedReader(reader);
        String line = bufferedReader.readLine();
        while (line != null) {
            StringBuilder stringBuilder = new StringBuilder();
            String key = "";
            String value = "";
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '=') {
                    key = stringBuilder.toString();
                    stringBuilder.setLength(0);
                    i++;
                }

                stringBuilder.append(line.charAt(i));
            }
            value = stringBuilder.toString();
            propertiesMap.put(key, value);

            line = bufferedReader.readLine();
        }

        return propertiesMap;
    }
}