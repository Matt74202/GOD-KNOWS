package com.Eval.NewApp.service;
import com.Eval.NewApp.model.Employee;
import com.Eval.NewApp.model.SalaryComponent;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Service
public class CSVImportService {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public List<String> importCSV(MultipartFile file) throws Exception {
        List<String> errors = new ArrayList<>();
        List<Employee> employees = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            for (CSVRecord record : csvParser) {
                try {
                    Employee employee = new Employee();
                    employee.setName(record.get("Employee ID"));
                    employee.setFirstName(record.get("First Name"));
                    employee.setLastName(record.get("Last Name"));
                    employee.setDepartment(record.get("Department"));

                    // Validate date format for salary component
                    String dateStr = record.get("Salary Date");
                    try {
                        DATE_FORMAT.setLenient(false);
                        DATE_FORMAT.parse(dateStr);
                    } catch (ParseException e) {
                        errors.add("Invalid date format for employee " + employee.getName() + ": " + dateStr);
                        continue;
                    }

                    SalaryComponent component = new SalaryComponent();
                    component.setComponent(record.get("Salary Component"));
                    component.setAmount(Double.parseDouble(record.get("Amount")));
                    component.setMonth(dateStr.substring(0, 7)); // Extract yyyy-MM
                    employee.setSalaryComponents(List.of(component));

                    employees.add(employee);
                    // TODO: Send employee and salary data to ERPNext API
                } catch (Exception e) {
                    errors.add("Error processing record for employee " + record.get("Employee ID") + ": " + e.getMessage());
                }
            }
        }

        // If no errors, you can proceed to send data to ERPNext
        if (errors.isEmpty()) {
            // TODO: Implement API call to ERPNext to save employees and salary components
        }

        return errors;
    }
}