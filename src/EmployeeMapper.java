import session.annotations.*;

import java.util.List;

@CustomCache(eviction = "FIFO", flushInterval = 60000, size = 6)
public interface EmployeeMapper {
    @Select(value = "SELECT * FROM Employees WHERE id = #{value}", useCache = true)
    Employee getEmployeeById(int id);

    @Select(value = "SELECT * FROM Employees", useCache = true)
    List<Employee> getAllEmployees();

    @Select(value = "SELECT * FROM Employees WHERE jobID = #{value}", useCache = true)
    List<Employee> getAllEmployeesByJob(int jobId);

    @Insert("INSERT INTO Employees (firstName, lastName, age, salary, jobID, managerID, departmentID) " +
            "VALUES (#{firstName}, #{lastName}, #{age}, #{salary}, #{jobId}, #{managerId}, #{departmentId})")
    int addEmployee(Employee e);

    @Update("UPDATE Employees" +
            "SET firstName = #{firstName}, lastName = #{lastName}, age = #{age}, salary = #{salary}, jobID = #{jobId}, managerID = #{managerId}, departmentID = #{departmentId},\n" +
            "WHERE id = #{id}")
    int updateEmployee(Employee e);

    @Update("UPDATE Employees SET salary = MAX(#{percent} * salary + salary, #{minSalary})")
    int updateEmployeeSalaries(float _percent, float _salary);

    @Delete("DELETE FROM Employees WHERE id = #{value}")
    int deleteEmployee(int id);
}
