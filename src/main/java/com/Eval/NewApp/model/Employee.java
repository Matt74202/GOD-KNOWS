package com.Eval.NewApp.model;
import java.util.List;

public class Employee {
    private String name;
    private String firstName;
    private String lastName;
    private String department;
    private List<SalaryComponent> salaryComponents;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public List<SalaryComponent> getSalaryComponents() { return salaryComponents; }
    public void setSalaryComponents(List<SalaryComponent> salaryComponents) { this.salaryComponents = salaryComponents; }
}