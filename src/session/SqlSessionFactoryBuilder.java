package session;

import config.Configuration;
import config.XMLConfigParser;

public class SqlSessionFactoryBuilder {
    public SqlSessionFactoryBuilder() {}

    public SqlSessionFactory build(String resource) throws Exception {
        XMLConfigParser xmlParser = new XMLConfigParser(resource);
        return new SqlSessionFactory(xmlParser.configuration);
    }
}
