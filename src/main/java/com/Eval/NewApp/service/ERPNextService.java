package com.Eval.NewApp.service;

import com.Eval.NewApp.model.Employee;
import com.Eval.NewApp.model.Payslip;
import com.Eval.NewApp.model.SalaryComponent;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ERPNextService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CookieStore cookieStore;

    @Value("${erpnext.base-url}")
    private String baseUrl;

    public boolean isSessionValid() {
        List<Cookie> cookies = cookieStore.getCookies();
        boolean hasSid = cookies.stream().anyMatch(c -> c.getName().equals("sid"));
        System.out.println("Checking session validity: Has 'sid' cookie? " + hasSid);
        return hasSid;
    }

    private void checkSessionOrThrow() {
        if (!isSessionValid()) {
            throw new RuntimeException("Session expired or invalid. Please login again.");
        }
    }

    public String validateUserCredentials(String username, String password) {
        try {
            System.out.println("Attempting to validate credentials for username: " + username);
            String endpoint = "method/login";
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            String requestBody = "usr=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                    "&pwd=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            cookieStore.clear();
            System.out.println("Cleared cookies before login request.");

            System.out.println("Sending login request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies received after login:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/method/login: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("full_name") && cookies.stream().anyMatch(c -> c.getName().equals("sid"))) {
                    System.out.println("Login successful with HTTP status: " + response.getStatusCode());
                    return null;
                } else {
                    System.out.println("Login failed: No 'full_name' or 'sid' cookie in response.");
                    return "Login failed: Invalid response or no session cookie.";
                }
            } else {
                System.out.println("Login failed: Invalid response. HTTP status: " + response.getStatusCode());
                return "Login failed: Invalid response. HTTP status: " + response.getStatusCode();
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error during login: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return "HTTP Error during login: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for login: " + e.getMessage());
            return "Failed to connect to ERPNext for login: " + e.getMessage();
        } catch (Exception e) {
            System.err.println("Unexpected error during login: " + e.getMessage());
            e.printStackTrace();
            return "Unexpected error during login: " + e.getMessage();
        }
    }

    public List<Employee> getEmployees(String search) {
        try {
            checkSessionOrThrow();
            
            String url = baseUrl + "resource/Employee?fields=[\"*\"]&filters=[[\"status\",\"=\",\"Active\"]]";
            if (search != null && !search.isEmpty()) {
                url = baseUrl + "resource/Employee?filters=[[\"status\",\"=\",\"Active\"],[\"first_name\",\"like\",\"%" + search + "%\"]]";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Fetching employees with URL: " + url);
            System.out.println("Cookies sent: " + cookieStore.getCookies());

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to fetch employees: HTTP " + response.getStatusCode() + " - " + response.getBody());
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("data")) {
                throw new RuntimeException("Invalid response from ERPNext: " + responseBody);
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            List<Employee> employees = new ArrayList<>();
            for (Map<String, Object> item : data) {
                Employee emp = new Employee();
                emp.setName((String) item.get("name"));
                emp.setFirstName((String) item.get("first_name"));
                emp.setLastName((String) item.get("last_name"));
                emp.setDepartment((String) item.get("department"));
                employees.add(emp);
            }
            return employees;
        } catch (HttpClientErrorException ex) {
            System.err.println("HTTP Error fetching employees: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch employees: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            System.err.println("RestClientException fetching employees: " + ex.getMessage());
            throw new RuntimeException("Failed to connect to ERPNext: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            System.err.println("Unexpected error fetching employees: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException("Unexpected error: " + ex.getMessage(), ex);
        }
    }

  public Employee getEmployeeDetails(String employeeId) {
    try {
        checkSessionOrThrow();
        
        String url = baseUrl + "resource/Employee/" + employeeId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        System.out.println("Fetching employee details with URL: " + url);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Employee emp = new Employee();
        emp.setName((String) data.get("name"));
        emp.setFirstName((String) data.get("first_name"));
        emp.setLastName((String) data.get("last_name"));
        emp.setDepartment((String) data.get("department"));

        String salaryUrl = baseUrl + "resource/Salary Slip?filters=[[\"employee\",\"=\",\"" + employeeId + "\"]]";
        System.out.println("Fetching salary slips with URL: " + salaryUrl);
        ResponseEntity<Map> salaryResponse = restTemplate.exchange(salaryUrl, HttpMethod.GET, entity, Map.class);
        List<Map<String, Object>> salaryData = (List<Map<String, Object>>) salaryResponse.getBody().get("data");
        List<SalaryComponent> components = new ArrayList<>();
        
        if (salaryData != null && !salaryData.isEmpty()) {
            for (Map<String, Object> slip : salaryData) {
                List<Map<String, Object>> earnings = (List<Map<String, Object>>) slip.get("earnings");
                if (earnings != null && !earnings.isEmpty()) {
                    for (Map<String, Object> earning : earnings) {
                        SalaryComponent comp = new SalaryComponent();
                        comp.setComponent((String) earning.get("salary_component"));
                        comp.setAmount(((Number) earning.get("amount")).doubleValue());
                        comp.setMonth(((String) slip.get("posting_date")).substring(0, 7));
                        components.add(comp);
                    }
                } else {
                    System.out.println("No earnings data for salary slip of employee: " + employeeId);
                }
            }
        } else {
            System.out.println("No salary slips found for employee: " + employeeId);
        }
        
        emp.setSalaryComponents(components);
        return emp;
    } catch (HttpClientErrorException ex) {
        System.err.println("HTTP Error fetching employee details: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
        throw new RuntimeException("Failed to fetch employee details: " + ex.getResponseBodyAsString(), ex);
    } catch (RestClientException ex) {
        System.err.println("RestClientException fetching employee details: " + ex.getMessage());
        throw new RuntimeException("Failed to connect to ERPNext: " + ex.getMessage(), ex);
    } catch (Exception ex) {
        System.err.println("Unexpected error fetching employee details: " + ex.getMessage());
        ex.printStackTrace();
        throw new RuntimeException("Unexpected error: " + ex.getMessage(), ex);
    }
    }

    public Payslip getPayslip(String employeeId, String month) {
        try {
            checkSessionOrThrow();
            
            String url = baseUrl + "resource/Salary Slip?filters=[[\"employee\",\"=\",\"" + employeeId + "\"],[\"posting_date\",\"like\",\"" + month + "%\"]]";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Fetching payslip with URL: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            if (data.isEmpty()) {
                return new Payslip();
            }
            Map<String, Object> slip = data.get(0);
            Payslip payslip = new Payslip();
            payslip.setEmployeeName((String) slip.get("employee_name"));
            payslip.setMonth(month);
            List<SalaryComponent> components = new ArrayList<>();
            List<Map<String, Object>> earnings = (List<Map<String, Object>>) slip.get("earnings");
            double total = 0.0;
            for (Map<String, Object> earning : earnings) {
                SalaryComponent comp = new SalaryComponent();
                comp.setComponent((String) earning.get("salary_component"));
                comp.setAmount(((Number) earning.get("amount")).doubleValue());
                comp.setMonth(month);
                components.add(comp);
                total += comp.getAmount();
            }
            payslip.setComponents(components);
            payslip.setTotal(total);
            return payslip;
        } catch (HttpClientErrorException ex) {
            System.err.println("HTTP Error fetching payslip: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch payslip: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            System.err.println("RestClientException fetching payslip: " + ex.getMessage());
            throw new RuntimeException("Failed to connect to ERPNext: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            System.err.println("Unexpected error fetching payslip: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException("Unexpected error: " + ex.getMessage(), ex);
        }
    }

public List<Payslip> getMonthlyPayslips(String month) {
    try {
        checkSessionOrThrow();
        
        // Validate month format (should be YYYY-MM)
        if (!month.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Month parameter must be in YYYY-MM format");
        }
        
        // Calculate start and end dates for the month
        java.time.YearMonth yearMonth = java.time.YearMonth.parse(month);
        String startDate = month + "-01";
        String endDate = yearMonth.atEndOfMonth().toString(); // Get the last day of the month
        
        String url = baseUrl + "resource/Salary Slip?filters=[[\"posting_date\",\"between\",[\"" 
            + startDate + "\",\"" + endDate + "\"]]]";
            
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        System.out.println("Fetching monthly payslips with URL: " + url);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        if (data == null) {
            System.out.println("No payslip data returned from ERPNext");
            return new ArrayList<>();
        }
        
        List<Payslip> payslips = new ArrayList<>();
        for (Map<String, Object> slip : data) {
            Payslip payslip = new Payslip();
            payslip.setEmployeeName((String) slip.get("employee_name"));
            payslip.setMonth(month);
            List<SalaryComponent> components = new ArrayList<>();
            
            List<Map<String, Object>> earnings = (List<Map<String, Object>>) slip.get("earnings");
            double total = 0.0;
            if (earnings != null && !earnings.isEmpty()) {
                for (Map<String, Object> earning : earnings) {
                    SalaryComponent comp = new SalaryComponent();
                    comp.setComponent((String) earning.get("salary_component"));
                    comp.setAmount(((Number) earning.get("amount")).doubleValue());
                    comp.setMonth(month);
                    components.add(comp);
                    total += comp.getAmount();
                }
            } else {
                System.out.println("No earnings data for employee: " + slip.get("employee_name"));
            }
            
            payslip.setComponents(components);
            payslip.setTotal(total);
            payslips.add(payslip);
        }
        return payslips;
    } catch (HttpClientErrorException ex) {
        System.err.println("HTTP Error fetching monthly payslips: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
        throw new RuntimeException("Failed to fetch monthly payslips: " + ex.getResponseBodyAsString(), ex);
    } catch (RestClientException ex) {
        System.err.println("RestClientException fetching monthly payslips: " + ex.getMessage());
        throw new RuntimeException("Failed to connect to ERPNext: " + ex.getMessage(), ex);
    } catch (Exception ex) {
        System.err.println("Unexpected error fetching monthly payslips: " + ex.getMessage());
        ex.printStackTrace();
        throw new RuntimeException("Unexpected error: " + ex.getMessage(), ex);
    }
}
}