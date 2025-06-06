package com.Eval.NewApp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class ResetDataService {

    private static final Logger logger = LoggerFactory.getLogger(ResetDataService.class);

    @Autowired
    private ERPNextService erpNextService;

    
    @Autowired
    private RestTemplate restTemplate;

    @Value("${erpnext.base-url}")
    private String baseUrl;

    private static final List<String> PROTECTED_SALARY_COMPONENTS = Arrays.asList(
        "Leave Encashment", "Arrear", "Basic", "Income Tax"
    );

    public List<String> resetData() throws RuntimeException {
        logger.info("Starting data reset process");
        List<String> results = new ArrayList<>();

        // Validate session
        erpNextService.checkSessionOrThrow();
        String sid = erpNextService.getSessionId();
        if (sid == null) {
            throw new RuntimeException("No session ID found");
        }

        // Define doctypes in dependency-aware order
        String[] doctypes = {
            "Salary Slip",
            "Salary Structure Assignment",
            "Salary Structure",
            "Employee",
            "Salary Component"
        };

        for (String doctype : doctypes) {
            try {
                // Fetch all records for the doctype
                String endpoint = String.format("resource/%s?fields=[\"name\",\"docstatus\"]", doctype);
                String url = baseUrl + endpoint;
                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "application/json");
                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class);

                List<Map<String, Object>> records = response.getBody() != null && response.getBody().containsKey("data")
                    ? (List<Map<String, Object>>) response.getBody().get("data")
                    : new ArrayList<>();

                if (records.isEmpty()) {
                    results.add(String.format("No records found in %s to delete", doctype));
                    logger.info("No records found in {} to delete", doctype);
                    continue;
                }

                // Process each record
                for (Map<String, Object> record : records) {
                    String recordName = (String) record.get("name");
                    Integer docstatus = (Integer) record.get("docstatus");

                    // Skip protected salary components
                    // if (doctype.equals("Salary Component") && PROTECTED_SALARY_COMPONENTS.contains(recordName)) {
                    //     results.add(String.format("Skipped protected Salary Component %s", recordName));
                    //     logger.info("Skipped protected Salary Component {}", recordName);
                    //     continue;
                    // }

                    try {
                        // Handle documents based on docstatus
                        if (doctype.equals("Salary Slip") || doctype.equals("Salary Structure") || doctype.equals("Salary Structure Assignment")) {
                            if (docstatus == 0) {
                                // Draft document: delete directly
                                logger.info("{} {} is in Draft (docstatus: 0), deleting directly", doctype, recordName);
                            } else if (docstatus == 1) {
                                // Submitted document: cancel first
                                try {
                                    String cancelEndpoint = String.format("resource/%s/%s?run_method=cancel", 
                                        doctype, recordName);
                                    String cancelUrl = baseUrl + cancelEndpoint;
                                    HttpHeaders cancelHeaders = new HttpHeaders();
                                    cancelHeaders.set("Accept", "application/json");
                                    HttpEntity<String> cancelEntity = new HttpEntity<>(cancelHeaders);
                                    ResponseEntity<Map> cancelResponse = restTemplate.exchange(
                                        cancelUrl, HttpMethod.POST, cancelEntity, Map.class);

                                    if (cancelResponse.getStatusCode().is2xxSuccessful()) {
                                        results.add(String.format("%s %s successfully canceled", doctype, recordName));
                                        logger.info("{} {} successfully canceled", doctype, recordName);
                                    } else {
                                        String errorMsg = cancelResponse.getBody() != null ? cancelResponse.getBody().toString() : "Unknown error";
                                        results.add(String.format("Failed to cancel %s %s: %s", doctype, recordName, errorMsg));
                                        logger.error("Failed to cancel {} {}: {}", doctype, recordName, errorMsg);
                                        continue;
                                    }
                                } catch (HttpClientErrorException e) {
                                    String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                                    results.add(String.format("Error canceling %s %s: %s", doctype, recordName, errorMsg));
                                    logger.error("Error canceling {} {}: {}", doctype, recordName, errorMsg);
                                    continue;
                                }
                            } else if (docstatus == 2) {
                                // Already cancelled: proceed to deletion
                                logger.info("{} {} is already cancelled (docstatus: 2)", doctype, recordName);
                            }
                        }

                        // Delete the record
                        try {
                            String deleteEndpoint = String.format("resource/%s/%s", doctype, recordName);
                            String deleteUrl = baseUrl + deleteEndpoint;
                            HttpHeaders deleteHeaders = new HttpHeaders();
                            deleteHeaders.set("Accept", "application/json");
                            HttpEntity<String> deleteEntity = new HttpEntity<>(deleteHeaders);
                            ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                                deleteUrl, HttpMethod.DELETE, deleteEntity, Map.class);

                            if (deleteResponse.getStatusCode().is2xxSuccessful()) {
                                results.add(String.format("%s %s successfully deleted", doctype, recordName));
                                logger.info("{} {} successfully deleted", doctype, recordName);
                            } else {
                                String errorMsg = deleteResponse.getBody() != null ? deleteResponse.getBody().toString() : "Unknown error";
                                results.add(String.format("Failed to delete %s %s: %s", doctype, recordName, errorMsg));
                                logger.error("Failed to delete {} {}: {}", doctype, recordName, errorMsg);
                            }
                        } catch (HttpClientErrorException e) {
                            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                            results.add(String.format("Error deleting %s %s: %s", doctype, recordName, errorMsg));
                            logger.error("Error deleting {} {}: {}", doctype, recordName, errorMsg);
                        }
                    } catch (Exception e) {
                        results.add(String.format("Error processing %s %s: %s", doctype, recordName, e.getMessage()));
                        logger.error("Error processing {} {}: {}", doctype, recordName, e.getMessage(), e);
                    }
                }
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                results.add(String.format("Error fetching records for %s: %s", doctype, errorMsg));
                logger.error("Error fetching records for {}: {}", doctype, errorMsg);
            } catch (RestClientException e) {
                results.add(String.format("Failed to connect to ERPNext for %s: %s", doctype, e.getMessage()));
                logger.error("Failed to connect to ERPNext for {}: {}", doctype, e.getMessage());
            } catch (Exception e) {
                results.add(String.format("Unexpected error fetching records for %s: %s", doctype, e.getMessage()));
                logger.error("Unexpected error fetching records for {}: {}", doctype, e.getMessage(), e);
            }
        }

        logger.info("Data reset completed: {} results", results.size());
        return results;
    }
}