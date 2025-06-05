package com.Eval.NewApp.model;

import java.util.List;
import java.util.Map;
import com.Eval.NewApp.service.UtilService;

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
    private String genre;
    private String dateEmbauche;
    private String dateNaissance;
    private List<Payslip> payslips;
    private UtilService utilService; // Placeholder: Define this service

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
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(String dateEmbauche) { this.dateEmbauche = dateEmbauche; }
    public String getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(String dateNaissance) { this.dateNaissance = dateNaissance; }
    public List<Payslip> getPayslips() { return payslips; }
    public void setPayslips(List<Payslip> payslips) { this.payslips = payslips; }
    public UtilService getUtilService() { return utilService; }
    public void setUtilService(UtilService utilService) { this.utilService = utilService; }

    // Validation method (placeholder)
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Employee Ref is required");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last Name is required");
        }
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First Name is required");
        }
        if (genre == null || genre.trim().isEmpty()) {
            throw new IllegalArgumentException("Genre is required");
        }
        if (dateEmbauche == null || dateEmbauche.trim().isEmpty()) {
            throw new IllegalArgumentException("Date Embauche is required");
        }
        if (dateNaissance == null || dateNaissance.trim().isEmpty()) {
            throw new IllegalArgumentException("Date Naissance is required");
        }
        if (company == null || company.trim().isEmpty()) {
            throw new IllegalArgumentException("Company is required");
        }
        // Add date format validation if needed
    }

    // Convert Employee to Map for API (placeholder)
    public Map<String, Object> toMap(boolean forUpdate) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("name", name);
        map.put("first_name", firstName);
        map.put("last_name", lastName);
        map.put("gender", genre);
        map.put("date_of_joining", dateEmbauche);
        map.put("date_of_birth", dateNaissance);
        map.put("company", company);
        // Add other fields as needed for ERPNext API
        return map;
    }
}