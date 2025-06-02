package com.Eval.NewApp.model;

import java.util.List;

public class Payslip {
    private String name;
    private String employee;
    private String employeeName;
    private String month;
    private String startDate;
    private String endDate;
    private Double grossPay;
    private Double netPay;
    private String status;
    private List<SalaryComponent> components;
    private Double total;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmployee() { return employee; }
    public void setEmployee(String employee) { this.employee = employee; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public Double getGrossPay() { return grossPay; }
    public void setGrossPay(Double grossPay) { this.grossPay = grossPay; }
    public Double getNetPay() { return netPay; }
    public void setNetPay(Double netPay) { this.netPay = netPay; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<SalaryComponent> getComponents() { return components; }
    public void setComponents(List<SalaryComponent> components) { this.components = components; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
}