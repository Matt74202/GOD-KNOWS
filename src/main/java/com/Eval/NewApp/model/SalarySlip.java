package com.Eval.NewApp.model;

import com.Eval.NewApp.service.UtilService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class SalarySlip {
    private static final Logger logger = LoggerFactory.getLogger(SalarySlip.class);

    private String month; // Mois (ex. : 01/04/2025)
    private String employeeId; // Ref Employe
    private Double baseSalary; // Salaire Base (maintenant un Double pour gérer les décimales)
    private String salaryStructure; // Salaire (nom de la structure salariale)

    @Autowired
    private UtilService utilService;

    // Constructeur par défaut
    public SalarySlip() {
    }

    // Constructeur avec paramètres
    public SalarySlip(String month, String employeeId, Double baseSalary, String salaryStructure) {
        this.month = month;
        this.employeeId = employeeId;
        this.baseSalary = baseSalary;
        this.salaryStructure = salaryStructure;
    }

    // Getters et Setters
    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public Double getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(String baseSalary) {
        if (baseSalary == null || baseSalary.trim().isEmpty()) {
            this.baseSalary = null;
            return;
        }
        try {
            // Remplacer la virgule par un point pour gérer les formats comme "1500000,30"
            String normalized = baseSalary.replace(",", ".");
            this.baseSalary = Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            logger.error("Invalid number format for baseSalary: {}", baseSalary);
            throw new IllegalArgumentException("Base salary must be a valid number: " + baseSalary);
        }
    }

    public String getSalaryStructure() {
        return salaryStructure;
    }

    public void setSalaryStructure(String salaryStructure) {
        this.salaryStructure = salaryStructure;
    }

    public void setUtilService(UtilService utilService) {
        this.utilService = utilService;
    }

    // Validation des données
    public void validate() throws IllegalArgumentException {
        if (month == null || month.trim().isEmpty()) {
            throw new IllegalArgumentException("Month is required");
        }
        try {
            utilService.getFormattedDate(month); // Valide le format de la date
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid date format for month: " + month);
        }
        if (employeeId == null || employeeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Employee ID is required");
        }
        if (baseSalary == null) {
            throw new IllegalArgumentException("Base salary is required");
        }
        if (baseSalary <= 0) {
            throw new IllegalArgumentException("Base salary must be greater than 0: " + baseSalary);
        }
        if (salaryStructure == null || salaryStructure.trim().isEmpty()) {
            throw new IllegalArgumentException("Salary structure is required");
        }
    }

    // Conversion en Map pour l'API ERPNext
    public Map<String, Object> toMap(boolean isUpdate) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("doctype", "Salary Slip");
            data.put("employee", employeeId);
            data.put("salary_structure", salaryStructure);
            data.put("payroll_date", utilService.formatDate(utilService.getFormattedDate(month), "yyyy-MM-dd"));
            data.put("start_date", utilService.formatDate(utilService.getFormattedDate(month), "yyyy-MM-dd"));
            data.put("end_date", utilService.getEndOfMonth(month, "dd/MM/yyyy"));
            data.put("posting_date", utilService.formatDate(utilService.getFormattedDate(month), "yyyy-MM-dd"));
            
            // Ajouter le salaire de base comme composant principal
            List<Map<String, Object>> earnings = new ArrayList<>();
            Map<String, Object> baseEarning = new HashMap<>();
            baseEarning.put("salary_component", "Salaire Base");
            baseEarning.put("amount", baseSalary); // Utiliser directement la valeur Double
            earnings.add(baseEarning);
            data.put("earnings", earnings);

            // Pour les autres composants, laisser ERPNext les calculer automatiquement
            data.put("deductions", new ArrayList<>());
            data.put("timesheets", new ArrayList<>());
            
            // Activer le calcul automatique
            data.put("calculate_total_salary", 1);

            if (isUpdate) {
                data.put("name", utilService.generateSalarySlipName(employeeId, month));
            }

            return data;
        } catch (Exception e) {
            logger.error("Error converting SalarySlip to Map: {}", e.getMessage());
            throw new IllegalStateException("Failed to convert SalarySlip to Map: " + e.getMessage());
        }
    }
}