package com.Eval.NewApp.service;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

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

    public String normalizeName(String name) {
        if (name == null) return null;
        return name.replaceAll("\\s+", "-")
                .replaceAll("[éèêë]", "e")
                .replaceAll("[àáâãäå]", "a")
                .replaceAll("[îï]", "i")
                .replaceAll("[ôö]", "o")
                .replaceAll("[ùúûü]", "u");
    }

    public boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) {
            logger.error("Input string is null or empty");
            return false;
        }
        try {
            Double.parseDouble(str.trim());
            return true;
        } catch (NumberFormatException e) {
            logger.error("Invalid numeric format: {}", str);
            return false;
        }
    }

    public String getEndOfMonth(String dateStr, String inputPattern) throws IllegalArgumentException {
        try {
            Date date = getFormattedDate(dateStr);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
            return formatDate(calendar.getTime(), "yyyy-MM-dd");
        } catch (Exception e) {
            logger.error("Error calculating end of month for date '{}': {}", dateStr, e.getMessage());
            throw new IllegalArgumentException("Error calculating end of month: " + e.getMessage());
        }
    }

    public String generateSalarySlipName(String employeeId, String month) {
        try {
            Date date = getFormattedDate(month);
            String formattedMonth = formatDate(date, "yyyy-MM");
            return String.format("%s-%s", normalizeName(employeeId), formattedMonth);
        } catch (Exception e) {
            logger.error("Error generating Salary Slip name for employee {} and month {}: {}", employeeId, month, e.getMessage());
            throw new IllegalArgumentException("Error generating Salary Slip name: " + e.getMessage());
        }
    }

    // public double evaluateFormula(String formula, Map<String, Double> componentValues) {
    //     if (formula == null || formula.trim().isEmpty() || formula.equalsIgnoreCase("base")) {
    //         return componentValues.getOrDefault("base", 0.0);
    //     }

    //     try {
    //         ScriptEngineManager manager = new ScriptEngineManager();
    //         ScriptEngine engine = manager.getEngineByName("js"); // Utiliser "js" pour Graal.js
    //         if (engine == null) {
    //             logger.error("No JavaScript engine found. Please ensure a compatible JS engine (e.g., Graal.js) is included.");
    //             throw new IllegalStateException("No JavaScript engine available for formula evaluation.");
    //         }

    //         // Remplacer les abréviations par leurs valeurs
    //         String evaluatedFormula = formula;
    //         for (Map.Entry<String, Double> entry : componentValues.entrySet()) {
    //             evaluatedFormula = evaluatedFormula.replace(entry.getKey(), entry.getValue().toString());
    //         }

    //         logger.debug("Evaluating formula: {}", evaluatedFormula);

    //         // Évaluer l'expression
    //         Object result = engine.eval(evaluatedFormula);
    //         if (result instanceof Number) {
    //             return ((Number) result).doubleValue();
    //         } else {
    //             throw new IllegalArgumentException("Formula evaluation did not return a number: " + evaluatedFormula);
    //         }
    //     } catch (ScriptException e) {
    //         logger.error("Error evaluating formula '{}': {}", formula, e.getMessage());
    //         throw new IllegalArgumentException("Failed to evaluate formula: " + formula, e);
    //     } catch (Exception e) {
    //         logger.error("Unexpected error evaluating formula '{}': {}", formula, e.getMessage());
    //         throw new IllegalStateException("Unexpected error during formula evaluation: " + formula, e);
    //     }
    // }
}
