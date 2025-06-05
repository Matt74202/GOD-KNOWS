package com.Eval.NewApp.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.Eval.NewApp.service.UtilService;

public class SalaryImport {
    private static final Logger logger = LoggerFactory.getLogger(SalaryImport.class);

    @Autowired
    private UtilService utilService;

    private String salaryStructure;
    private String name;
    private String salary_component_abbr; 
    private String type;
    private String valeur;
    private String company;

    public SalaryImport() {
    }

    public void validate() {
        if (salaryStructure == null || salaryStructure.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom de la structure salariale est requis.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du composant est requis.");
        }
        if (salary_component_abbr == null || salary_component_abbr.trim().isEmpty()) { 
            throw new IllegalArgumentException("L'abréviation du composant est requise.");
        }
        if (type == null || (!type.equalsIgnoreCase("earning") && !type.equalsIgnoreCase("deduction"))) {
            throw new IllegalArgumentException("Le type doit être 'earning' ou 'deduction'.");
        }
        if (valeur == null || valeur.trim().isEmpty()) {
            throw new IllegalArgumentException("La valeur ou formule est requise.");
        }
        if (company == null || company.trim().isEmpty()) {
            throw new IllegalArgumentException("La société est requise.");
        }
    }

    // Getters et Setters
    public String getSalaryStructure() {
        return salaryStructure;
    }

    public void setSalaryStructure(String salaryStructure) {
        this.salaryStructure = salaryStructure;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSalaryComponentAbbr() { // Changement ici
        return salary_component_abbr;
    }

    public void setSalaryComponentAbbr(String salary_component_abbr) { // Changement ici
        this.salary_component_abbr = salary_component_abbr;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValeur() {
        return valeur;
    }

    public void setValeur(String valeur) {
        this.valeur = valeur;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        return name.replaceAll("\\s+", "-")
                   .replaceAll("[éèêë]", "e")
                   .replaceAll("[àáâãäå]", "a")
                   .replaceAll("[îï]", "i")
                   .replaceAll("[ôö]", "o")
                   .replaceAll("[ùúûü]", "u");
    }

    public Map<String, Object> toMap(boolean isUpdate) {
        Map<String, Object> map = new HashMap<>();
        if (isUpdate) {
            map.put("name", utilService.normalizeName(name));
        }
        map.put("salary_component", name);
        map.put("salary_component_abbr", salary_component_abbr);
        map.put("type", type.equalsIgnoreCase("earning") ? "Earning" : "Deduction");
        map.put("company", company);
        map.put("is_tax_applicable", type.equalsIgnoreCase("earning"));
        map.put("amount_based_on_formula", true);
        map.put("condition", "1 == 1"); // Condition par défaut
        map.put("formula", valeur.equalsIgnoreCase("base") ? "base" : valeur);
        //map.put("depends_on_payment_days", 0);
        map.put("do_not_include_in_total", 0);
        map.put("is_flexible_benefit", 0);
        List<Map<String, String>> accounts = new ArrayList<>();
        Map<String, String> account = new HashMap<>();
        account.put("company", company);
        account.put("account", "Main Cash - AD - AD" + company.substring(0, Math.min(3, company.length())).toUpperCase());
        accounts.add(account);
        map.put("accounts", accounts);
        return map;
    }
}
