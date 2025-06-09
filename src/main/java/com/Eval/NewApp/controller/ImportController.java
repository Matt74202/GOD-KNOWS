package com.Eval.NewApp.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.Eval.NewApp.service.ImportService;
import com.Eval.NewApp.service.UtilService;

@Controller
public class ImportController {

    private static final Logger logger = LoggerFactory.getLogger(ImportController.class);

    @Autowired
    private ImportService importService;

    @Autowired
    private UtilService utilService;

    @GetMapping("/import")
    public String loadImportPage() {
        return "import";
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/import")
    public String importCSV(
            @RequestParam(value = "employeeCsvFile", required = false) MultipartFile employeeCsvFile,
            @RequestParam(value = "salaryStructureCsvFile", required = false) MultipartFile salaryStructureCsvFile,
            @RequestParam(value = "salarySlipCsvFile", required = false) MultipartFile salarySlipCsvFile,
            HttpSession session,
            Model model) {
        try {
            String sid = (String) session.getAttribute("sid");
            if (sid == null) {
                logger.warn("Missing session ID, redirecting to login");
                return "redirect:/login?error=Missing session ID";
            }

            // Initialize employee reference map
            Map<String, String> refToNameMap = (Map<String, String>) session.getAttribute("employeeRefToNameMap");
            if (refToNameMap == null) {
                refToNameMap = new HashMap<>();
                session.setAttribute("employeeRefToNameMap", refToNameMap);
            }

            List<String> results = new ArrayList<>();

            // Validate file types
            if (employeeCsvFile != null && !employeeCsvFile.isEmpty() && !isValidCsvFile(employeeCsvFile)) {
                logger.error("Invalid file type for Employee CSV: {}", employeeCsvFile.getContentType());
                model.addAttribute("error", "Invalid file type for Employee CSV. Please upload a CSV file.");
                return "employee_list";
            }
            if (salaryStructureCsvFile != null && !salaryStructureCsvFile.isEmpty() && !isValidCsvFile(salaryStructureCsvFile)) {
                logger.error("Invalid file type for Salary Structure CSV: {}", salaryStructureCsvFile.getContentType());
                model.addAttribute("error", "Invalid file type for Salary Structure CSV. Please upload a CSV file.");
                return "employee_list";
            }
            if (salarySlipCsvFile != null && !salarySlipCsvFile.isEmpty() && !isValidCsvFile(salarySlipCsvFile)) {
                logger.error("Invalid file type for Salary Slip CSV: {}", salarySlipCsvFile.getContentType());
                model.addAttribute("error", "Invalid file type for Salary Slip CSV. Please upload a CSV file.");
                return "employee_list";
            }

            // Process Employee CSV if provided
            if (employeeCsvFile != null && !employeeCsvFile.isEmpty()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(employeeCsvFile.getInputStream(), StandardCharsets.UTF_8))) {
                    String headerLine = reader.readLine();
                    if (headerLine == null || !headerLine.trim().toLowerCase()
                            .startsWith("ref,nom,prenom,genre,date embauche,date naissance,company")) {
                        logger.error("Invalid employee CSV header: {}", headerLine);
                        model.addAttribute("error", "Invalid employee CSV header. Expected: Ref,Nom,Prenom,genre,Date embauche,date naissance,company");
                        return "employee_list";
                    }

                    String line;
                    int lineNumber = 1;
                    while ((line = reader.readLine()) != null) {
                        lineNumber++;
                        if (line.trim().isEmpty()) {
                            results.add(String.format("Line %d: Skipped empty line", lineNumber));
                            logger.info("Line {}: Skipped empty line", lineNumber);
                            continue;
                        }

                        String[] fields = parseCsvLine(line);
                        if (fields.length < 7) {
                            results.add(String.format("Line %d: Error: Invalid CSV format, expected 7 fields, found %d", lineNumber, fields.length));
                            logger.error("Line {}: Invalid CSV format, expected 7 fields, found {}", lineNumber, fields.length);
                            continue;
                        }

                        Map<String, Object> employeeData = new HashMap<>();
                        String ref = fields[0].trim();
                        employeeData.put("name", ref);
                        employeeData.put("last_name", fields[1].trim());
                        employeeData.put("first_name", fields[2].trim());
                        employeeData.put("employee_name", fields[2].trim() + " " + fields[1].trim());

                        String csvGender = fields[3].trim().toLowerCase();
                        String erpNextGender = csvGender.equals("masculin") ? "Male" : csvGender.equals("feminin") ? "Female" : null;
                        if (erpNextGender == null) {
                            results.add(String.format("Line %d: Error: Invalid gender value '%s'", lineNumber, csvGender));
                            logger.error("Line {}: Invalid gender value '{}'", lineNumber, csvGender);
                            continue;
                        }
                        employeeData.put("gender", erpNextGender); // Maps to genre in Employee model

                        String csvDateOfJoining = fields[4].trim();
                        if (csvDateOfJoining.isEmpty()) {
                            results.add(String.format("Line %d: Error: Date of joining cannot be empty", lineNumber));
                            logger.error("Line {}: Date of joining is empty", lineNumber);
                            continue;
                        }
                        try {
                            utilService.getFormattedDate(csvDateOfJoining); // Validate date format
                            employeeData.put("date_of_joining", csvDateOfJoining);
                        } catch (IllegalArgumentException e) {
                            results.add(String.format("Line %d: Error: Invalid date_of_joining format '%s': %s", lineNumber, csvDateOfJoining, e.getMessage()));
                            logger.error("Line {}: Invalid date_of_joining format '{}': {}", lineNumber, csvDateOfJoining, e.getMessage());
                            continue;
                        }

                        String csvDateOfBirth = fields[5].trim();
                        if (csvDateOfBirth.isEmpty()) {
                            results.add(String.format("Line %d: Error: Date of birth cannot be empty", lineNumber));
                            logger.error("Line {}: Date of birth is empty", lineNumber);
                            continue;
                        }
                        try {
                            utilService.getFormattedDate(csvDateOfBirth); // Validate date format
                            employeeData.put("date_of_birth", csvDateOfBirth);
                        } catch (IllegalArgumentException e) {
                            results.add(String.format("Line %d: Error: Invalid date_of_birth format '%s': %s", lineNumber, csvDateOfBirth, e.getMessage()));
                            logger.error("Line {}: Invalid date_of_birth format '{}': {}", lineNumber, csvDateOfBirth, e.getMessage());
                            continue;
                        }

                        employeeData.put("company", fields[6].trim());
                        employeeData.put("status", "Active");

                        try {
                            ResponseEntity<Map> response = importService.importEmployee(employeeData, session);
                            if (response.getStatusCode().is2xxSuccessful()) {
                                Map<String, Object> responseBody = response.getBody();
                                String message = (String) responseBody.get("message");
                                String employeeId = (String) responseBody.get("employee_id");
                                results.add(String.format("Line %d: %s (Employee ID: %s)", lineNumber, message, employeeId));
                                logger.info("Line {}: Employee Ref {} mapped to ERPNext ID {}", lineNumber, ref, employeeId);
                            } else {
                                String error = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                                results.add(String.format("Line %d: Failed to import employee %s: %s", lineNumber, employeeData.get("employee_name"), error));
                                logger.error("Line {}: Failed to import employee {}: {}", lineNumber, employeeData.get("employee_name"), error);
                            }
                        } catch (Exception e) {
                            results.add(String.format("Line %d: Error importing employee %s: %s", lineNumber, employeeData.get("employee_name"), e.getMessage()));
                            logger.error("Line {}: Error importing employee {}: {}", lineNumber, employeeData.get("employee_name"), e.getMessage(), e);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error reading employee CSV file: {}", e.getMessage(), e);
                    model.addAttribute("error", "Error reading employee CSV file: " + e.getMessage());
                    return "employee_list";
                }
            } else if (employeeCsvFile != null && employeeCsvFile.isEmpty()) {
                results.add("Error: Employee CSV file is empty");
                logger.warn("Employee CSV file is empty");
            }

            // Process Salary Structure CSV if provided
            if (salaryStructureCsvFile != null && !salaryStructureCsvFile.isEmpty()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(salaryStructureCsvFile.getInputStream(), StandardCharsets.UTF_8))) {
                    String headerLine = reader.readLine();
                    if (headerLine == null || !headerLine.trim().toLowerCase()
                            .startsWith("salary structure,name,abbr,type,valeur,company")) {
                        logger.error("Invalid salary structure CSV header: {}", headerLine);
                        model.addAttribute("error", "Invalid salary structure CSV header. Expected: salary structure,name,abbr,type,valeur,company");
                        return "employee_list";
                    }

                    List<Map<String, Object>> componentDataList = new ArrayList<>();
                    String line;
                    int lineNumber = 1;
                    while ((line = reader.readLine()) != null) {
                        lineNumber++;
                        if (line.trim().isEmpty()) {
                            results.add(String.format("Line %d: Skipped empty line", lineNumber));
                            logger.info("Line {}: Skipped empty line", lineNumber);
                            continue;
                        }

                        String[] fields = parseCsvLine(line);
                        if (fields.length < 6) {
                            results.add(String.format("Line %d: Error: Invalid CSV format, expected 6 fields, found %d", lineNumber, fields.length));
                            logger.error("Line {}: Invalid CSV format, expected 6 fields, found {}", lineNumber, fields.length);
                            continue;
                        }

                        Map<String, Object> componentData = new HashMap<>();
                        componentData.put("salary_structure", fields[0].trim());
                        componentData.put("name", fields[1].trim());
                        componentData.put("abbr", fields[2].trim());
                        componentData.put("type", fields[3].trim());
                        componentData.put("valeur", fields[4].trim());
                        componentData.put("company", fields[5].trim());

                        componentDataList.add(componentData);
                    }

                    try {
                        ResponseEntity<Map> response = importService.importSalaryStructure(componentDataList);
                        if (response.getStatusCode().is2xxSuccessful()) {
                            List<String> structureResults = (List<String>) response.getBody().get("results");
                            String message = (String) response.getBody().get("message");
                            if (structureResults.isEmpty()) {
                                results.add(message != null ? message : "Salary structures imported successfully with no issues");
                            } else {
                                results.addAll(structureResults);
                            }
                            logger.info("Salary structures imported successfully with {} results", structureResults.size());
                        } else {
                            List<String> structureResults = (List<String>) response.getBody().get("results");
                            String error = (String) response.getBody().get("error");
                            if (structureResults != null) {
                                results.addAll(structureResults);
                            }
                            results.add("Error importing salary structures: " + error);
                            logger.error("Error importing salary structures: {}", error);
                        }
                    } catch (Exception e) {
                        results.add("Error importing salary structures: " + e.getMessage());
                        logger.error("Error importing salary structures: {}", e.getMessage(), e);
                    }
                } catch (IOException e) {
                    logger.error("Error reading salary structure CSV file: {}", e.getMessage(), e);
                    model.addAttribute("error", "Error reading salary structure CSV file: " + e.getMessage());
                    return "employee_list";
                }
            } else if (salaryStructureCsvFile != null && salaryStructureCsvFile.isEmpty()) {
                results.add("Error: Salary structure CSV file is empty");
                logger.warn("Salary structure CSV file is empty");
            }

            // Process Salary Slip CSV if provided
            if (salarySlipCsvFile != null && !salarySlipCsvFile.isEmpty()) {
                try {
                    List<String> slipResults = importService.importSalarySlips(salarySlipCsvFile, sid, refToNameMap, session);
                    results.addAll(slipResults);
                    logger.info("Salary slips imported with {} results", slipResults.size());
                } catch (Exception e) {
                    results.add("Error importing salary slips: " + e.getMessage());
                    logger.error("Error importing salary slips: {}", e.getMessage(), e);
                }
            } else if (salarySlipCsvFile != null && salarySlipCsvFile.isEmpty()) {
                results.add("Error: Salary slip CSV file is empty");
                logger.warn("Salary slip CSV file is empty");
            }

            // If no files were provided
            if ((employeeCsvFile == null || employeeCsvFile.isEmpty()) &&
                (salaryStructureCsvFile == null || salaryStructureCsvFile.isEmpty()) &&
                (salarySlipCsvFile == null || salarySlipCsvFile.isEmpty())) {
                logger.warn("No valid CSV files uploaded");
                model.addAttribute("error", "No valid CSV files uploaded");
                return "employee_list";
            }

            // Add results to model
            model.addAttribute("importResults", results);
            model.addAttribute("message", results.isEmpty() ? "CSV imported successfully" :
                    "CSV import completed with " + (results.stream().anyMatch(r -> r.contains("Error") || r.contains("Failed")) ? "issues" : "success"));
            logger.info("CSV import completed with {} results", results.size());

            return "employee_list";

        } catch (Exception e) {
            logger.error("Unexpected error during CSV import: {}", e.getMessage(), e);
            model.addAttribute("error", "Unexpected error: " + e.getMessage());
            return "employee_list";
        }
    }

    private boolean isValidCsvFile(MultipartFile file) {
        return file.getContentType() != null &&
               (file.getContentType().equals("text/csv") ||
                file.getContentType().equals("application/vnd.ms-excel"));
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                fields.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }
}