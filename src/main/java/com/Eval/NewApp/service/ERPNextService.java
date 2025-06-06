
package com.Eval.NewApp.service;

import com.Eval.NewApp.model.Employee;
import com.Eval.NewApp.model.Payslip;
import com.Eval.NewApp.model.SalaryComponent;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    public String getSessionId() {
    List<Cookie> cookies = cookieStore.getCookies();
    return cookies.stream()
            .filter(c -> c.getName().equals("sid"))
            .findFirst()
            .map(Cookie::getValue)
            .orElse(null);
}
public void clearCookies() {
        cookieStore.clear();
        System.out.println("Cookies cleared in CookieStore.");
    }

    public void checkSessionOrThrow() {
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
            System.out.println("Attempting to fetch employees with filters: " + filters);
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
            String endpoint = "resource/Employee?fields=[\"*\"]" + filtersStr;
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Sending GET request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Employee: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("data")) {
                    System.out.println("Fetch employees successful with HTTP status: " + response.getStatusCode());
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
                } else {
                    System.out.println("Fetch employees failed: No 'data' in response.");
                    return Collections.emptyList();
                }
            } else {
                System.out.println("Fetch employees failed: Invalid response. HTTP status: " + response.getStatusCode());
                return Collections.emptyList();
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error fetching employees: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for employees: " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Unexpected error fetching employees: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<String> getGenderList() {
        try {
            System.out.println("Attempting to fetch gender list");
            checkSessionOrThrow();

            String endpoint = "resource/Gender?fields=[\"*\"]";
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Sending GET request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Gender: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("data")) {
                    System.out.println("Fetch gender list successful with HTTP status: " + response.getStatusCode());
                    List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
                    return data.stream()
                            .map(item -> (String) item.get("gender"))
                            .filter(gender -> gender != null)
                            .distinct()
                            .collect(Collectors.toList());
                } else {
                    System.out.println("Fetch gender list failed: No 'data' in response.");
                    return Collections.emptyList();
                }
            } else {
                System.out.println("Fetch gender list failed: Invalid response. HTTP status: " + response.getStatusCode());
                return Collections.emptyList();
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error fetching gender list: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for gender list: " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Unexpected error fetching gender list: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<String> getDepartmentList() {
        try {
            System.out.println("Attempting to fetch department list");
            checkSessionOrThrow();

            String endpoint = "resource/Department?fields=[\"name\"]";
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Sending GET request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Department: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("data")) {
                    System.out.println("Fetch department list successful with HTTP status: " + response.getStatusCode());
                    List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
                    return data.stream()
                            .map(item -> (String) item.get("name"))
                            .filter(name -> name != null)
                            .collect(Collectors.toList());
                } else {
                    System.out.println("Fetch department list failed: No 'data' in response.");
                    return Collections.emptyList();
                }
            } else {
                System.out.println("Fetch department list failed: Invalid response. HTTP status: " + response.getStatusCode());
                return Collections.emptyList();
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error fetching department list: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for department list: " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Unexpected error fetching department list: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<String> getDesignationList() {
        try {
            System.out.println("Attempting to fetch designation list");
            checkSessionOrThrow();

            String endpoint = "resource/Designation?fields=[\"name\"]";
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Sending GET request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Designation: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("data")) {
                    System.out.println("Fetch designation list successful with HTTP status: " + response.getStatusCode());
                    List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
                    return data.stream()
                            .map(item -> (String) item.get("name"))
                            .filter(name -> name != null)
                            .collect(Collectors.toList());
                } else {
                    System.out.println("Fetch designation list failed: No 'data' in response.");
                    return Collections.emptyList();
                }
            } else {
                System.out.println("Fetch designation list failed: Invalid response. HTTP status: " + response.getStatusCode());
                return Collections.emptyList();
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error fetching designation list: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for designation list: " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Unexpected error fetching designation list: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public List<String> getCompanyList() {
        try {
            System.out.println("Attempting to fetch company list");
            checkSessionOrThrow();

            String endpoint = "resource/Company?fields=[\"name\"]";
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Sending GET request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Company: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody.containsKey("data")) {
                    System.out.println("Fetch company list successful with HTTP status: " + response.getStatusCode());
                    List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
                    return data.stream()
                            .map(item -> (String) item.get("name"))
                            .filter(name -> name != null)
                            .collect(Collectors.toList());
                } else {
                    System.out.println("Fetch company list failed: No 'data' in response.");
                    return Collections.emptyList();
                }
            } else {
                System.out.println("Fetch company list failed: Invalid response. HTTP status: " + response.getStatusCode());
                return Collections.emptyList();
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error fetching company list: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for company list: " + e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Unexpected error fetching company list: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public Employee getEmployeeDetails(String employeeId) {
        try {
            System.out.println("Attempting to fetch details for employee: " + employeeId);
            checkSessionOrThrow();

            String endpoint = "resource/Employee/" + employeeId;
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Sending GET request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after employee details request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Employee/" + employeeId + ": " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().containsKey("data")) {
                System.out.println("Fetch employee details successful with HTTP status: " + response.getStatusCode());
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

                String salaryEndpoint = "resource/Salary Slip?fields=[\"name\",\"employee\",\"employee_name\",\"start_date\",\"end_date\",\"gross_pay\",\"net_pay\",\"status\",\"earnings\"]&filters=[[\"employee\",\"=\",\"" + employeeId + "\"]]";
                String salaryUrl = baseUrl + salaryEndpoint;
                System.out.println("Constructed salary slips URL: " + salaryUrl);

                System.out.println("Sending GET request for salary slips to: " + salaryUrl);
                ResponseEntity<Map> salaryResponse = restTemplate.exchange(salaryUrl, HttpMethod.GET, entity, Map.class);

                System.out.println("Cookies after salary slips request:");
                for (Cookie cookie : cookies) {
                    System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
                }

                String salaryRawResponse = salaryResponse.getBody() != null ? salaryResponse.getBody().toString() : "null";
                System.out.println("Raw response from /api/resource/Salary Slip: " + salaryRawResponse);

                List<Payslip> payslips = new ArrayList<>();
                if (salaryResponse.getStatusCode().is2xxSuccessful() && salaryResponse.getBody() != null && salaryResponse.getBody().containsKey("data")) {
                    System.out.println("Fetch salary slips successful with HTTP status: " + salaryResponse.getStatusCode());
                    List<Map<String, Object>> salaryData = (List<Map<String, Object>>) salaryResponse.getBody().get("data");
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
                } else {
                    System.out.println("Fetch salary slips failed: Invalid response. HTTP status: " + salaryResponse.getStatusCode());
                }

                emp.setPayslips(payslips);
                return emp;
            } else {
                System.out.println("Fetch employee details failed: Invalid response. HTTP status: " + response.getStatusCode());
                return null;
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error fetching employee details: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return null;
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for employee details: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Unexpected error fetching employee details: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public Payslip getPayslip(String employeeId, String month) {
        try {
            System.out.println("Attempting to fetch payslip for employee: " + employeeId + ", month: " + month);
            checkSessionOrThrow();

            String endpoint = "resource/Salary Slip?fields=[\"name\",\"employee\",\"employee_name\",\"start_date\",\"end_date\",\"gross_pay\",\"net_pay\",\"status\",\"earnings\"]&filters=[[\\\"employee\\\",\\\"=\\\",\\\"" + employeeId + "\\\"],[\\\"posting_date\\\",\\\"like\\\",\\\"" + month + "%\\\"]]";
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Sending GET request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Salary Slip: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().containsKey("data")) {
                System.out.println("Fetch payslip successful with HTTP status: " + response.getStatusCode());
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data.isEmpty()) {
                    System.out.println("No payslip found for employee: " + employeeId + ", month: " + month);
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
                }
                payslip.setComponents(components);
                payslip.setTotal(total);
                return payslip;
            } else {
                System.out.println("Fetch payslip failed: Invalid response. HTTP status: " + response.getStatusCode());
                return new Payslip();
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error fetching payslip: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return new Payslip();
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for payslip: " + e.getMessage());
            return new Payslip();
        } catch (Exception e) {
            System.err.println("Unexpected error fetching payslip: " + e.getMessage());
            e.printStackTrace();
            return new Payslip();
        }
    }

    public List<Payslip> getMonthlyPayslips(String month) {
        try {
            System.out.println("Attempting to fetch monthly payslips for month: " + month);
            checkSessionOrThrow();

            String endpoint;
            if (month == null || month.isEmpty()) {
                endpoint = "resource/Salary Slip?fields=[\"name\",\"employee\",\"employee_name\",\"start_date\",\"end_date\",\"gross_pay\",\"net_pay\",\"status\",\"earnings\"]";
            } else {
                if (!month.matches("\\d{4}-\\d{2}")) {
                    throw new IllegalArgumentException("Month parameter must be in YYYY-MM format");
                }
                YearMonth yearMonth = YearMonth.parse(month);
                String startDate = month + "-01";
                String endDate = yearMonth.atEndOfMonth().toString();
                // Construct the filters JSON string
                String filters = "[[\"posting_date\",\"between\",[\"" + startDate + "\",\"" + endDate + "\"]]]";
                endpoint = "resource/Salary Slip?fields=[\"name\",\"employee\",\"employee_name\",\"start_date\",\"end_date\",\"gross_pay\",\"net_pay\",\"status\",\"earnings\"]&filters=" + filters;
            }
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Sending GET request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Salary Slip: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().containsKey("data")) {
                System.out.println("Fetch monthly payslips successful with HTTP status: " + response.getStatusCode());
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null) {
                    System.out.println("No payslip data returned from API");
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
            } else {
                System.out.println("Fetch monthly payslips failed: Invalid response. HTTP status: " + response.getStatusCode());
                return new ArrayList<>();
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error fetching monthly payslips: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return new ArrayList<>();
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for monthly payslips: " + e.getMessage());
            return new ArrayList<>();
        } catch (Exception e) {
            System.err.println("Unexpected error fetching monthly payslips: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public ResponseEntity<Map> getResource(String endpoint) {
        try {
            System.out.println("Attempting to fetch resource with endpoint: " + endpoint);
            checkSessionOrThrow();

            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            System.out.println("Sending GET request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/" + endpoint + ": " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Fetch resource successful with HTTP status: " + response.getStatusCode());
                return response;
            } else {
                System.out.println("Fetch resource failed: Invalid response. HTTP status: " + response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error fetching resource: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for resource: " + e.getMessage());
            throw new RuntimeException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("Unexpected error fetching resource: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    public ResponseEntity<Map> createEmployee(Map<String, Object> data) {
        try {
            System.out.println("Attempting to create employee with data: " + data);
            checkSessionOrThrow();

            String endpoint = "resource/Employee";
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);

            System.out.println("Sending POST request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Employee: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Create employee successful with HTTP status: " + response.getStatusCode());
                return response;
            } else {
                System.out.println("Create employee failed: Invalid response. HTTP status: " + response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error creating employee: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for employee creation: " + e.getMessage());
            throw new RuntimeException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("Unexpected error creating employee: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    public ResponseEntity<Map> updateEmployee(String employeeId, Map<String, Object> data) {
        try {
            System.out.println("Attempting to update employee: " + employeeId + " with data: " + data);
            checkSessionOrThrow();

            String endpoint = "resource/Employee/" + employeeId;
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);

            System.out.println("Sending PUT request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Employee/" + employeeId + ": " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Update employee successful with HTTP status: " + response.getStatusCode());
                return response;
            } else {
                System.out.println("Update employee failed: Invalid response. HTTP status: " + response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error updating employee: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for employee update: " + e.getMessage());
            throw new RuntimeException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("Unexpected error updating employee: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    public ResponseEntity<Map> createCompany(Map<String, Object> data) {
        try {
            System.out.println("Attempting to create company with data: " + data);
            checkSessionOrThrow();

            String endpoint = "resource/Company";
            String url = baseUrl + endpoint;
            System.out.println("Constructed URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);

            System.out.println("Sending POST request to: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            System.out.println("Cookies after request:");
            for (Cookie cookie : cookies) {
                System.out.println("Cookie: " + cookie.getName() + "=" + cookie.getValue() + "; Domain=" + cookie.getDomain() + "; Path=" + cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            System.out.println("Raw response from /api/resource/Company: " + rawResponse);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Create company successful with HTTP status: " + response.getStatusCode());
                return response;
            } else {
                System.out.println("Create company failed: Invalid response. HTTP status: " + response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
            }
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error creating company: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            System.err.println("Failed to connect to ERPNext for company creation: " + e.getMessage());
            throw new RuntimeException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("Unexpected error creating company: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Unexpected error: " + e.getMessage(), e);
        }
    }

public ResponseEntity<Map> importEmployee(Map<String, Object> employeeData) {
    try {
        System.out.println("Attempting to import employee with data: " + employeeData);
        checkSessionOrThrow();

        // Verify required fields
        if (!employeeData.containsKey("company") || !employeeData.containsKey("first_name") || 
            !employeeData.containsKey("employee_name")) {
            throw new IllegalArgumentException("Required fields missing: company, first_name, employee_name");
        }

        String companyName = (String) employeeData.get("company");
        String employeeName = (String) employeeData.get("employee_name");

        // Check if the company exists
        List<String> companies = getCompanyList();
        if (!companies.contains(companyName)) {
            System.out.println("Company " + companyName + " does not exist. Creating new company...");
            Map<String, Object> companyData = new HashMap<>();
            companyData.put("name", companyName);
            companyData.put("company_name", companyName);
            companyData.put("default_currency", "USD"); // Set a default currency (e.g., USD)
            // Add other required fields if necessary (e.g., country, if required by your ERPNext setup)
            companyData.put("country", "United States"); // Optional, adjust as needed
            ResponseEntity<Map> companyResponse = createCompany(companyData);
            
            if (!companyResponse.getStatusCode().is2xxSuccessful()) {
                System.err.println("Failed to create company: " + companyName);
                return ResponseEntity.status(companyResponse.getStatusCode())
                        .body(Map.of("error", "Failed to create company: " + companyName));
            }
            System.out.println("Company " + companyName + " created successfully");
        }

        // Rest of the method remains unchanged
        Map<String, String> filters = new HashMap<>();
        filters.put("employee_name", employeeName);
        List<Employee> existingEmployees = getEmployees(filters);
        
        if (!existingEmployees.isEmpty()) {
            System.out.println("Employee " + employeeName + " already exists with ID: " + existingEmployees.get(0).getName());
            return ResponseEntity.ok(Map.of(
                "message", "Employee already exists",
                "employee_id", existingEmployees.get(0).getName()
            ));
        }

        System.out.println("Creating new employee: " + employeeName);
        ResponseEntity<Map> employeeResponse = createEmployee(employeeData);
        
        if (employeeResponse.getStatusCode().is2xxSuccessful()) {
            System.out.println("Employee " + employeeName + " created successfully");
            Map<String, Object> responseBody = employeeResponse.getBody();
            if (responseBody != null && responseBody.containsKey("data")) {
                Map<String, Object> employeeDataResponse = (Map<String, Object>) responseBody.get("data");
                return ResponseEntity.ok(Map.of(
                    "message", "Employee created successfully",
                    "employee_id", employeeDataResponse.get("name")
                ));
            }
            return employeeResponse;
        } else {
            System.err.println("Failed to create employee: " + employeeName);
            return ResponseEntity.status(employeeResponse.getStatusCode())
                .body(Map.of("error", "Failed to create employee: " + employeeName));
        }

    } catch (IllegalArgumentException e) {
        System.err.println("Invalid input data: " + e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (HttpClientErrorException e) {
        System.err.println("HTTP Error importing employee: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getResponseBodyAsString()));
    } catch (RestClientException e) {
        System.err.println("Failed to connect to ERPNext for employee import: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Failed to connect to ERPNext: " + e.getMessage()));
    } catch (Exception e) {
        System.err.println("Unexpected error importing employee: " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Unexpected error: " + e.getMessage()));
    }
}
}
