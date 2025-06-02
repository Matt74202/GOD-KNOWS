package com.Eval.NewApp.model;

import java.util.List;

public class Employee {
    private String name;
    private String employeeName;
    private String firstName;
    private String lastName;
    private String department;
    private String designation;
    private String company;
    private String dateOfJoining;
    private String gender;
    private String dateOfBirth;
    private String employmentType;
    private String status;
    private List<Payslip> payslips;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(String dateOfJoining) { this.dateOfJoining = dateOfJoining; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Payslip> getPayslips() { return payslips; }
    public void setPayslips(List<Payslip> payslips) { this.payslips = payslips; }
}