package com.Eval.NewApp.controller;

import com.Eval.NewApp.model.Employee;
import com.Eval.NewApp.model.Payslip;
import com.Eval.NewApp.service.CSVImportService;
import com.Eval.NewApp.service.ERPNextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Controller
public class ERPNextController {

    @Autowired
    private ERPNextService erpNextService;

    @Autowired
    private CSVImportService csvImportService;

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, Model model) {
        try {
            String result = erpNextService.validateUserCredentials(username, password);
            if (result == null) {
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
    public String listEmployees(@RequestParam(required = false) String search, Model model) {
        try {
            List<Employee> employees = erpNextService.getEmployees(search);
            model.addAttribute("employees", employees);
            model.addAttribute("search", search);
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
    public String importCSV(@RequestParam("file") MultipartFile file, Model model) throws Exception {
        try {
            List<String> errors = csvImportService.importCSV(file);
            if (errors.isEmpty()) {
                model.addAttribute("message", "CSV imported successfully");
            } else {
                model.addAttribute("errors", errors);
            }
            return "employee_list";
        } catch (RuntimeException e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }
}