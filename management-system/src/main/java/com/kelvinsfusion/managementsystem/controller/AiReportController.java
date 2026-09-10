package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.Expense;
import com.kelvinsfusion.managementsystem.model.Sale;
import com.kelvinsfusion.managementsystem.repository.ExpenseRepository;
import com.kelvinsfusion.managementsystem.repository.SaleRepository;
import com.kelvinsfusion.managementsystem.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class AiReportController {

    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private GeminiService geminiService;

    @GetMapping("/ai-report")
    public String getAiReport(@RequestParam(value = "period", required = false) String period,
                              @RequestParam(required = false) String specificMonth,
                              @RequestParam(required = false) String specificYear,
                              Model model, Principal principal) {

        // Default to "month" if the user simply navigates to the page without clicking any filters
        if (period == null && specificMonth == null && specificYear == null) {
            period = "month";
        }

        // 1. FETCH ALL RAW DATA ONCE
        List<Sale> rawSales = saleRepository.findAll();
        List<Expense> rawExpenses = expenseRepository.findAll();

        List<Sale> filteredSales = new ArrayList<>();
        List<Expense> filteredExpenses = new ArrayList<>();
        String timeFrameLabel = "All Time";

        // 2. UNIFIED FILTERING LOGIC
        if (specificMonth != null && !specificMonth.isEmpty()) {
            timeFrameLabel = "Month: " + specificMonth;
            filteredSales = rawSales.stream().filter(s -> s.getSaleDateTime() != null &&
                    String.format("%04d-%02d", s.getSaleDateTime().getYear(), s.getSaleDateTime().getMonthValue()).equals(specificMonth)
            ).collect(Collectors.toList());

            filteredExpenses = rawExpenses.stream().filter(e -> e.getDate() != null &&
                    String.format("%04d-%02d", e.getDate().getYear(), e.getDate().getMonthValue()).equals(specificMonth)
            ).collect(Collectors.toList());

        } else if (specificYear != null && !specificYear.isEmpty()) {
            timeFrameLabel = "Year: " + specificYear;
            int year = Integer.parseInt(specificYear);

            filteredSales = rawSales.stream().filter(s -> s.getSaleDateTime() != null && s.getSaleDateTime().getYear() == year).collect(Collectors.toList());
            filteredExpenses = rawExpenses.stream().filter(e -> e.getDate() != null && e.getDate().getYear() == year).collect(Collectors.toList());

        } else if (period != null) {
            LocalDate now = LocalDate.now();
            LocalDate start = now.minusYears(100);
            LocalDate end = now;

            if ("today".equals(period)) {
                start = now;
                timeFrameLabel = "Today (" + now.toString() + ")";
            } else if ("week".equals(period)) {
                start = now.minusDays(now.getDayOfWeek().getValue() - 1);
                timeFrameLabel = "This Week";
            } else if ("month".equals(period)) {
                start = now.withDayOfMonth(1);
                timeFrameLabel = "This Month (" + Month.of(now.getMonthValue()).name() + ")";
            } else if ("year".equals(period)) {
                start = now.withDayOfYear(1);
                timeFrameLabel = "This Year (" + now.getYear() + ")";
            }

            LocalDate finalStart = start;
            LocalDate finalEnd = end;

            filteredSales = rawSales.stream().filter(s -> s.getSaleDateTime() != null &&
                    !s.getSaleDateTime().toLocalDate().isBefore(finalStart) && !s.getSaleDateTime().toLocalDate().isAfter(finalEnd)
            ).collect(Collectors.toList());

            filteredExpenses = rawExpenses.stream().filter(e -> e.getDate() != null &&
                    !e.getDate().isBefore(finalStart) && !e.getDate().isAfter(finalEnd)
            ).collect(Collectors.toList());
        }

        // 3. CALCULATE KPIs
        double totalSales = filteredSales.stream().mapToDouble(Sale::getTotalAmount).sum();
        double totalExpenses = filteredExpenses.stream().mapToDouble(Expense::getAmount).sum();
        double netProfit = totalSales - totalExpenses;
        double profitMargin = (totalSales > 0) ? (netProfit / totalSales) * 100 : 0.0;

        // 4. BUILD GRAPH DATA
        // Sort sales chronologically so the graph draws left-to-right correctly
        filteredSales.sort(Comparator.comparing(Sale::getSaleDateTime));
        Map<String, Double> trendMap = new LinkedHashMap<>();

        for (Sale s : filteredSales) {
            LocalDateTime time = s.getSaleDateTime();
            String key;
            // Group by Day for shorter periods, or by Month for longer periods
            if (specificMonth != null || "today".equals(period) || "week".equals(period) || "month".equals(period)) {
                key = String.format("%02d", time.getDayOfMonth()) + " " + time.getMonth().name().substring(0,3);
            } else {
                key = time.getMonth().name().substring(0,3) + " " + time.getYear();
            }
            trendMap.put(key, trendMap.getOrDefault(key, 0.0) + s.getTotalAmount());
        }

        // 5. BUILD THE PROMPT (Tuned for fresh juice & raw ingredients)
        String prompt = String.format(
                "You are an expert financial analyst for a fresh fruit juice business. " +
                        "Analyze this data for the period: %s.\n\n" +
                        "Total Revenue from mixed juice: KES %.2f\n" +
                        "Total Raw Ingredient Expenses (Fruit, Tamarind, Sugar, etc.): KES %.2f\n\n" +
                        "Provide a brief, professional report. Calculate the profit margin, assess if the raw ingredient spending is efficient compared to the revenue, and give one actionable tip to improve profitability. Do not use markdown symbols like asterisks.",
                timeFrameLabel, totalSales, totalExpenses
        );

        // 6. GET AI RESPONSE
        try {
            String aiResponse = geminiService.generateText(prompt);
            model.addAttribute("aiResponse", aiResponse);
        } catch (Exception e) {
            model.addAttribute("aiResponse", "Error connecting to AI: " + e.getMessage());
        }

        // 7. SEND EVERYTHING TO HTML
        model.addAttribute("currentPeriod", period);
        model.addAttribute("timeFrameLabel", timeFrameLabel);
        model.addAttribute("totalSales", totalSales);
        model.addAttribute("totalExpenses", totalExpenses);
        model.addAttribute("netProfit", netProfit);
        model.addAttribute("profitMargin", String.format("%.1f", profitMargin));

        // Pass the graph data
        model.addAttribute("dayLabels", trendMap.keySet());
        model.addAttribute("dayData", trendMap.values());

        return "ai-report";
    }
}