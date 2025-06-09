package com.Eval.NewApp.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.multipart.MultipartFile;

import com.Eval.NewApp.model.Employee;
import com.Eval.NewApp.model.SalaryImport;
import com.Eval.NewApp.model.SalarySlip;
import com.Eval.NewApp.model.SalaryStructure;

import jakarta.servlet.http.HttpSession;

@Service
public class ImportService {

    private static final Logger logger = LoggerFactory.getLogger(ImportService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CookieStore cookieStore;

    @Autowired
    private ERPNextService erpNextService;

    @Autowired
    private UtilService utilService;

    @Autowired
    private HttpSession session;

    @Value("${erpnext.base-url}")
    private String baseUrl;

    //------------------------------------------------------------------------------------------------------------------------------------------
    //----------------------------------------------------EMPLOYE-------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------------------------------------------------------

 public ResponseEntity<Map> importEmployee(Map<String, Object> employeeData, HttpSession session) {
        try {
            String company = (String) employeeData.get("company");
            if (company == null || company.trim().isEmpty()) {
                logger.error("Company is missing in employee data: {}", employeeData);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Company is required for employee");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Create Employee object to validate and format data
            Employee employee = new Employee();
            employee.setUtilService(utilService);
            employee.setName((String) employeeData.get("name"));
            employee.setFirstName((String) employeeData.get("first_name"));
            employee.setLastName((String) employeeData.get("last_name"));
            employee.setGenre((String) employeeData.get("gender"));
            employee.setCompany(company);
            employee.setDateEmbauche((String) employeeData.get("date_of_joining"));
            employee.setDateNaissance((String) employeeData.get("date_of_birth"));
            employee.setStatus((String) employeeData.get("status"));
            employee.validate();

            // Convert to API payload
            Map<String, Object> payload = employee.toMap(false);
            logger.info("Employee API payload: {}", payload);

            // Check if company exists
            List<String> companies = erpNextService.getCompanyList();
            if (!companies.contains(company)) {
                logger.info("Creating company {}...", company);
                Map<String, Object> companyData = new HashMap<>();
                companyData.put("company_name", company);
                companyData.put("default_currency", "MAD");
                companyData.put("country", "Madagascar");

                String holidayListName = createDefaultHolidayList(company);
                if (holidayListName != null) {
                    companyData.put("default_holiday_list", holidayListName);
                } else {
                    logger.error("Holiday List 'My Company Holiday List 2025' not found for company {}", company);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Holiday List 'My Company Holiday List 2025' not found for company: " + company);
                    return ResponseEntity.badRequest().body(errorResponse);
                }

                ResponseEntity<Map> companyResponse = createCompany(companyData);
                if (!companyResponse.getStatusCode().is2xxSuccessful()) {
                    String errorMsg = companyResponse.getBody() != null ? companyResponse.getBody().toString() : "Unknown error";
                    logger.error("Failed to create company {}: {}", company, errorMsg);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Failed to create company: " + errorMsg);
                    return ResponseEntity.badRequest().body(errorResponse);
                }
                logger.info("Company {} created successfully", company);
            } else {
                logger.info("Company {} already exists, using existing company", company);
            }

            String ref = (String) employeeData.get("name");
            boolean employeeExists = checkEmployeeExists(ref, session);
            ResponseEntity<Map> response = employeeExists ? updateEmployee(ref, employee.toMap(true)) : createEmployee(payload);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                String erpNextEmployeeId = data != null ? (String) data.get("name") : ref;

                // Update refToNameMap
                @SuppressWarnings("unchecked")
                Map<String, String> refToNameMap = (Map<String, String>) session.getAttribute("employeeRefToNameMap");
                if (refToNameMap == null) {
                    refToNameMap = new HashMap<>();
                    session.setAttribute("employeeRefToNameMap", refToNameMap);
                }
                refToNameMap.put(ref, erpNextEmployeeId);
                logger.info("Mapped Ref {} to ERPNext Employee ID {}", ref, erpNextEmployeeId);

                Map<String, Object> result = new HashMap<>();
                result.put("message", "Employee " + employeeData.get("employee_name") + " successfully " + (employeeExists ? "updated" : "created"));
                result.put("employee_id", erpNextEmployeeId);
                return ResponseEntity.ok(result);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", response.getBody() != null ? response.getBody().toString() : "Unknown error");
                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Validation error importing employee: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Unexpected error importing employee: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Unexpected error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    private void validateForApi(Employee employee) throws IllegalArgumentException {
        if (employee.getFirstName() == null || employee.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (employee.getGenre() == null || employee.getGenre().trim().isEmpty()) {
            throw new IllegalArgumentException("Gender is required");
        }
        if (employee.getDateNaissance() == null || employee.getDateNaissance().trim().isEmpty()) {
            throw new IllegalArgumentException("Date of birth is required");
        }
        if (employee.getDateEmbauche() == null || employee.getDateEmbauche().trim().isEmpty()) {
            throw new IllegalArgumentException("Date of joining is required");
        }
        if (employee.getCompany() == null || employee.getCompany().trim().isEmpty()) {
            throw new IllegalArgumentException("Company is required");
        }
        // Validate date formats
        if (employee.getUtilService() == null) {
            throw new IllegalStateException("UtilService is not set");
        }
        try {
            employee.getUtilService().getFormattedDate(employee.getDateEmbauche());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format for Date embauche: " + employee.getDateEmbauche());
        }
        try {
            employee.getUtilService().getFormattedDate(employee.getDateNaissance());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format for Date naissance: " + employee.getDateNaissance());
        }
    }

    public ResponseEntity<Map> createEmployee(Map<String, Object> data) {
        try {
            logger.info("Attempting to create employee with data: {}", data);
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Employee";
            String url = baseUrl + endpoint;
            logger.info("Constructed URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);

            logger.info("Sending POST request to: {}", url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Create employee successful with HTTP status: {}", response.getStatusCode());
                return response;
            } else {
                logger.error("Create employee failed: Invalid response. HTTP status: {}", response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error creating employee: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            logger.error("Failed to connect to ERPNext for employee creation: {}", e.getMessage());
            throw new RuntimeException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error creating employee: {}", e.getMessage());
            throw new RuntimeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    public ResponseEntity<Map> updateEmployee(String employeeId, Map<String, Object> data) {
        try {
            logger.info("Attempting to update employee: {} with data: {}", employeeId, data);
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Employee/" + employeeId;
            String url = baseUrl + endpoint;
            logger.info("Constructed URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);

            logger.info("Sending PUT request to: {}", url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Update employee successful with HTTP status: {}", response.getStatusCode());
                return response;
            } else {
                logger.error("Update employee failed: Invalid response. HTTP status: {}", response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error updating employee: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            logger.error("Failed to connect to ERPNext for employee update: {}", e.getMessage());
            throw new RuntimeException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error updating employee: {}", e.getMessage());
            throw new RuntimeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    public ResponseEntity<Map> createCompany(Map<String, Object> data) {
        try {
            logger.info("Attempting to create company with data: {}", data);
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Company";
            String url = baseUrl + endpoint;
            logger.info("Constructed URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);

            logger.info("Sending POST request to: {}", url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Create company successful with HTTP status: {}", response.getStatusCode());
                return response;
            } else {
                logger.error("Create company failed: Invalid response. HTTP status: {}", response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error creating company: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            logger.error("Failed to connect to ERPNext for company creation: {}", e.getMessage());
            throw new RuntimeException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error creating company: {}", e.getMessage());
            throw new RuntimeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    private String createDefaultHolidayList(String companyName) {
        try {
            erpNextService.checkSessionOrThrow();

            String holidayListName = "My Company Holiday List 2025";
            String endpoint = "resource/Holiday List";
            String url = baseUrl + endpoint + "?fields=[\"name\"]&filters=[[\"name\",\"=\",\"" + holidayListName + "\"]]";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> checkEntity = new HttpEntity<>(headers);

            ResponseEntity<Map> checkResponse = restTemplate.getForEntity(url, Map.class);
            List<Map<String, Object>> existingHolidayLists = (List<Map<String, Object>>) checkResponse.getBody().get("data");

            if (checkResponse.getStatusCode().is2xxSuccessful() && !existingHolidayLists.isEmpty()) {
                logger.info("Holiday List '{}' already exists for company '{}'", holidayListName, companyName);
                return holidayListName;
            } else {
                logger.error("Holiday List '{}' does not exist for company '{}'", holidayListName, companyName);
                return null;
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error checking Holiday List for company {}: {}", companyName, e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            logger.error("Error checking Holiday List for company {}: {}", companyName, e.getMessage());
            return null;
        }
    }

    private boolean checkEmployeeExists(String ref, HttpSession session) {
        @SuppressWarnings("unchecked")
        Map<String, String> refToNameMap = (Map<String, String>) session.getAttribute("employeeRefToNameMap");
        try {
            erpNextService.checkSessionOrThrow();

            // Retrieve refToNameMap from session
            String employeeId = refToNameMap != null ? refToNameMap.getOrDefault(ref, ref) : ref;
            logger.info("Checking employee existence for ref {} (ERPNext ID: {})", ref, employeeId);

            String endpoint = "resource/Employee";
            String url = baseUrl + endpoint + "?fields=[\"name\"]&filters=[[\"name\",\"=\",\"" + employeeId + "\"]]";
            logger.info("Checking employee existence at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            List<Map<String, Object>> employeeData = (List<Map<String, Object>>) response.getBody().get("data");
            boolean exists = !employeeData.isEmpty();
            logger.info("Employee with ref {} (ERPNext ID: {}) exists: {}", ref, employeeId, exists);
            return exists;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error checking employee existence for ref {} (ERPNext ID: {}): {} - {}", 
                    ref, refToNameMap != null ? refToNameMap.getOrDefault(ref, ref) : ref, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (RestClientException e) {
            logger.error("Failed to connect to ERPNext for employee existence check for ref {}: {}", ref, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error checking employee existence for ref {}: {}", ref, e.getMessage());
            return false;
        }
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
    //----------------------------------------------------SALARY STRUCTURE----------------------------------------------------------------------
    //------------------------------------------------------------------------------------------------------------------------------------------

    public ResponseEntity<Map> importSalaryStructure(List<Map<String, Object>> componentDataList) {
        try {
            logger.info("Processing salary structure with {} components", componentDataList.size());

            // Validate input
            if (componentDataList == null || componentDataList.isEmpty()) {
                logger.error("Component data list is null or empty");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Component data list cannot be null or empty");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Group components by salary structure and company
            Map<String, List<Map<String, Object>>> structureComponentsMap = new HashMap<>();
            Map<String, String> structureCompanyMap = new HashMap<>();
            List<String> results = new ArrayList<>();

            for (Map<String, Object> component : componentDataList) {
                String salaryStructure = (String) component.get("salary_structure");
                String company = (String) component.get("company");
                String key = salaryStructure + "_" + company;

                structureComponentsMap.computeIfAbsent(key, k -> new ArrayList<>()).add(component);
                structureCompanyMap.putIfAbsent(key, company);
            }

            // Process each salary structure
            for (Map.Entry<String, List<Map<String, Object>>> entry : structureComponentsMap.entrySet()) {
                String[] keyParts = entry.getKey().split("_");
                String salaryStructureName = keyParts[0];
                String company = structureCompanyMap.get(entry.getKey());
                List<Map<String, Object>> components = entry.getValue();

                // Check if company exists
                List<String> companies = erpNextService.getCompanyList();
                if (!companies.contains(company)) {
                    logger.info("Creating company {}...", company);
                    Map<String, Object> companyData = new HashMap<>();
                    companyData.put("name", company);
                    companyData.put("company_name", company);
                    companyData.put("default_currency", "MAD");
                    companyData.put("country", "Madagascar");

                    String holidayListName = createDefaultHolidayList(company);
                    if (holidayListName != null) {
                        companyData.put("default_holiday_list", holidayListName);
                    } else {
                        logger.error("Holiday List 'My Company Holiday List 2025' not found for company {}", company);
                        results.add("Holiday List 'My Company Holiday List 2025' not found for company: " + company);
                        continue;
                    }

                    ResponseEntity<Map> companyResponse = createCompany(companyData);
                    if (!companyResponse.getStatusCode().is2xxSuccessful()) {
                        String errorMsg = companyResponse.getBody() != null ? companyResponse.getBody().toString() : "Unknown error";
                        logger.error("Failed to create company {}: {}", company, errorMsg);
                        results.add("Failed to create company: " + errorMsg);
                        continue;
                    }
                    logger.info("Company {} created successfully", company);
                } else {
                    logger.info("Company {} already exists, using existing company", company);
                }

                // Step 1: Create or update Salary Components
                for (Map<String, Object> component : components) {
                    int lineNumber = components.indexOf(component) + 2;
                    try {
                        String componentName = (String) component.get("name");
                        String normalizedName = utilService.normalizeName(componentName);
                        boolean componentExists = checkComponentExists(normalizedName);
                        logger.info("Attempting to {} component {} (normalized: {})",
                                componentExists ? "update" : "create", componentName, normalizedName);

                        String endpoint = "resource/Salary Component" + (componentExists ? "/" + normalizedName : "");
                        String url = baseUrl + endpoint;
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);

                        Map<String, Object> componentPayload = new HashMap<>();
                        componentPayload.put("salary_component", componentName);
                        componentPayload.put("salary_component_abbr", component.get("abbr"));
                        componentPayload.put("type", component.get("type").toString().equalsIgnoreCase("Earning") ? "Earning" : "Deduction");
                        String valeur = (String) component.get("valeur");
                        if (valeur != null && !valeur.isEmpty() && !valeur.equalsIgnoreCase("base")) {
                            componentPayload.put("depends_on_payment_days", 0);
                            componentPayload.put("is_tax_applicable", 0);
                        } else if (valeur != null && valeur.equalsIgnoreCase("base")) {
                            componentPayload.put("depends_on_payment_days", 1);
                        }

                        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(componentPayload, headers);
                        ResponseEntity<Map> response = restTemplate.exchange(url, componentExists ? HttpMethod.PUT : HttpMethod.POST, entity, Map.class);

                        if (response.getStatusCode().is2xxSuccessful()) {
                            results.add(String.format("Line %d: Component %s successfully %s",
                                    lineNumber, componentName, componentExists ? "updated" : "created"));
                            logger.info("Line {}: Component {} successfully {}", lineNumber, componentName, componentExists ? "updated" : "created");
                        } else {
                            String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                            results.add(String.format("Line %d: Failed to %s component %s: %s",
                                    lineNumber, componentExists ? "update" : "create", componentName, errorMsg));
                            logger.error("Line {}: Failed to {} component {}: {}", lineNumber, componentExists ? "update" : "create", componentName, errorMsg);
                        }
                    } catch (HttpClientErrorException e) {
                        String errorMsg = e.getResponseBodyAsString();
                        if (errorMsg.contains("DuplicateEntryError")) {
                            logger.info("Duplicate entry for component {}. Using existing component...", component.get("name"));
                            results.add(String.format("Line %d: Component %s already exists, using existing component",
                                    lineNumber, component.get("name")));
                        } else {
                            results.add(String.format("Line %d: API error for component %s: %s", lineNumber, component.get("name"), errorMsg));
                            logger.error("Line {}: API error for component {}: {}", lineNumber, component.get("name"), errorMsg);
                        }
                    } catch (Exception e) {
                        results.add(String.format("Line %d: Error processing component %s: %s", lineNumber, component.get("name"), e.getMessage()));
                        logger.error("Line {}: Error processing component {}: {}", lineNumber, component.get("name"), e.getMessage());
                    }
                }

                // Step 2: Create or update Salary Structure
                try {
                    boolean structureExists = checkStructureExists(salaryStructureName);
                    logger.info("Attempting to {} salary structure: {}", structureExists ? "update" : "create", salaryStructureName);

                    Map<String, Object> data = new HashMap<>();
                    data.put("doctype", "Salary Structure");
                    data.put("name", salaryStructureName);
                    data.put("company", company);
                    data.put("payroll_frequency", "Monthly");
                    data.put("is_active", "Yes");

                    List<Map<String, Object>> earnings = new ArrayList<>();
                    List<Map<String, Object>> deductions = new ArrayList<>();

                    for (Map<String, Object> component : components) {
                        Map<String, Object> child = new HashMap<>();
                        child.put("salary_component", component.get("name"));
                        child.put("salary_component_abbr", component.get("abbr"));
                        child.put("amount_based_on_formula", true);
                        String valeur = (String) component.get("valeur");
                        if (!valeur.equalsIgnoreCase("base")) {
                            child.put("formula", valeur);
                            child.put("depends_on_payment_days", 0);
                        } else {
                            child.put("formula", "base");
                            child.put("depends_on_payment_days", 1);
                        }
                        if (component.get("type").toString().equalsIgnoreCase("Earning")) {
                            earnings.add(child);
                            child.put("is_tax_applicable", true);
                        } else {
                            deductions.add(child);
                        }
                    }

                    data.put("earnings", earnings);
                    data.put("deductions", deductions);
                    logger.debug("Salary structure payload: {}", data);

                    String endpoint = "resource/Salary Structure" + (structureExists ? "/" + salaryStructureName : "");
                    String url = baseUrl + endpoint;
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(data, headers);

                    logger.info("Sending {} request to: {}", structureExists ? "PUT" : "POST", url);
                    ResponseEntity<Map> response = restTemplate.exchange(url, structureExists ? HttpMethod.PUT : HttpMethod.POST, entity, Map.class);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        results.add(String.format("Structure %s successfully %s", salaryStructureName, structureExists ? "updated" : "created"));
                        logger.info("Structure {} successfully {}", salaryStructureName, structureExists ? "updated" : "created");

                        ResponseEntity<Map> submitResponse = submitResource("Salary Structure", salaryStructureName);
                        if (submitResponse.getStatusCode().is2xxSuccessful()) {
                            results.add(String.format("Structure %s successfully submitted", salaryStructureName));
                            logger.info("Structure {} successfully submitted", salaryStructureName);
                        } else {
                            String errorMsg = submitResponse.getBody() != null ? submitResponse.getBody().toString() : "Unknown error";
                            results.add(String.format("Failed to submit structure %s: %s", salaryStructureName, errorMsg));
                            logger.error("Failed to submit structure {}: {}", salaryStructureName, errorMsg);
                        }
                    } else {
                        String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                        results.add(String.format("Failed to %s structure %s: %s",
                                structureExists ? "update" : "create", salaryStructureName, errorMsg));
                        logger.error("Failed to {} structure {}: {}", structureExists ? "update" : "create", salaryStructureName, errorMsg);
                    }
                } catch (HttpClientErrorException e) {
                    String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                    results.add(String.format("API error for structure %s: %s", salaryStructureName, errorMsg));
                    logger.error("API error for structure {}: {}", salaryStructureName, errorMsg);
                } catch (Exception e) {
                    results.add(String.format("Error processing structure %s: %s", salaryStructureName, e.getMessage()));
                    logger.error("Error processing structure {}: {}", salaryStructureName, e.getMessage());
                }
            }

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("results", results);
            responseBody.put("message", "Salary structures processed successfully");
            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            logger.error("Unexpected error importing salary structure: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Unexpected error: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    private boolean checkComponentExists(String componentName) {
        try {
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Salary Component";
            String url = baseUrl + endpoint + "?fields=[\"name\"]&filters=[[\"salary_component\",\"=\",\"" + componentName + "\"]]";
            logger.info("Checking component existence at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            List<Map<String, Object>> componentData = (List<Map<String, Object>>) response.getBody().get("data");
            logger.info("Component {} exists: {}", componentName, !componentData.isEmpty());
            return !componentData.isEmpty();
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error checking component existence for {}: {} - {}", componentName, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (RestClientException e) {
            logger.error("Failed to connect to ERPNext for component existence check: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error checking component existence for {}: {}", componentName, e.getMessage());
            return false;
        }
    }

    private boolean checkStructureExists(String structureName) {
        try {
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Salary Structure";
            String url = baseUrl + endpoint + "?fields=[\"name\"]&filters=[[\"name\",\"=\",\"" + structureName + "\"]]";
            logger.info("Checking structure existence at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            List<Map<String, Object>> structureData = (List<Map<String, Object>>) response.getBody().get("data");
            logger.info("Structure {} exists: {}", structureName, !structureData.isEmpty());
            return !structureData.isEmpty();
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error checking structure existence for {}: {} - {}", structureName, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (RestClientException e) {
            logger.error("Failed to connect to ERPNext for structure existence check: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error checking structure existence for {}: {}", structureName, e.getMessage());
            return false;
        }
    }

    private ResponseEntity<Map> submitResource(String resourceType, String id) {
        try {
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/" + resourceType + "/" + id + "?run_method=submit";
            String url = baseUrl + endpoint;
            logger.info("Submitting resource at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            logger.info("Sending POST request to: {}", url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            return response;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error submitting {} {}: {} - {}", resourceType, id, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (RestClientException e) {
            logger.error("Failed to connect to ERPNext for submitting {}: {}", resourceType, e.getMessage());
            throw new RuntimeException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error submitting {} {}: {}", resourceType, id, e.getMessage());
            throw new RuntimeException("Unexpected error: " + e.getMessage(), e);
        }
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
    //----------------------------------------------------SALARY SLIP---------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------------------------------------------------------

    public List<String> importSalarySlips(MultipartFile file, String sid, Map<String, String> refToNameMap, HttpSession session) {
        List<String> results = new ArrayList<>();
        logger.info("Processing Salary Slip CSV file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        String salaryComponent = "Salaire Base";
        try {
            if (!checkComponentExists(salaryComponent)) {
                results.add("Error: Salary Component '" + salaryComponent + "' does not exist in ERPNext.");
                logger.error("Salary Component '{}' does not exist", salaryComponent);
                return results;
            }
        } catch (Exception e) {
            results.add("Error checking Salary Component 'Salaire Base': " + e.getMessage());
            logger.error("Error checking Salary Component '{}': {}", salaryComponent, e.getMessage(), e);
            return results;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || !headerLine.trim().toLowerCase().startsWith("mois,ref employe,salaire base,salaire")) {
                results.add("Error: Invalid CSV header. Expected: Mois,Ref Employe,Salaire Base,Salaire");
                logger.error("CSV header error: Invalid header, expected 'Mois,Ref Employe,Salaire Base,Salaire'");
                return results;
            }
            logger.info("CSV header: {}", headerLine);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    if (line.trim().isEmpty()) {
                        results.add(String.format("Line %d: Skipped empty line", lineNumber));
                        logger.info("Line {}: Skipped empty line", lineNumber);
                        continue;
                    }

                    String[] fields = parseCsvLine(line);
                    if (fields.length < 4) {
                        results.add(String.format("Line %d: Invalid number of fields, expected 4, found %d", lineNumber, fields.length));
                        logger.error("Line {}: Invalid number of fields, expected 4, found {}", lineNumber, fields.length);
                        continue;
                    }

                    SalarySlip salarySlip = new SalarySlip();
                    salarySlip.setUtilService(utilService);
                    salarySlip.setMonth(fields[0].trim());
                    String ref = fields[1].trim();
                    String employeeId = refToNameMap.getOrDefault(ref, ref);
                    salarySlip.setEmployeeId(employeeId);
                    salarySlip.setBaseSalary(fields[2].trim());
                    salarySlip.setSalaryStructure(fields[3].trim());

                    logger.info("Line {}: Raw Salary Slip data - Month: {}, Employee Ref: {}, ERPNext ID: {}, Base Salary: {}, Salary Structure: {}",
                            lineNumber, salarySlip.getMonth(), ref, employeeId,
                            salarySlip.getBaseSalary(), salarySlip.getSalaryStructure());

                    salarySlip.validate();

                    if (!checkEmployeeExists(ref, session)) {
                        results.add(String.format("Line %d: Employee with Ref %s (ERPNext ID: %s) does not exist", lineNumber, ref, employeeId));
                        logger.error("Line {}: Employee with Ref {} (ERPNext ID: {}) does not exist", lineNumber, ref, employeeId);
                        continue;
                    }

                    if (!checkStructureExists(salarySlip.getSalaryStructure())) {
                        results.add(String.format("Line %d: Salary Structure %s does not exist", lineNumber, salarySlip.getSalaryStructure()));
                        logger.error("Line {}: Salary Structure {} does not exist", lineNumber, salarySlip.getSalaryStructure());
                        continue;
                    }

                    // Vérifier et créer une Salary Structure Assignment si nécessaire
                    String payrollDate = utilService.formatDate(utilService.getFormattedDate(salarySlip.getMonth()), "yyyy-MM-dd");
                    double baseSalary = salarySlip.getBaseSalary();
                    if (!checkSalaryStructureAssignmentExists(employeeId, salarySlip.getSalaryStructure(), payrollDate, baseSalary)) {
                        boolean assignmentCreated = createSalaryStructureAssignment(salarySlip, lineNumber, results);
                        if (!assignmentCreated) {
                            results.add(String.format("Line %d: Failed to create Salary Structure Assignment for employee %s (Ref: %s)", lineNumber, employeeId, ref));
                            logger.error("Line {}: Failed to create Salary Structure Assignment for employee {} (Ref: {})", lineNumber, employeeId, ref);
                            continue;
                        }
                    }

                    boolean slipExists = checkSalarySlipExists(employeeId, salarySlip.getMonth());
                    String slipName = utilService.generateSalarySlipName(employeeId, salarySlip.getMonth());
                    String endpoint = "resource/Salary Slip" + (slipExists ? "/" + slipName : "");
                    String url = baseUrl + endpoint;
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);

                    Map<String, Object> payload = salarySlip.toMap(slipExists);
                    logger.info("Payload for Salary Slip: {}", payload);
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

                    logger.info("Sending {} request to: {}", slipExists ? "PUT" : "POST", url);
                    ResponseEntity<Map> response = restTemplate.exchange(url, slipExists ? HttpMethod.PUT : HttpMethod.POST, entity, Map.class);

                    List<Cookie> cookies = cookieStore.getCookies();
                    logger.info("Cookies after request:");
                    for (Cookie cookie : cookies) {
                        logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
                    }

                    String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
                    logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        slipName = slipExists ? slipName : (String) ((Map) response.getBody().get("data")).get("name");
                        results.add(String.format("Line %d: Salary Slip for employee %s (Ref: %s) successfully %s", lineNumber, employeeId, ref, slipExists ? "updated" : "created"));
                        logger.info("Line {}: Salary Slip for employee {} (Ref: {}) successfully {}", lineNumber, employeeId, ref, slipExists ? "updated" : "created");

                        // Submit the Salary Slip
                        ResponseEntity<Map> submitResponse = submitResource("Salary Slip", slipName);
                        if (submitResponse.getStatusCode().is2xxSuccessful()) {
                            results.add(String.format("Line %d: Salary Slip %s successfully submitted", lineNumber, slipName));
                            logger.info("Line {}: Salary Slip {} successfully submitted", lineNumber, slipName);

                            // Trigger total salary calculation
                            Map<String, Object> updateData = new HashMap<>();
                            updateData.put("calculate_total_salary", 1);
                            ResponseEntity<Map> updateResponse = restTemplate.exchange(
                                    baseUrl + "resource/Salary Slip/" + slipName,
                                    HttpMethod.PUT,
                                    new HttpEntity<>(updateData, headers),
                                    Map.class
                            );
                            if (updateResponse.getStatusCode().is2xxSuccessful()) {
                                results.add(String.format("Line %d: Salary Slip %s total salary calculated", lineNumber, slipName));
                                logger.info("Line {}: Salary Slip {} total salary calculated", lineNumber, slipName);
                            } else {
                                String errorMsg = updateResponse.getBody() != null ? updateResponse.getBody().toString() : "Unknown error";
                                results.add(String.format("Line %d: Failed to calculate total salary for Salary Slip %s: %s", lineNumber, slipName, errorMsg));
                                logger.error("Line {}: Failed to calculate total salary for Salary Slip {}: {}", lineNumber, slipName, errorMsg);
                            }
                        } else {
                            String errorMsg = submitResponse.getBody() != null ? submitResponse.getBody().toString() : "Unknown error";
                            results.add(String.format("Line %d: Failed to submit Salary Slip %s: %s", lineNumber, slipName, errorMsg));
                            logger.error("Line {}: Failed to submit Salary Slip {}: {}", lineNumber, slipName, errorMsg);
                        }
                    } else {
                        String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                        results.add(String.format("Line %d: Failed to %s Salary Slip for employee %s (Ref: %s): %s", lineNumber, slipExists ? "update" : "create", employeeId, ref, errorMsg));
                        logger.error("Line {}: Failed to {} Salary Slip for employee {} (Ref: {}): {}", lineNumber, slipExists ? "update" : "create", employeeId, ref, errorMsg);
                    }
                } catch (IllegalArgumentException e) {
                    results.add(String.format("Line %d: Validation error: %s", lineNumber, e.getMessage()));
                    logger.error("Validation error on line {}: {}", lineNumber, e.getMessage());
                } catch (HttpClientErrorException e) {
                    String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                    results.add(String.format("Line %d: API error: %s", lineNumber, errorMsg));
                    logger.error("API error on line {}: {}", lineNumber, errorMsg);
                } catch (Exception e) {
                    results.add(String.format("Line %d: Error processing Salary Slip: %s", lineNumber, e.getMessage()));
                    logger.error("Error processing line {}: {}", lineNumber, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            results.add("Error importing Salary Slip CSV: " + e.getMessage());
            logger.error("Error importing Salary Slip CSV: {}", e.getMessage(), e);
            return results;
        }

        return results;
    }

    // Méthode pour parser une ligne CSV en gérant les guillemets
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
        // Ajouter le dernier champ
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    private boolean checkSalarySlipExists(String employeeId, String month) {
        try {
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Salary Slip";
            // Parse the month string to a Date
            Date parsedMonth = utilService.getFormattedDate(month);
            // Format the Date to a String in "yyyy-MM" format for ERPNext
            String formattedMonth = utilService.formatDate(parsedMonth, "yyyy-MM");
            String filters = String.format("[[\"employee\",\"=\",\"%s\"],[\"month\",\"=\",\"%s\"]]", employeeId, formattedMonth);
            String url = baseUrl + endpoint + "?fields=[\"name\"]&filters=" + filters;
            logger.info("Checking salary slip existence at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            List<Map<String, Object>> slipData = (List<Map<String, Object>>) response.getBody().get("data");
            logger.info("Salary Slip for employee {} and month {} exists: {}", employeeId, month, !slipData.isEmpty());
            return !slipData.isEmpty();
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error checking salary slip existence for employee {} and month {}: {} - {}", 
                    employeeId, month, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (RestClientException e) {
            logger.error("Failed to connect to ERPNext for salary slip existence check: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error checking salary slip existence for employee {} and month {}: {}", 
                    employeeId, month, e.getMessage());
            return false;
        }
    }

    private void fetchSalaryStructureComponents(String structureName, List<Map<String, Object>> earnings, List<Map<String, Object>> deductions) {
        try {
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Salary Structure/" + structureName;
            String url = baseUrl + endpoint + "?fields=[\"earnings\",\"deductions\"]";
            logger.info("Fetching salary structure components at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            List<Map<String, Object>> earningsData = (List<Map<String, Object>>) data.getOrDefault("earnings", new ArrayList<>());
            List<Map<String, Object>> deductionsData = (List<Map<String, Object>>) data.getOrDefault("deductions", new ArrayList<>());

            for (Map<String, Object> earning : earningsData) {
                Map<String, Object> component = new HashMap<>();
                component.put("salary_component", earning.get("salary_component"));
                component.put("salary_component_abbr", earning.getOrDefault("salary_component_abbr", ""));
                component.put("amount", 0.0);
                component.put("formula", earning.getOrDefault("formula", null));
                earnings.add(component);
            }

            for (Map<String, Object> deduction : deductionsData) {
                Map<String, Object> component = new HashMap<>();
                component.put("salary_component", deduction.get("salary_component"));
                component.put("salary_component_abbr", deduction.getOrDefault("salary_component_abbr", ""));
                component.put("amount", 0.0);
                component.put("formula", deduction.getOrDefault("formula", null));
                deductions.add(component);
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error fetching salary structure {} components: {}", structureName, e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching salary structure {} components: {}", structureName, e.getMessage());
            throw new RuntimeException("Failed to fetch salary structure components: " + e.getMessage(), e);
        }
    }

    private String getCompanyCurrency(String companyName) {
        try {
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Company/" + companyName;
            String url = baseUrl + endpoint + "?fields=[\"default_currency\"]";
            logger.info("Fetching company currency at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            return (String) data.getOrDefault("default_currency", "MAD");
        } catch (Exception e) {
            logger.error("Error fetching currency for company {}: {}", companyName, e.getMessage());
            return "MAD";
        }
    }

    private String getEmployeeCompany(String employeeId) {
        try {
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Employee";
            String filters = String.format("[[\"name\",\"=\",\"%s\"]]", employeeId);
            String url = baseUrl + endpoint + "?fields=[\"company\"]&filters=" + filters;
            logger.info("Fetching company for employee at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            List<Map<String, Object>> employeeData = (List<Map<String, Object>>) response.getBody().get("data");
            if (!employeeData.isEmpty()) {
                String company = (String) employeeData.get(0).get("company");
                logger.info("Fetched company {} for employee {}", company, employeeId);
                return company;
            }
            logger.warn("No company found for employee {}", employeeId);
            return null;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error fetching company for employee {}: {}", employeeId, e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            logger.error("Error fetching company for employee {}: {}", employeeId, e.getMessage());
            return null;
        }
    }

    private boolean checkSalaryStructureAssignmentExists(String employeeId, String salaryStructure, String payrollDate, double baseSalary) {
        try {
            erpNextService.checkSessionOrThrow();

            String endpoint = "resource/Salary Structure Assignment";
            String filters = String.format(
                "[[\"employee\",\"=\",\"%s\"],[\"salary_structure\",\"=\",\"%s\"],[\"from_date\",\"=\",\"%s\"],[\"base\",\"=\",\"%s\"]]",
                employeeId, salaryStructure, payrollDate, baseSalary
            );
            String url = baseUrl + endpoint + "?fields=[\"name\",\"base\"]&filters=" + filters;
            logger.info("Checking salary structure assignment existence at: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            List<Map<String, Object>> assignmentData = (List<Map<String, Object>>) response.getBody().get("data");
            boolean exists = !assignmentData.isEmpty();
            logger.info("Salary Structure Assignment for employee {}, structure {}, date {}, base {} exists: {}", 
                        employeeId, salaryStructure, payrollDate, baseSalary, exists);
            return exists;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Error checking Salary Structure Assignment for employee {}, structure {}, date {}, base {}: {}", 
                        employeeId, salaryStructure, payrollDate, baseSalary, e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            logger.error("Error checking Salary Structure Assignment for employee {}, structure {}, date {}, base {}: {}", 
                        employeeId, salaryStructure, payrollDate, baseSalary, e.getMessage());
            return false;
        }
    }

    private boolean createSalaryStructureAssignment(SalarySlip salarySlip, int lineNumber, List<String> results) {
        try {
            String employeeId = salarySlip.getEmployeeId();
            String payrollDate = utilService.formatDate(utilService.getFormattedDate(salarySlip.getMonth()), "yyyy-MM-dd");
            double baseSalary = salarySlip.getBaseSalary();

            // Vérifier si une affectation existe déjà pour la période exacte et le salaire de base
            if (checkSalaryStructureAssignmentExists(employeeId, salarySlip.getSalaryStructure(), payrollDate, baseSalary)) {
                results.add(String.format("Line %d: Salary Structure Assignment already exists for employee %s, period %s, base %s", 
                                        lineNumber, employeeId, payrollDate, baseSalary));
                logger.info("Line {}: Salary Structure Assignment already exists for employee {}, period {}, base {}", 
                            lineNumber, employeeId, payrollDate, baseSalary);
                return true;
            }

            Map<String, Object> assignmentPayload = new HashMap<>();
            assignmentPayload.put("doctype", "Salary Structure Assignment");
            assignmentPayload.put("employee", employeeId);
            assignmentPayload.put("salary_structure", salarySlip.getSalaryStructure());
            assignmentPayload.put("from_date", payrollDate);
            // Définir la date de fin (dernier jour du mois)
            String toDate = utilService.getEndOfMonth(salarySlip.getMonth(), "yyyy-MM-dd");
            assignmentPayload.put("to_date", toDate);
            assignmentPayload.put("base", baseSalary);

            String company = getEmployeeCompany(employeeId);
            if (company == null) {
                results.add(String.format("Line %d: Failed to fetch company for employee %s", lineNumber, employeeId));
                logger.error("Line {}: Failed to fetch company for employee {}", lineNumber, employeeId);
                return false;
            }
            assignmentPayload.put("company", company);

            logger.info("Line {}: Creating Salary Structure Assignment for employee {} with payload: {}", 
                        lineNumber, employeeId, assignmentPayload);

            String endpoint = "resource/Salary Structure Assignment";
            String url = baseUrl + endpoint;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(assignmentPayload, headers);

            logger.info("Sending POST request to: {}", url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            List<Cookie> cookies = cookieStore.getCookies();
            logger.info("Cookies after request:");
            for (Cookie cookie : cookies) {
                logger.info("Cookie: {}={} ; Domain={} ; Path={}", cookie.getName(), cookie.getValue(), cookie.getDomain(), cookie.getPath());
            }

            String rawResponse = response.getBody() != null ? response.getBody().toString() : "null";
            logger.info("Raw response from /api/{}: {}", endpoint, rawResponse);

            if (response.getStatusCode().is2xxSuccessful()) {
                String assignmentName = (String) ((Map) response.getBody().get("data")).get("name");
                results.add(String.format("Line %d: Salary Structure Assignment %s created for employee %s", 
                                        lineNumber, assignmentName, employeeId));
                logger.info("Line {}: Salary Structure Assignment {} created for employee {}", 
                            lineNumber, assignmentName, employeeId);

                ResponseEntity<Map> submitResponse = submitResource("Salary Structure Assignment", assignmentName);
                if (submitResponse.getStatusCode().is2xxSuccessful()) {
                    results.add(String.format("Line %d: Salary Structure Assignment %s submitted for employee %s", 
                                            lineNumber, assignmentName, employeeId));
                    logger.info("Line {}: Salary Structure Assignment {} submitted for employee {}", 
                                lineNumber, assignmentName, employeeId);
                    return true;
                } else {
                    //String errorMsg = submitResponse.getError().getBody() != null ? submitResponse.getBody().toString() : "Unknown error";
                    // results.add(String.format("Line {}: Failed to submit Salary Structure Assignment %s for employee %s: %s", 
                    //                         lineNumber, assignmentName, employeeId, errorMsg));
                    // logger.error("Line {}: Failed to submit Salary Structure Assignment {} for employee {}: {}", 
                    //             lineNumber, assignmentName, employeeId, errorMsg);
                    return false;
                }
            } else {
                String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                results.add(String.format("Line %d: Failed to create Salary Structure Assignment for employee %s: %s", 
                                        lineNumber, employeeId, errorMsg));
                logger.error("Line {}: Failed to create Salary Structure Assignment for employee {}: {}", 
                            lineNumber, employeeId, errorMsg);
                return false;
            }
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
            results.add(String.format("Line %d: Error creating/submitting Salary Structure Assignment for employee %s: %s", 
                                    lineNumber, salarySlip.getEmployeeId(), errorMsg));
            logger.error("Line {}: Error creating/submitting Salary Structure Assignment for employee {}: {}", 
                        lineNumber, salarySlip.getEmployeeId(), errorMsg, e);
            return false;
        } catch (Exception e) {
            results.add(String.format("Line %d: Error creating/submitting Salary Structure Assignment for employee %s: %s", 
                                    lineNumber, salarySlip.getEmployeeId(), e.getMessage()));
            logger.error("Line {}: Error creating/submitting Salary Structure Assignment for employee {}: {}", 
                        lineNumber, salarySlip.getEmployeeId(), e.getMessage(), e);
            return false;
        }
    }
}