package com.Eval.NewApp.model;
import java.util.List;

public class Payslip {
    private String employeeName;
    private String month;
    private List<SalaryComponent> components;
    private Double total;

    // Getters and Setters
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public List<SalaryComponent> getComponents() { return components; }
    public void setComponents(List<SalaryComponent> components) { this.components = components; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
}