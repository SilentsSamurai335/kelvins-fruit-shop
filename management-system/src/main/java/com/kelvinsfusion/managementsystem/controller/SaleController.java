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
    public String showSales(@RequestParam(value = "period", defaultValue = "all") String period, Model model) {

        List<Sale> sales;

        // Date Math Logic
        LocalDateTime now = LocalDateTime.now();

        if (period.equals("today")) {
            // From 00:00 today to right now
            LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
            sales = saleRepository.findBySaleDateTimeBetween(startOfDay, now);
            model.addAttribute("periodTitle", "Today's Sales");

        } else if (period.equals("month")) {
            // From Day 1 of this month to right now
            LocalDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            sales = saleRepository.findBySaleDateTimeBetween(startOfMonth, now);
            model.addAttribute("periodTitle", "This Month's Sales");

        } else {
            // Default: Show everything
            sales = saleRepository.findAll();
            model.addAttribute("periodTitle", "All Time Sales");
        }

        // Calculate Revenue for the filtered list ONLY
        double totalRevenue = 0.0;
        for (Sale sale : sales) {
            totalRevenue += sale.getTotalAmount();
        }

        model.addAttribute("listSales", sales);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("activePeriod", period); // To highlight the button

        return "sales";
    }

    // 2. The Logic to SELL a specific product
    @GetMapping("/sell/{id}")
    public String sellProduct(@PathVariable("id") Long id) {
        Product product = productRepository.findById(id).orElse(null);

        if (product != null) {
            // CHECK: Do we have stock?
            if (product.getQuantity() > 0) {

                // 1. Create Sale Record
                Sale newSale = new Sale();
                newSale.setItemsSold(product.getName());
                newSale.setTotalAmount(product.getPrice());
                saleRepository.save(newSale);

                // 2. DEDUCT STOCK
                product.setQuantity(product.getQuantity() - 1);
                productRepository.save(product); // Update the product in DB
            }
        }
        return "redirect:/sales";
    }
}