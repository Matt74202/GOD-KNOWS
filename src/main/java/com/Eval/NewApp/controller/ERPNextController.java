package com.Eval.NewApp.controller;

import com.Eval.NewApp.model.Employee;
import com.Eval.NewApp.model.Payslip;
import com.Eval.NewApp.service.ERPNextService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ERPNextController {

    @Autowired
    private ERPNextService erpNextService;

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

@PostMapping("/login")
public String login(@RequestParam String username, @RequestParam String password, 
                   HttpSession session, Model model) {
    try {
        String result = erpNextService.validateUserCredentials(username, password);
        if (result == null) {
            // Récupérer le SID du cookie store et le stocker en session
            String sid = erpNextService.getSessionId();
            if (sid != null) {
                session.setAttribute("sid", sid);
            }
            return "redirect:/employees";
        } else {
            model.addAttribute("error", result);
            return "login";
        }
    } catch (RuntimeException e) {
        model.addAttribute("error", "Authentication failed: " + e.getMessage());
        return "login";
    }
}    

@GetMapping("/employees")
    public String listEmployees(@RequestParam(required = false) String search,
                               @RequestParam(required = false) String firstName,
                               @RequestParam(required = false) String lastName,
                               @RequestParam(required = false) String department,
                               @RequestParam(required = false) String designation,
                               @RequestParam(required = false) String company,
                               @RequestParam(required = false) String gender,
                               @RequestParam(required = false) String dateOfJoining,
                               @RequestParam(required = false) String dateOfBirth,
                               @RequestParam(required = false) String employmentType,
                               Model model) {
        try {
            Map<String, String> filters = new HashMap<>();
            if (search != null && !search.isEmpty()) {
                filters.put("first_name", search); // Maintain compatibility with existing search
            }
            if (firstName != null && !firstName.isEmpty()) filters.put("first_name", firstName);
            if (lastName != null && !lastName.isEmpty()) filters.put("last_name", lastName);
            if (department != null && !department.isEmpty()) filters.put("department", department);
            if (designation != null && !designation.isEmpty()) filters.put("designation", designation);
            if (company != null && !company.isEmpty()) filters.put("company", company);
            if (gender != null && !gender.isEmpty()) filters.put("gender", gender);
            if (dateOfJoining != null && !dateOfJoining.isEmpty()) filters.put("date_of_joining", dateOfJoining);
            if (dateOfBirth != null && !dateOfBirth.isEmpty()) filters.put("date_of_birth", dateOfBirth);
            if (employmentType != null && !employmentType.isEmpty()) filters.put("employment_type", employmentType);

            List<Employee> employees = erpNextService.getEmployees(filters);
            List<String> genderList = erpNextService.getGenderList();
            List<String> departmentList = erpNextService.getDepartmentList();
            List<String> designationList = erpNextService.getDesignationList();
            List<String> companyList = erpNextService.getCompanyList();

            model.addAttribute("employees", employees);
            model.addAttribute("search", search);
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            model.addAttribute("department", department);
            model.addAttribute("designation", designation);
            model.addAttribute("company", company);
            model.addAttribute("gender", gender);
            model.addAttribute("dateOfJoining", dateOfJoining);
            model.addAttribute("dateOfBirth", dateOfBirth);
            model.addAttribute("employmentType", employmentType);
            model.addAttribute("genderList", genderList);
            model.addAttribute("departmentList", departmentList);
            model.addAttribute("designationList", designationList);
            model.addAttribute("companyList", companyList);
            return "employee_list";
        } catch (RuntimeException e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }
    @GetMapping("/employee/{id}")
    public String employeeDetails(@PathVariable String id, Model model) {
        try {
            Employee employee = erpNextService.getEmployeeDetails(id);
            model.addAttribute("employee", employee);
            return "employee_details";
        } catch (RuntimeException e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }

    @GetMapping("/payslip/{employeeId}/{month}")
    public String payslip(@PathVariable String employeeId, @PathVariable String month, Model model) {
        try {
            Payslip payslip = erpNextService.getPayslip(employeeId, month);
            model.addAttribute("payslip", payslip);
            return "payslip";
        } catch (RuntimeException e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }

    @GetMapping("/payslips")
    public String monthlyPayslips(@RequestParam(required = false) String month, Model model) {
        try {
            List<Payslip> payslips = erpNextService.getMonthlyPayslips(month);
            model.addAttribute("payslips", payslips);
            model.addAttribute("month", month);
            return "payslips";
        } catch (RuntimeException e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }

    @GetMapping("/monthly-payslips")
    public String viewMonthlyPayslips() {
        return "redirect:/payslips";
    }

@PostMapping("/import")
public String importCSV(@RequestParam("file") MultipartFile file, 
                       HttpSession session, 
                       Model model) {
    try {
        String sid = (String) session.getAttribute("sid");
        if (sid == null) {
            return "redirect:/login?error=Missing session ID";
        }
        
        if (file.isEmpty()) {
            model.addAttribute("error", "Uploaded file is empty");
            return "employee_list";
        }

        List<String> results = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true; // To skip header
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false; // Skip the header row
                    continue;
                }
                
                // Split the CSV line (assuming comma-separated)
                String[] fields = line.split(",");
                if (fields.length < 7) {
                    results.add("Error: Invalid CSV format for line: " + line);
                    continue;
                }

                // Map CSV fields to employee data
                Map<String, Object> employeeData = new HashMap<>();
                employeeData.put("name", fields[0].trim()); // Ref
                employeeData.put("last_name", fields[1].trim()); // Nom
                employeeData.put("first_name", fields[2].trim()); // Prenom
                employeeData.put("employee_name", fields[2].trim() + " " + fields[1].trim()); // Prenom + Nom
                
                // Map gender values
                String csvGender = fields[3].trim(); // genre
                String erpNextGender;
                switch (csvGender) {
                    case "Masculin":
                        erpNextGender = "Male";
                        break;
                    case "Feminin":
                        erpNextGender = "Female";
                        break;
                    default:
                        results.add("Error: Invalid gender value '" + csvGender + "' for employee: " + employeeData.get("employee_name"));
                        continue; // Skip invalid gender
                }
                employeeData.put("gender", erpNextGender); // Set ERPNext-compatible gender

                // Convert dates from DD/MM/YYYY to YYYY-MM-DD
                String csvDateOfJoining = fields[4].trim(); // Date embauche
                if (!csvDateOfJoining.isEmpty()) {
                    String[] dateParts = csvDateOfJoining.split("/");
                    if (dateParts.length == 3) {
                        employeeData.put("date_of_joining", dateParts[2] + "-" + dateParts[1] + "-" + dateParts[0]); // e.g., 2024-04-03
                    } else {
                        results.add("Error: Invalid date_of_joining format '" + csvDateOfJoining + "' for employee: " + employeeData.get("employee_name"));
                        continue;
                    }
                }

                String csvDateOfBirth = fields[5].trim(); // date naissance
                if (!csvDateOfBirth.isEmpty()) {
                    String[] dateParts = csvDateOfBirth.split("/");
                    if (dateParts.length == 3) {
                        employeeData.put("date_of_birth", dateParts[2] + "-" + dateParts[1] + "-" + dateParts[0]); // e.g., 1980-01-01
                    } else {
                        results.add("Error: Invalid date_of_birth format '" + csvDateOfBirth + "' for employee: " + employeeData.get("employee_name"));
                        continue;
                    }
                }

                employeeData.put("company", fields[6].trim()); // company
                employeeData.put("status", "Active"); // Default status

                // Call importEmployee to create or check the employee
                ResponseEntity<Map> response = erpNextService.importEmployee(employeeData);
                if (response.getStatusCode().is2xxSuccessful()) {
                    Map<String, Object> responseBody = response.getBody();
                    String message = (String) responseBody.get("message");
                    String employeeId = (String) responseBody.get("employee_id");
                    results.add("Success: " + message + " (Employee ID: " + employeeId + ")");
                } else {
                    Map<String, Object> responseBody = response.getBody();
                    String error = responseBody != null ? (String) responseBody.get("error") : "Unknown error";
                    results.add("Failed to import employee: " + employeeData.get("employee_name") + " - " + error);
                }
            }
        } catch (IOException e) {
            model.addAttribute("error", "Error reading CSV file: " + e.getMessage());
            return "employee_list";
        }

        model.addAttribute("importResults", results);
        model.addAttribute("message", results.isEmpty() ? "CSV imported successfully" : 
            "CSV import completed with " + (results.stream().anyMatch(r -> r.startsWith("Failed") || r.startsWith("Error")) ? "issues" : "success"));
        
        return "employee_list"; // Stay on the employee_list page to display results

    } catch (Exception e) {
        model.addAttribute("error", "Unexpected error: " + e.getMessage());
        return "employee_list";
    }
}
}