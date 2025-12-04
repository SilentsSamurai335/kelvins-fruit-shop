package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.Product;
import com.kelvinsfusion.managementsystem.model.Sale;
import com.kelvinsfusion.managementsystem.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired // <--- Add this
    private com.kelvinsfusion.managementsystem.repository.SaleRepository saleRepository;

    @Autowired
    private com.kelvinsfusion.managementsystem.repository.ExpenseRepository expenseRepository;

    // 1. Show the blank form
    @GetMapping("/add-product")
    public String showAddProductForm(Model model) {
        model.addAttribute("product", new Product());
        return "add-product"; // This looks for a file named add-product.html
    }

    // 2. Handle the "Save" button click
    @PostMapping("/save-product")
    public String saveProduct(Product product) {
        productRepository.save(product); // Saves to MySQL!
        return "redirect:/"; // Go back home after saving
    }

    // 3. Show the list of products
    @GetMapping("/products")
    public String viewHomePage(Model model) {
        // This command goes to MySQL, gets ALL rows, and puts them in a list
        model.addAttribute("listProducts", productRepository.findAll());
        return "products"; // Looks for products.html
    }
    // 4. The Real Home Page
    @GetMapping("/")
    public String showLandingPage(Model model) {
        List<com.kelvinsfusion.managementsystem.model.Sale> allSales = saleRepository.findAll();

        // 1. DATA FOR "PRODUCT MIX"
        java.util.Map<String, Integer> productMap = new java.util.HashMap<>();
        for (com.kelvinsfusion.managementsystem.model.Sale sale : allSales) {
            String juiceName = sale.getItemsSold();
            productMap.put(juiceName, productMap.getOrDefault(juiceName, 0) + 1);
        }
        model.addAttribute("productLabels", productMap.keySet());
        model.addAttribute("productData", productMap.values());

        // 2. DATA FOR "DAILY TREND" (Safe Mode)
        java.util.Map<java.time.LocalDate, Double> trendMap = new java.util.TreeMap<>();

        for (com.kelvinsfusion.managementsystem.model.Sale sale : allSales) {
            // FIX: Only calculate if the date exists!
            if (sale.getSaleDateTime() != null) {
                java.time.LocalDate date = sale.getSaleDateTime().toLocalDate();
                trendMap.put(date, trendMap.getOrDefault(date, 0.0) + sale.getTotalAmount());
            }
        }
        model.addAttribute("trendDates", trendMap.keySet());
        model.addAttribute("trendAmounts", trendMap.values());

        return "index";
    }
    // --- REPLACE THE OLD "SELL" METHOD WITH THIS NEW ONE ---
    @GetMapping("/sell/{id}")
    public String sellProduct(@PathVariable("id") Long id,
                              @RequestParam(value = "size", required = false) String size) {

        Product product = productRepository.findById(id).orElse(null);

        if (product != null) {
            Sale newSale = new Sale();
            newSale.setSaleDateTime(java.time.LocalDateTime.now());

            // --- CASE 1: JUICES (Unlimited Stock) ---
            if ("Beverage".equals(product.getCategory())) {
                newSale.setItemsSold(product.getName() + " (" + size + ")");

                if ("250ml".equals(size)) {
                    newSale.setTotalAmount(product.getPriceSmall());
                } else {
                    newSale.setTotalAmount(product.getPriceLarge());
                }
                // No stock deduction for juices!
                saleRepository.save(newSale);
            }

            // --- CASE 2: SNACKS & SPICES (Track Stock) ---
            else {
                // We use 'stockSmall' and 'priceSmall' to store the single stock/price
                if (product.getStockSmall() > 0) {
                    newSale.setItemsSold(product.getName()); // No size needed
                    newSale.setTotalAmount(product.getPriceSmall());

                    // Deduct 1 unit
                    product.setStockSmall(product.getStockSmall() - 1);

                    productRepository.save(product);
                    saleRepository.save(newSale);
                }
            }
        }
        return "redirect:/products";
    }

    // 6. Delete the Product
    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable("id") Long id) {
        productRepository.deleteById(id);
        return "redirect:/products";
    }

    @GetMapping("/login")
    public String showLoginPage() {
        return "login"; // Looks for login.html
    }
    // 1. Show the Expenses Page
    @GetMapping("/expenses")
    public String showExpenses(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "category", required = false) String category,
            Model model) {

        // --- 1. DATE FILTER LOGIC ---
        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.LocalDate start = now.minusYears(100); // Default: All time
        java.time.LocalDate end = now;
        String periodTitle = "All Time";

        if (month != null && year != null) {
            start = java.time.LocalDate.of(year, month, 1);
            end = start.plusMonths(1).minusDays(1);
            periodTitle = java.time.Month.of(month).name() + " " + year;
        } else if ("today".equals(period)) {
            start = now;
            end = now;
            periodTitle = "Today";
        } else if ("month".equals(period)) {
            start = now.withDayOfMonth(1);
            end = now.withDayOfMonth(now.lengthOfMonth());
            periodTitle = "This Month";
        }

        List<com.kelvinsfusion.managementsystem.model.Expense> expenses = expenseRepository.findByDateBetween(start, end);

        // --- 2. CATEGORY FILTER LOGIC ---
        if (category != null && !category.isEmpty() && !category.equals("ALL")) {

            // Define Groups
            java.util.List<String> utilityGroup = java.util.Arrays.asList("Rent", "Gas", "Tap Water", "Garbage", "Token");
            java.util.List<String> ingredientGroup = java.util.Arrays.asList("Juice Ingredients", "Uji Ingredients", "Snacks", "Spices", "Packaging", "Kitchen Utilities");
            java.util.List<String> opsGroup = java.util.Arrays.asList("Transport", "Lunch", "Maintenance", "Honorarium");

            if (category.equals("GROUP_UTILITIES")) {
                expenses = expenses.stream().filter(e -> utilityGroup.contains(e.getCategory())).toList();
                periodTitle += " (Utilities)";
            } else if (category.equals("GROUP_INGREDIENTS")) {
                expenses = expenses.stream().filter(e -> ingredientGroup.contains(e.getCategory())).toList();
                periodTitle += " (Ingredients)";
            } else if (category.equals("GROUP_OPERATIONS")) {
                expenses = expenses.stream().filter(e -> opsGroup.contains(e.getCategory())).toList();
                periodTitle += " (Operations)";
            } else {
                // Exact Match (e.g., just "Rent")
                String finalCategory = category;
                expenses = expenses.stream().filter(e -> e.getCategory().equals(finalCategory)).toList();
                periodTitle += " (" + category + ")";
            }
        }

        // Calculate Total for the filtered view
        double totalExpense = expenses.stream().mapToDouble(com.kelvinsfusion.managementsystem.model.Expense::getAmount).sum();

        model.addAttribute("listExpenses", expenses);
        model.addAttribute("newExpense", new com.kelvinsfusion.managementsystem.model.Expense());
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("periodTitle", periodTitle);
        model.addAttribute("selectedCategory", category);

        // For the Dropdowns
        model.addAttribute("selectedMonth", (month != null) ? month : now.getMonthValue());
        model.addAttribute("selectedYear", (year != null) ? year : now.getYear());

        return "expenses";
    }

    // 2. Save a new Expense
    @PostMapping("/save-expense")
    public String saveExpense(com.kelvinsfusion.managementsystem.model.Expense expense) {
        expenseRepository.save(expense);
        return "redirect:/expenses";
    }
}