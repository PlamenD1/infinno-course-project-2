public class Employee {
    public int id;
    public String firstName;
    public String lastName;
    public int age;
    public float salary;
    public int jobId;
    public Integer managerId;
    public int departmentId;

    public Employee() {}

    public Employee(int id, String firstName, String lastName, int age, float salary, int jobId, Integer managerId, int departmentId) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.salary = salary;
        this.jobId = jobId;
        this.managerId = managerId;
        this.departmentId = departmentId;
    }

    @Override
    public String toString() {
        return firstName + " " + lastName + " " + age + " " + salary + " jobId: " + jobId + " managerId: " + managerId + " departmentId: " + departmentId;
    }
}
