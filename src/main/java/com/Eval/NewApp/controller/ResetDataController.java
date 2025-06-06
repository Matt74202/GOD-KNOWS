package com.Eval.NewApp.controller;

import com.Eval.NewApp.service.ResetDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

@Controller
public class ResetDataController {

    private static final Logger logger = LoggerFactory.getLogger(ResetDataController.class);

    @Autowired
    private ResetDataService resetDataService;

    @Autowired
    private HttpSession session;

    @PostMapping("/reset-data")
    public String resetData(Model model) {
        List<String> resetResults = new ArrayList<>();

        try {
            String sid = (String) session.getAttribute("sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please login again.");
                return "employee_list";
            }

            logger.info("Initiating data reset");
            resetResults = resetDataService.resetData();
            model.addAttribute("resetResults", resetResults);
            model.addAttribute("message", resetResults.isEmpty() ? "Data reset completed successfully" :
                "Data reset completed with " + (resetResults.stream().anyMatch(r -> r.startsWith("Failed") || r.startsWith("Error")) ? "issues" : "success"));
            logger.info("Data reset completed: {} results", resetResults.size());

        } catch (RuntimeException e) {
            logger.error("Error during data reset: {}", e.getMessage());
            model.addAttribute("error", "Error during data reset: " + e.getMessage());
        }

        return "employee_list";
    }
}