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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

public List<Employee> getEmployees(Map<String, String> filters) {
        try {
            checkSessionOrThrow();
            
            List<String> filterList = new ArrayList<>();
            filterList.add("[\"status\",\"=\",\"Active\"]");
            
            if (filters != null && !filters.isEmpty()) {
                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    String field = entry.getKey();
                    String value = entry.getValue();
                    if (value != null && !value.isEmpty()) {
                        if (field.equals("date_of_joining") || field.equals("date_of_birth")) {
                            filterList.add("[\"" + field + "\",\"=\",\"" + value + "\"]");
                        } else {
                            filterList.add("[\"" + field + "\",\"like\",\"%" + value + "%\"]");
                        }
                    }
                }
            }

            String filtersStr = filterList.isEmpty() ? "" : "&filters=[" + String.join(",", filterList) + "]";
            String url = baseUrl + "resource/Employee?fields=[\"*\"]" + filtersStr;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Fetching employees with URL: " + url);
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
                emp.setEmployeeName((String) item.get("employee_name"));
                emp.setDesignation((String) item.get("designation"));
                emp.setCompany((String) item.get("company"));
                emp.setDateOfJoining((String) item.get("date_of_joining"));
                emp.setGender((String) item.get("gender"));
                emp.setDateOfBirth((String) item.get("date_of_birth"));
                emp.setEmploymentType((String) item.get("employment_type"));
                emp.setStatus((String) item.get("status"));
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

    public List<String> getGenderList() {
        try {
            checkSessionOrThrow();
            String url = baseUrl + "resource/Gender?fields=[\"*\"]";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Fetching gender list with URL: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to fetch gender list: HTTP " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("data")) {
                throw new RuntimeException("Invalid response from ERPNext for gender list");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            List<String> genders = data.stream()
                    .map(item -> (String) item.get("gender"))
                    .filter(gender -> gender != null)
                    .distinct()
                    .collect(Collectors.toList());
            return genders;
        } catch (HttpClientErrorException ex) {
            System.err.println("HTTP Error fetching gender list: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch gender list: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            System.err.println("RestClientException fetching gender list: " + ex.getMessage());
            throw new RuntimeException("Failed to connect to ERPNext: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            System.err.println("Unexpected error fetching gender list: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException("Unexpected error: " + ex.getMessage(), ex);
        }
    }

    public List<String> getDepartmentList() {
        try {
            checkSessionOrThrow();
            String url = baseUrl + "resource/Department?fields=[\"name\"]";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Fetching department list with URL: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to fetch department list: HTTP " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("data")) {
                throw new RuntimeException("Invalid response from ERPNext for department list");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            List<String> departments = data.stream()
                    .map(item -> (String) item.get("name"))
                    .filter(name -> name != null)
                    .collect(Collectors.toList());
            return departments;
        } catch (HttpClientErrorException ex) {
            System.err.println("HTTP Error fetching department list: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch department list: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            System.err.println("RestClientException fetching department list: " + ex.getMessage());
            throw new RuntimeException("Failed to connect to ERPNext: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            System.err.println("Unexpected error fetching department list: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException("Unexpected error: " + ex.getMessage(), ex);
        }
    }

    public List<String> getDesignationList() {
        try {
            checkSessionOrThrow();
            String url = baseUrl + "resource/Designation?fields=[\"name\"]";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Fetching designation list with URL: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to fetch designation list: HTTP " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("data")) {
                throw new RuntimeException("Invalid response from ERPNext for designation list");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            List<String> designations = data.stream()
                    .map(item -> (String) item.get("name"))
                    .filter(name -> name != null)
                    .collect(Collectors.toList());
            return designations;
        } catch (HttpClientErrorException ex) {
            System.err.println("HTTP Error fetching designation list: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch designation list: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            System.err.println("RestClientException fetching designation list: " + ex.getMessage());
            throw new RuntimeException("Failed to connect to ERPNext: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            System.err.println("Unexpected error fetching designation list: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException("Unexpected error: " + ex.getMessage(), ex);
        }
    }

    public List<String> getCompanyList() {
        try {
            checkSessionOrThrow();
            String url = baseUrl + "resource/Company?fields=[\"name\"]";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Fetching company list with URL: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to fetch company list: HTTP " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !responseBody.containsKey("data")) {
                throw new RuntimeException("Invalid response from ERPNext for company list");
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            List<String> companies = data.stream()
                    .map(item -> (String) item.get("name"))
                    .filter(name -> name != null)
                    .collect(Collectors.toList());
            return companies;
        } catch (HttpClientErrorException ex) {
            System.err.println("HTTP Error fetching company list: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch company list: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            System.err.println("RestClientException fetching company list: " + ex.getMessage());
            throw new RuntimeException("Failed to connect to ERPNext: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            System.err.println("Unexpected error fetching company list: " + ex.getMessage());
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
            emp.setEmployeeName((String) data.get("employee_name"));
            emp.setDesignation((String) data.get("designation"));
            emp.setCompany((String) data.get("company"));
            emp.setDateOfJoining((String) data.get("date_of_joining"));
            emp.setGender((String) data.get("gender"));
            emp.setDateOfBirth((String) data.get("date_of_birth"));
            emp.setEmploymentType((String) data.get("employment_type"));
            emp.setStatus((String) data.get("status"));

            String salaryUrl = baseUrl + "resource/Salary Slip?fields=[\"name\",\"employee\",\"employee_name\",\"start_date\",\"end_date\",\"gross_pay\",\"net_pay\",\"status\",\"earnings\"]&filters=[[\"employee\",\"=\",\"" + employeeId + "\"]]";
            System.out.println("Fetching salary slips with URL: " + salaryUrl);
            ResponseEntity<Map> salaryResponse = restTemplate.exchange(salaryUrl, HttpMethod.GET, entity, Map.class);
            List<Map<String, Object>> salaryData = (List<Map<String, Object>>) salaryResponse.getBody().get("data");
            List<Payslip> payslips = new ArrayList<>();
            
            if (salaryData != null && !salaryData.isEmpty()) {
                for (Map<String, Object> slip : salaryData) {
                    Payslip payslip = new Payslip();
                    payslip.setName((String) slip.get("name"));
                    payslip.setEmployee((String) slip.get("employee"));
                    payslip.setEmployeeName((String) slip.get("employee_name"));
                    String startDate = (String) slip.get("start_date");
                    payslip.setMonth(startDate != null ? startDate.substring(0, 7) : null);
                    payslip.setStartDate(startDate);
                    payslip.setEndDate((String) slip.get("end_date"));
                    payslip.setGrossPay(((Number) slip.get("gross_pay")).doubleValue());
                    payslip.setNetPay(((Number) slip.get("net_pay")).doubleValue());
                    payslip.setStatus((String) slip.get("status"));
                    List<SalaryComponent> components = new ArrayList<>();
                    
                    List<Map<String, Object>> earnings = (List<Map<String, Object>>) slip.get("earnings");
                    double total = 0.0;
                    if (earnings != null && !earnings.isEmpty()) {
                        for (Map<String, Object> earning : earnings) {
                            SalaryComponent comp = new SalaryComponent();
                            comp.setComponent((String) earning.get("salary_component"));
                            comp.setAmount(((Number) earning.get("amount")).doubleValue());
                            comp.setMonth(startDate != null ? startDate.substring(0, 7) : null);
                            components.add(comp);
                            total += comp.getAmount();
                        }
                    } else {
                        System.out.println("No earnings data for salary slip of employee: " + employeeId);
                    }
                    
                    payslip.setComponents(components);
                    payslip.setTotal(total);
                    payslips.add(payslip);
                }
            } else {
                System.out.println("No salary slips found for employee: " + employeeId);
            }
            
            emp.setPayslips(payslips);
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
            
            String url = baseUrl + "resource/Salary Slip?fields=[\"name\",\"employee\",\"employee_name\",\"start_date\",\"end_date\",\"gross_pay\",\"net_pay\",\"status\",\"earnings\"]&filters=[[\"employee\",\"=\",\"" + employeeId + "\"],[\"posting_date\",\"like\",\"" + month + "%\"]]";
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
            payslip.setName((String) slip.get("name"));
            payslip.setEmployee((String) slip.get("employee"));
            payslip.setEmployeeName((String) slip.get("employee_name"));
            payslip.setMonth(month);
            payslip.setStartDate((String) slip.get("start_date"));
            payslip.setEndDate((String) slip.get("end_date"));
            payslip.setGrossPay(((Number) slip.get("gross_pay")).doubleValue());
            payslip.setNetPay(((Number) slip.get("net_pay")).doubleValue());
            payslip.setStatus((String) slip.get("status"));
            List<Payslip> components = new ArrayList<>();
            List<Map<String, Object>> earnings = (List<Map<String, Object>>) slip.get("earnings");
            double total = 0.0;
            for (Map<String, Object> earning : earnings) {
                Payslip comp = new Payslip();
                comp.setStatus((String) earning.get("status"));
                comp.setMonth(month);
                components.add(comp);
            }
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
            
            String url;
            if (month == null || month.isEmpty()) {
                url = baseUrl + "resource/Salary Slip?fields=[\"name\",\"employee\",\"employee_name\",\"start_date\",\"end_date\",\"gross_pay\",\"net_pay\",\"status\",\"earnings\"]";
            } else {
                if (!month.matches("\\d{4}-\\d{2}")) {
                    throw new IllegalArgumentException("Month parameter must be in YYYY-MM format");
                }
                YearMonth yearMonth = YearMonth.parse(month);
                String startDate = month + "-01";
                String endDate = yearMonth.atEndOfMonth().toString();
                url = baseUrl + "resource/Salary Slip?fields=[\"name\",\"employee\",\"employee_name\",\"start_date\",\"end_date\",\"gross_pay\",\"net_pay\",\"status\",\"earnings\"]&filters=[[\"posting_date\",\"between\",[\"" 
                    + startDate + "\",\"" + endDate + "\"]]]";
            }
            
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
                payslip.setName((String) slip.get("name"));
                payslip.setEmployee((String) slip.get("employee"));
                payslip.setEmployeeName((String) slip.get("employee_name"));
                String startDate = (String) slip.get("start_date");
                payslip.setMonth(month != null ? month : startDate != null ? startDate.substring(0, 7) : null);
                payslip.setStartDate(startDate);
                payslip.setEndDate((String) slip.get("end_date"));
                payslip.setGrossPay(((Number) slip.get("gross_pay")).doubleValue());
                payslip.setNetPay(((Number) slip.get("net_pay")).doubleValue());
                payslip.setStatus((String) slip.get("status"));
                List<SalaryComponent> components = new ArrayList<>();
                
                List<Map<String, Object>> earnings = (List<Map<String, Object>>) slip.get("earnings");
                double total = 0.0;
                if (earnings != null && !earnings.isEmpty()) {
                    for (Map<String, Object> earning : earnings) {
                        SalaryComponent comp = new SalaryComponent();
                        comp.setComponent((String) earning.get("salary_component"));
                        comp.setAmount(((Number) earning.get("amount")).doubleValue());
                        comp.setMonth(month != null ? month : startDate != null ? startDate.substring(0, 7) : null);
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
    }}