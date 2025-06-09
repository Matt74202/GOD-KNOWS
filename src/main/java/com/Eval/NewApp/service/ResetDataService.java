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

import jakarta.servlet.http.HttpSession;

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

    @Autowired
    private HttpSession session;

    @Value("${erpnext.base-url}")
    private String baseUrl;

    private static final List<String> PROTECTED_SALARY_COMPONENTS = Arrays.asList(
        "Leave Encashment", "Arrear", "Basic", "Income Tax"
    );

    // Méthode pour tenter une suppression avec réessais
    private ResponseEntity<Map> attemptDelete(String doctype, String recordName, List<String> results) {
        int maxAttempts = 3;
        int attempt = 1;
        while (attempt <= maxAttempts) {
            try {
                String deleteEndpoint = String.format("resource/%s/%s", doctype, recordName);
                String deleteUrl = baseUrl + deleteEndpoint;
                HttpHeaders deleteHeaders = new HttpHeaders();
                deleteHeaders.set("Accept", "application/json");
                HttpEntity<String> deleteEntity = new HttpEntity<>(deleteHeaders);
                ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                    deleteUrl, HttpMethod.DELETE, deleteEntity, Map.class);

                return deleteResponse;
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                if (attempt == maxAttempts) {
                    results.add(String.format("Error deleting %s %s after %d attempts: %s", doctype, recordName, maxAttempts, errorMsg));
                    logger.error("Error deleting {} {} after {} attempts: {}", doctype, recordName, maxAttempts, errorMsg);
                    return null;
                }
                logger.warn("Attempt {}/{} failed for deleting {} {}: {}. Retrying...", attempt, maxAttempts, doctype, recordName, errorMsg);
                attempt++;
                try {
                    Thread.sleep(1000); // Délai de 1 seconde entre les tentatives
                } catch (InterruptedException ie) {
                    logger.error("Interrupted during retry delay: {}", ie.getMessage());
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (Exception e) {
                results.add(String.format("Unexpected error deleting %s %s: %s", doctype, recordName, e.getMessage()));
                logger.error("Unexpected error deleting {} {}: {}", doctype, recordName, e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    // Méthode pour tenter une annulation avec réessais
    private ResponseEntity<Map> attemptCancel(String doctype, String recordName, List<String> results) {
        int maxAttempts = 3;
        int attempt = 1;
        while (attempt <= maxAttempts) {
            try {
                String cancelEndpoint = String.format("resource/%s/%s?run_method=cancel", doctype, recordName);
                String cancelUrl = baseUrl + cancelEndpoint;
                HttpHeaders cancelHeaders = new HttpHeaders();
                cancelHeaders.set("Accept", "application/json");
                HttpEntity<String> cancelEntity = new HttpEntity<>(cancelHeaders);
                ResponseEntity<Map> cancelResponse = restTemplate.exchange(
                    cancelUrl, HttpMethod.POST, cancelEntity, Map.class);

                return cancelResponse;
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                if (attempt == maxAttempts) {
                    results.add(String.format("Error canceling %s %s after %d attempts: %s", doctype, recordName, maxAttempts, errorMsg));
                    logger.error("Error canceling {} {} after {} attempts: {}", doctype, recordName, maxAttempts, errorMsg);
                    return null;
                }
                logger.warn("Attempt {}/{} failed for canceling {} {}: {}. Retrying...", attempt, maxAttempts, doctype, recordName, errorMsg);
                attempt++;
                try {
                    Thread.sleep(1000); // Délai de 1 seconde entre les tentatives
                } catch (InterruptedException ie) {
                    logger.error("Interrupted during retry delay: {}", ie.getMessage());
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (Exception e) {
                results.add(String.format("Unexpected error canceling %s %s: %s", doctype, recordName, e.getMessage()));
                logger.error("Unexpected error canceling {} {}: {}", doctype, recordName, e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    public List<String> resetData() throws RuntimeException {
        logger.info("Starting data reset process");
        List<String> results = new ArrayList<>();

        // Nettoyage de la session
        session.removeAttribute("employeeRefToNameMap");
        logger.info("Cleared employeeRefToNameMap from session");

        // Validate session
        erpNextService.checkSessionOrThrow();

        // Define doctypes in dependency-aware order
        String[] doctypes = {
            "Payroll Entry",
            "Salary Slip",
            "Salary Structure Assignment",
            "Salary Structure",
            "Employee",
            "Salary Component"
        };

        // Étape 1 : Suppression des enregistrements par lots
        for (String doctype : doctypes) {
            int pageSize = 100;
            int start = 0;
            boolean hasMore = true;

            while (hasMore) {
                try {
                    String fields = "[\"name\", \"docstatus\"]";
                    String filters = "";
                    String endpoint = String.format("resource/%s?fields=%s&limit_page_length=%d&limit_start=%d", 
                        doctype, fields, pageSize, start);
                    String url = baseUrl + endpoint;
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Accept", "application/json");
                    HttpEntity<String> entity = new HttpEntity<>(headers);
                    ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

                    List<Map<String, Object>> records = response.getBody() != null && response.getBody().containsKey("data")
                        ? (List<Map<String, Object>>) response.getBody().get("data")
                        : new ArrayList<>();

                    if (records.isEmpty()) {
                        results.add(String.format("No records found in %s to delete successfully", doctype));
                        logger.info("No records found in {} to delete", doctype);
                        hasMore = false;
                        continue;
                    }

                    // Process each record
                    for (Map<String, Object> record : records) {
                        String recordName = (String) record.get("name");
                        Integer docstatus = (Integer) record.get("docstatus");

                        // // Skip protected salary components
                        // if (doctype.equals("Salary Component") && PROTECTED_SALARY_COMPONENTS.contains(recordName)) {
                        //     results.add(String.format("Skipped protected Salary Component %s", recordName));
                        //     logger.info("Skipped protected Salary Component {}", recordName);
                        //     continue;
                        // }

                        try {
                            // Handle documents based on docstatus
                            if (doctype.equals("Salary Slip") || doctype.equals("Salary Structure") || 
                                doctype.equals("Salary Structure Assignment") || doctype.equals("Payroll Entry")) {
                                if (docstatus == 0) {
                                    // Draft document: delete directly
                                    logger.info("{} {} is in Draft (docstatus: 0), deleting directly", doctype, recordName);
                                } else if (docstatus == 1) {
                                    // Submitted document: cancel first
                                    ResponseEntity<Map> cancelResponse = attemptCancel(doctype, recordName, results);
                                    if (cancelResponse == null || !cancelResponse.getStatusCode().is2xxSuccessful()) {
                                        if (cancelResponse != null) {
                                            String errorMsg = cancelResponse.getBody() != null ? cancelResponse.getBody().toString() : "Unknown error";
                                            results.add(String.format("Failed to cancel %s %s: %s", doctype, recordName, errorMsg));
                                            logger.error("Failed to cancel {} {}: {}", doctype, recordName, errorMsg);
                                        }
                                        continue; // Passer à l'enregistrement suivant
                                    }
                                    results.add(String.format("%s %s successfully canceled", doctype, recordName));
                                    logger.info("{} {} successfully canceled", doctype, recordName);
                                } else if (docstatus == 2) {
                                    // Already cancelled
                                    logger.info("{} {} is already cancelled (docstatus: 2)", doctype, recordName);
                                }
                            }

                            // Delete the record
                            ResponseEntity<Map> deleteResponse = attemptDelete(doctype, recordName, results);
                            if (deleteResponse != null && deleteResponse.getStatusCode().is2xxSuccessful()) {
                                results.add(String.format("%s %s successfully deleted", doctype, recordName));
                                logger.info("{} {} successfully deleted", doctype, recordName);
                            } else if (deleteResponse != null) {
                                String errorMsg = deleteResponse.getBody() != null ? deleteResponse.getBody().toString() : "Unknown error";
                                results.add(String.format("Failed to delete %s %s: %s", doctype, recordName, errorMsg));
                                logger.error("Failed to delete {} {}: {}", doctype, recordName, errorMsg);
                            }
                        } catch (Exception e) {
                            results.add(String.format("Error processing %s %s: %s", doctype, recordName, e.getMessage()));
                            logger.error("Error processing {} {}: {}", doctype, recordName, e.getMessage(), e);
                        }
                    }

                    start += pageSize;
                    hasMore = records.size() == pageSize;
                } catch (HttpClientErrorException e) {
                    String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                    if (e.getStatusCode().value() == 403) {
                        results.add(String.format("Permission denied for %s: %s", doctype, errorMsg));
                        logger.error("Permission denied for {}: {}", doctype, errorMsg);
                        break; // Arrêter pour ce doctype si permission refusée
                    }
                    results.add(String.format("Error fetching records for %s: %s", doctype, errorMsg));
                    logger.error("Error fetching records for {}: {}", doctype, errorMsg);
                    hasMore = false;
                } catch (RestClientException e) {
                    results.add(String.format("Failed to connect to ERPNext for %s: %s", doctype, e.getMessage()));
                    logger.error("Failed to connect to ERPNext for {}: {}", doctype, e.getMessage());
                    hasMore = false;
                } catch (Exception e) {
                    results.add(String.format("Unexpected error fetching records for %s: %s", doctype, e.getMessage()));
                    logger.error("Unexpected error fetching records for {}: {}", doctype, e.getMessage(), e);
                    hasMore = false;
                }
            }
        }

        // Étape 2 : Vérification des enregistrements restants
        for (String doctype : doctypes) {
            try {
                String endpoint = String.format("resource/%s?fields=[\"name\"]", doctype);
                String url = baseUrl + endpoint;
                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "application/json");
                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

                List<Map<String, Object>> remainingRecords = response.getBody() != null && response.getBody().containsKey("data")
                    ? (List<Map<String, Object>>) response.getBody().get("data")
                    : new ArrayList<>();

                if (!remainingRecords.isEmpty()) {
                    results.add(String.format("Warning: %d records remain in %s after deletion", remainingRecords.size(), doctype));
                    logger.warn("{} records remain in {} after deletion", remainingRecords.size(), doctype);
                }
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                results.add(String.format("Error verifying remaining records for %s: %s", doctype, errorMsg));
                logger.error("Error verifying remaining records for {}: {}", doctype, errorMsg);
            } catch (RestClientException e) {
                results.add(String.format("Failed to connect to ERPNext for verifying %s: %s", doctype, e.getMessage()));
                logger.error("Failed to connect to ERPNext for verifying {}: {}", doctype, e.getMessage());
            } catch (Exception e) {
                results.add(String.format("Unexpected error verifying remaining records for %s: %s", doctype, e.getMessage()));
                logger.error("Unexpected error verifying remaining records for {}: {}", doctype, e.getMessage());
            }
        }

        logger.info("Data reset completed: {} results", results.size());
        return results;
    }
}