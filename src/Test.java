import config.XMLConfigParser;
import session.SqlSession;
import session.SqlSessionFactoryBuilder;

import java.util.Arrays;

public class Test {
    public static void main(String[] args) throws Exception {
        try (SqlSession sqlSession = new SqlSessionFactoryBuilder().build("C:\\Users\\plame\\IdeaProjects\\infinno-course-project-2\\src\\config\\config.xml").openSession()) {
            System.out.println(sqlSession.selectList("getAllEmployees"));
            var mapper = sqlSession.getMapper(EmployeeMapper.class);
            System.out.println(mapper.getEmployeeById(2));
            System.out.println(mapper.getEmployeeById(2));
            mapper.addEmployee(new Employee(4, "Ivan", "Petrov", 19, 2000f, 1, 1, 1));
            System.out.println(mapper.getEmployeeById(2));
        }
    }
}