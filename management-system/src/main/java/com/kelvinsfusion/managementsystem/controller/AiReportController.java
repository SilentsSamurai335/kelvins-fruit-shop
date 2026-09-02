package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.Expense;
import com.kelvinsfusion.managementsystem.model.Sale;
import com.kelvinsfusion.managementsystem.repository.ExpenseRepository;
import com.kelvinsfusion.managementsystem.repository.SaleRepository;
import com.kelvinsfusion.managementsystem.service.GeminiService; // Ensure this is imported!
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

@Controller
public class AiReportController {

    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private GeminiService geminiService;

    @GetMapping("/ai-report")
    public String getAiReport(@RequestParam(value = "period", required = false) String period, Model model) {

        // If they just load the page without submitting the form, return empty view
        if (period == null) {
            model.addAttribute("currentPeriod", "month");
            return "ai-report";
        }

        LocalDate now = LocalDate.now();
        LocalDate start = now.minusYears(100);
        LocalDate end = now;
        String timeFrameLabel = "All Time";

        // 1. FILTER TIMEFRAME
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

        // 2. FETCH DATA
        List<Expense> expenses = expenseRepository.findByDateBetween(start, end);
        List<Sale> sales = saleRepository.findBySaleDateTimeBetween(start.atStartOfDay(), end.atTime(23, 59, 59));

        double totalRevenue = sales.stream().mapToDouble(Sale::getTotalAmount).sum();
        double totalExpenses = expenses.stream().mapToDouble(Expense::getAmount).sum();

        // 3. BUILD THE PROMPT (Tuned for fresh juice & raw ingredients)
        String prompt = String.format(
                "You are an expert financial analyst for a fresh fruit juice business. " +
                        "Analyze this data for the period: %s.\n\n" +
                        "Total Revenue from mixed juice: KES %.2f\n" +
                        "Total Raw Ingredient Expenses (Fruit, Tamarind, Sugar, etc.): KES %.2f\n\n" +
                        "Provide a brief, professional report. Calculate the profit margin, assess if the raw ingredient spending is efficient compared to the revenue, and give one actionable tip to improve profitability. Do not use markdown symbols like asterisks.",
                timeFrameLabel, totalRevenue, totalExpenses
        );

        // 4. GET AI RESPONSE
        try {
            String aiResponse = geminiService.generateText(prompt);
            model.addAttribute("aiResponse", aiResponse);
        } catch (Exception e) {
            model.addAttribute("aiResponse", "Error connecting to AI: " + e.getMessage());
        }

        model.addAttribute("currentPeriod", period);
        model.addAttribute("timeFrameLabel", timeFrameLabel);

        return "ai-report";
    }
}