package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.Product;
import com.kelvinsfusion.managementsystem.model.Sale;
import com.kelvinsfusion.managementsystem.repository.ProductRepository;
import com.kelvinsfusion.managementsystem.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam; // You need this for the filter!
import java.util.List;

@Controller
public class SaleController {

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ProductRepository productRepository;

    // 1. Show the Sales History Page
    @GetMapping("/sales")
    public String showSales(@RequestParam(value = "period", required = false) String period,
                            @RequestParam(value = "month", required = false) Integer month,
                            @RequestParam(value = "year", required = false) Integer year,
                            Model model) {

        List<Sale> sales;
        LocalDateTime now = LocalDateTime.now();
        String title = "All Time Sales";

        // Logic to determine date range
        if (month != null && year != null) {
            // Case 1: Specific Month Selected (e.g., November 2025)
            LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
            LocalDateTime end = start.plusMonths(1).minusSeconds(1);
            sales = saleRepository.findBySaleDateTimeBetween(start, end);
            title = java.time.Month.of(month).name() + " " + year + " Sales";

        } else if ("today".equals(period)) {
            // Case 2: Today
            LocalDateTime start = now.toLocalDate().atStartOfDay();
            sales = saleRepository.findBySaleDateTimeBetween(start, now);
            title = "Today's Sales";

        } else if ("month".equals(period)) {
            // Case 3: Current Month
            LocalDateTime start = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            sales = saleRepository.findBySaleDateTimeBetween(start, now);
            title = "This Month's Sales";

        } else {
            // Case 4: All Time
            sales = saleRepository.findAll();
        }

        // Calculate Revenue
        double totalRevenue = 0.0;
        for (Sale sale : sales) {
            totalRevenue += sale.getTotalAmount();
        }

        model.addAttribute("listSales", sales);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("periodTitle", title);

        // Pass current month/year to keep the dropdown selected
        model.addAttribute("selectedMonth", (month != null) ? month : now.getMonthValue());
        model.addAttribute("selectedYear", (year != null) ? year : now.getYear());

        return "sales";
    }



}