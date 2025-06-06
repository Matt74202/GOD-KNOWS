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

    // Redirect root URL to /login
    @GetMapping("/")
    public String redirectToLogin() {
        return "redirect:/login";
    }

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
    @GetMapping("/logout")
    public String logout(HttpSession session, Model model) {
        try {
            // Invalidate the HTTP session
            session.invalidate();
            // Clear cookies using ERPNextService method
            erpNextService.clearCookies();
            System.out.println("Session invalidated and cookies cleared on logout.");
            model.addAttribute("message", "You have been logged out successfully.");
            return "redirect:/login";
        } catch (Exception e) {
            System.err.println("Error during logout: " + e.getMessage());
            model.addAttribute("error", "Error during logout: " + e.getMessage());
            return "login";
        }
    }

    // Other methods remain unchanged
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
                               Model model, HttpSession session) {
        try {
            // Check session validity
            if (!erpNextService.isSessionValid()) {
                return "redirect:/login?error=Session expired or invalid. Please login again.";
            }
            Map<String, String> filters = new HashMap<>();
            if (search != null && !search.isEmpty()) {
                filters.put("first_name", search);
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
    public String employeeDetails(@PathVariable String id, Model model, HttpSession session) {
        try {
            if (!erpNextService.isSessionValid()) {
                return "redirect:/login?error=Session expired or invalid. Please login again.";
            }
            Employee employee = erpNextService.getEmployeeDetails(id);
            model.addAttribute("employee", employee);
            return "employee_details";
        } catch (RuntimeException e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }

    @GetMapping("/payslip/{employeeId}/{month}")
    public String payslip(@PathVariable String employeeId, @PathVariable String month, Model model, HttpSession session) {
        try {
            if (!erpNextService.isSessionValid()) {
                return "redirect:/login?error=Session expired or invalid. Please login again.";
            }
            Payslip payslip = erpNextService.getPayslip(employeeId, month);
            model.addAttribute("payslip", payslip);
            return "payslip";
        } catch (RuntimeException e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }

    @GetMapping("/payslips")
    public String monthlyPayslips(@RequestParam(required = false) String month, Model model, HttpSession session) {
        try {
            if (!erpNextService.isSessionValid()) {
                return "redirect:/login?error=Session expired or invalid. Please login again.";
            }
            List<Payslip> payslips = erpNextService.getMonthlyPayslips(month);
            model.addAttribute("payslips", payslips);
            model.addAttribute("month", month);
            return "payslips";
        } catch (RuntimeException e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }

    @GetMapping("/monthly-payslips")
    public String viewMonthlyPayslips(HttpSession session) {
        if (!erpNextService.isSessionValid()) {
            return "redirect:/login?error=Session expired or invalid. Please login again.";
        }
        return "redirect:/payslips";
    }
}