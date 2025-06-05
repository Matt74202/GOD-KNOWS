package com.Eval.NewApp.service;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UtilService {

    private static final Logger logger = LoggerFactory.getLogger(UtilService.class);

    private static final List<String> DATE_FORMATS = Arrays.asList(
        "dd-MM-yyyy",
        "dd/MM/yyyy",
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "MM-dd-yyyy",
        "MM/dd/yyyy",
        "dd MMM yyyy",
        "dd MMMM yyyy",
        "yyyyMMdd",
        "ddMMyyyy"
    );

    public Date getFormattedDate(String dateStr) throws IllegalArgumentException {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            logger.error("Date string is null or empty");
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }

        String trimmedDate = dateStr.trim();
        for (String pattern : DATE_FORMATS) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
                dateFormat.setLenient(false); // Strict parsing
                Date parsedDate = dateFormat.parse(trimmedDate);
                logger.debug("Successfully parsed date '{}' with pattern '{}'", trimmedDate, pattern);
                return parsedDate;
            } catch (Exception e) {
                logger.debug("Failed to parse date '{}' with pattern '{}': {}", trimmedDate, pattern, e.getMessage());
            }
        }

        logger.error("Unable to parse date '{}' with any supported pattern", trimmedDate);
        throw new IllegalArgumentException("Invalid date format: " + trimmedDate + ". Supported formats: " + DATE_FORMATS);
    }

    public String formatDate(Date date, String outputPattern) throws IllegalArgumentException {
        if (date == null) {
            logger.error("Date object is null");
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (outputPattern == null || outputPattern.trim().isEmpty()) {
            logger.error("Output pattern is null or empty");
            throw new IllegalArgumentException("Output pattern cannot be null or empty");
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(outputPattern);
            String formattedDate = dateFormat.format(date);
            logger.debug("Formatted date '{}' to pattern '{}': {}", date, outputPattern, formattedDate);
            return formattedDate;
        } catch (Exception e) {
            logger.error("Error formatting date '{}' with pattern '{}': {}", date, outputPattern, e.getMessage());
            throw new IllegalArgumentException("Invalid output pattern: " + outputPattern);
        }
    }
}
