package com.Eval.NewApp.model;

import java.util.ArrayList;
import java.util.List;

public class SalaryStructure {
    private List<SalaryImport> components;
    private String salaryStructureName;
    private String company;
    private List<String> results;

    public SalaryStructure(List<SalaryImport> components, String salaryStructureName, String company) {
        this.components = components;
        this.salaryStructureName = salaryStructureName;
        this.company = company;
        this.results = new ArrayList<>();
    }

    // Getters et Setters
    public List<SalaryImport> getComponents() {
        return components;
    }

    public void setComponents(List<SalaryImport> components) {
        this.components = components;
    }

    public String getSalaryStructureName() {
        return salaryStructureName;
    }

    public void setSalaryStructureName(String salaryStructureName) {
        this.salaryStructureName = salaryStructureName;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public List<String> getResults() {
        return results;
    }

    public void setResults(List<String> results) {
        this.results = results;
    }

    public void addResult(String result) {
        this.results.add(result);
    }
}
