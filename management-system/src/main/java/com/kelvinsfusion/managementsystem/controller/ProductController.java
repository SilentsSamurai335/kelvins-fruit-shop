package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.*;
import com.kelvinsfusion.managementsystem.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import java.util.*;

@Controller
public class ProductController {

    @Autowired private ProductRepository productRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamLogRepository teamLogRepository;
    @Autowired private OperatingCashRepository cashRepo;
    // --- LOGIN & DASHBOARD ---
    @GetMapping("/login")
    public String showLoginPage() { return "login"; }

    @GetMapping("/")
    public String showLandingPage(Model model, Principal principal) {
        List<Sale> allSales = saleRepository.findAll();
        LocalDate today = LocalDate.now();

        Map<String, Integer> productMap = new HashMap<>();
        Map<LocalDate, Double> trendMap = new TreeMap<>();

        for (Sale sale : allSales) {
            // Product Popularity logic
            if (sale.getItemsSold() != null) {
                String raw = sale.getItemsSold();
                String clean = raw.contains(" (") ? raw.substring(0, raw.lastIndexOf(" (")) : raw;
                productMap.put(clean, productMap.getOrDefault(clean, 0) + 1);
            }

            // Trend Chart logic (NEW: Excludes DEBT)
            if (sale.getSaleDateTime() != null && !"DEBT".equalsIgnoreCase(sale.getPaymentMethod())) {
                LocalDate date = sale.getSaleDateTime().toLocalDate();
                trendMap.put(date, trendMap.getOrDefault(date, 0.0) + sale.getTotalAmount());
            }
        }

        // Today's Sales logic (NEW: Reuses allSales list and excludes DEBT)
        double todaySalesTotal = allSales.stream()
                .filter(sale -> sale.getSaleDateTime() != null)
                .filter(sale -> sale.getSaleDateTime().toLocalDate().equals(today))
                .filter(sale -> !"DEBT".equalsIgnoreCase(sale.getPaymentMethod()))
                .mapToDouble(Sale::getTotalAmount)
                .sum();

        model.addAttribute("productLabels", productMap.keySet());
        model.addAttribute("productData", productMap.values());
        model.addAttribute("trendDates", trendMap.keySet());
        model.addAttribute("trendAmounts", trendMap.values());
        model.addAttribute("todaySalesTotal", todaySalesTotal);

        // Notifications
        List<TeamLog> allChats = teamLogRepository.findByTypeOrderByTimestampDesc("CHAT");
        boolean hasNewMessage = false;
        if (principal != null && !allChats.isEmpty()) {
            TeamLog lastMsg = allChats.get(0);
            if (!lastMsg.getAuthor().equals(principal.getName())) {
                hasNewMessage = true;
            }
        }
        model.addAttribute("hasNewMessage", hasNewMessage);

        // Cash at Hand
        OperatingCash cashAtHand = cashRepo.findById(1L).orElse(new OperatingCash());
        model.addAttribute("cashAtHand", cashAtHand.getAmount());

        // Calculate total unpaid debts globally
        double totalOutstandingDebt = allSales.stream()
                .filter(sale -> "DEBT".equalsIgnoreCase(sale.getPaymentMethod()))
                .mapToDouble(Sale::getTotalAmount)
                .sum();

        model.addAttribute("totalOutstandingDebt", totalOutstandingDebt);

        return "index";
    }

    // NEW: Fixed URL path and proper redirect
    @PostMapping("/update-cash")
    public String updateCash(@RequestParam Double newCashAmount) {
        OperatingCash cash = cashRepo.findById(1L).orElse(new OperatingCash());
        cash.setId(1L);
        cash.setAmount(newCashAmount);
        cashRepo.save(cash);

        // Redirects back to the GetMapping so the dashboard reloads fully populated
        return "redirect:/";
    }

    // --- INVENTORY ---
    @GetMapping("/products")
    public String showProducts(Model model) {
        List<Product> products = productRepository.findAll();
        List<Product> beverages = products.stream().filter(p -> "Beverage".equals(p.getCategory())).toList();
        model.addAttribute("listProducts", products);
        model.addAttribute("beverageList", beverages);
        return "products";
    }

    @GetMapping("/add-product")
    public String showAddProductForm(Model model) {
        model.addAttribute("product", new Product());
        return "add-product";
    }

    @PostMapping("/save-product")
    public String saveProduct(Product product) {
        if (product.getStockSmall() < 0) product.setStockSmall(0);
        if (product.getStockLarge() < 0) product.setStockLarge(0);
        productRepository.save(product);
        return "redirect:/products";
    }

    @PostMapping("/add-stock")
    public String addStock(@RequestParam Long productId,
                           @RequestParam int quantity,
                           Principal principal) {

        // 1. Security Check
        if (principal == null || !"Kelvin".equals(principal.getName())) {
            return "redirect:/";
        }

        // 2. Find the product
        Product product = productRepository.findById(productId).orElse(null);

        // 3. Update the stock using the EXACT methods from your screenshot
        if (product != null) {
            // We use getStockSmall() and setStockSmall() for standard snack inventory
            product.setStockSmall(product.getStockSmall() + quantity);

            productRepository.save(product);
        }

        // 4. Redirect back to POS
        return "redirect:/products";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model) {
        Product product = productRepository.findById(id).orElse(null);
        model.addAttribute("product", product);
        return "add-product";
    }

    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable("id") Long id) {
        productRepository.deleteById(id);
        return "redirect:/products";
    }

    // --- POS / SELLING ---
    @GetMapping("/sell/{id}")
    public String sellProduct(@PathVariable("id") Long id,
                              @RequestParam(value = "size", required = false) String size,
                              @RequestParam(value = "qty", defaultValue = "1") int qty,
                              @RequestParam(value = "payment", defaultValue = "Cash") String payment,
                              @RequestParam(value = "customPrice", required = false) Double customPrice,
                              @RequestParam(value = "customSizeName", required = false) String customSizeName,
                              @RequestParam(value = "manualDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate manualDate,
                              Authentication authentication) {

        Product product = productRepository.findById(id).orElse(null);
        if (product != null) {
            Sale newSale = new Sale();

            // 1. Capture the exact username from the active session (e.g., 'paul', 'godi', 'les')
            String currentUsername = "Admin";
            if (authentication != null && authentication.isAuthenticated()) {
                currentUsername = authentication.getName();
            }
            newSale.setSeller(currentUsername);

            // 2. Admin Date Logic
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN"));

            if (isAdmin && manualDate != null && !manualDate.isAfter(LocalDate.now())) {
                newSale.setSaleDateTime(manualDate.atTime(LocalTime.now()));
            } else {
                newSale.setSaleDateTime(LocalDateTime.now());
            }

            newSale.setQuantity(qty);
            newSale.setPaymentMethod(payment);

            if ("Beverage".equals(product.getCategory())) {
                double price;
                if ("Custom".equals(size) && customPrice != null) {
                    String label = (customSizeName != null && !customSizeName.isEmpty()) ? customSizeName : "Custom";
                    newSale.setItemsSold(product.getName() + " (" + label + ")");
                    price = customPrice;
                } else {
                    newSale.setItemsSold(product.getName() + " (" + size + ")");
                    price = "250ml".equals(size) ? product.getPriceSmall() : product.getPriceLarge();
                }
                newSale.setTotalAmount(price * qty);
                saleRepository.save(newSale);
            } else {
                if (product.getStockSmall() >= qty) {
                    newSale.setItemsSold(product.getName());
                    newSale.setTotalAmount(product.getPriceSmall() * qty);
                    product.setStockSmall(product.getStockSmall() - qty);
                    productRepository.save(product);
                    saleRepository.save(newSale);
                }
            }
        }
        return "redirect:/products";
    }

    @GetMapping("/inventory/delete/{id}")
    public String deleteProduct(@PathVariable Long id, Principal principal) {
        // 1. Security Check: Only Kelvin has the authority to delete a product
        if (principal != null && "Kelvin".equals(principal.getName())) {
            // 2. Delete the item from the database
            productRepository.deleteById(id);
        }

        // 3. Send you right back to the inventory page
        // (Change this to "redirect:/products" if that is your actual URL)
        return "redirect:/products";
    }

    @org.springframework.web.bind.annotation.GetMapping("/products/edit/{id}")
    public String editProduct(@org.springframework.web.bind.annotation.PathVariable Long id, org.springframework.ui.Model model) {
        Product productToEdit = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product Id:" + id));

        // We pass it as "product" so your existing add-product form catches it naturally
        model.addAttribute("product", productToEdit);
        return "add-product";
    }
 
    @PostMapping("/sell-custom")
    public String sellCustom(@RequestParam("mixName") String mixName,
                             @RequestParam(value = "amount", defaultValue = "0.0") double amount,
                             @RequestParam("payment") String payment) {
        Sale s = new Sale();
        s.setItemsSold("Mix: " + mixName);
        s.setTotalAmount(amount);
        s.setQuantity(1);
        s.setPaymentMethod(payment);
        s.setSaleDateTime(LocalDateTime.now());
        saleRepository.save(s);
        return "redirect:/products";
    }

    @GetMapping("/sales")
    public String showSales(@RequestParam(value = "period", required = false) String period,
                            @RequestParam(value = "month", required = false) Integer month,
                            @RequestParam(value = "year", required = false) Integer year,
                            @RequestParam(value = "date", required = false) java.time.LocalDate specificDate,
                            @RequestParam(value = "paymentMethod", required = false, defaultValue = "ALL") String paymentMethod,
                            @RequestParam(value = "search", required = false) String search,
                            Model model) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusYears(100);
        LocalDateTime end = now;
        String title = "All Time Sales";

        if (specificDate != null) {
            start = specificDate.atStartOfDay();
            end = specificDate.atTime(23, 59, 59);
            title = "Sales for " + specificDate;
        } else if (month != null && year != null) {
            start = LocalDateTime.of(year, month, 1, 0, 0);
            end = start.plusMonths(1).minusSeconds(1);
            title = Month.of(month).name() + " " + year + " Sales";
        } else if ("today".equals(period)) {
            start = now.toLocalDate().atStartOfDay();
            title = "Today's Sales";
        } else if ("week".equals(period)) {
            start = now.minusDays(now.getDayOfWeek().getValue() - 1).toLocalDate().atStartOfDay();
            title = "This Week's Sales";
        } else if ("month".equals(period)) {
            start = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            title = "This Month's Sales";
        }

        // 1. Fetch all sales for the chosen date range FIRST
        List<Sale> sales = saleRepository.findBySaleDateTimeBetween(start, end);

        // 2. Filter in-memory to catch the dynamic "Split" strings
        if (!"ALL".equalsIgnoreCase(paymentMethod)) {
            String filterTarget = paymentMethod.toUpperCase();

            sales = sales.stream().filter(s -> {
                if (s.getPaymentMethod() == null) return false;
                String pm = s.getPaymentMethod().toUpperCase();

                if (filterTarget.equals("MPESA")) {
                    return pm.equals("MPESA") || (pm.startsWith("SPLIT") && pm.contains("M-PESA"));
                } else if (filterTarget.equals("CASH")) {
                    return pm.equals("CASH") || (pm.startsWith("SPLIT") && pm.contains("CASH"));
                } else if (filterTarget.equals("SPLIT")) {
                    return pm.startsWith("SPLIT");
                }

                return pm.equals(filterTarget);
            }).collect(Collectors.toList());

            title += " (" + filterTarget + ")";
        }

        if (search != null && !search.trim().isEmpty()) {
            String keyword = search.toLowerCase();
            sales = sales.stream()
                    .filter(s -> (s.getItemsSold() != null && s.getItemsSold().toLowerCase().contains(keyword)) ||
                            (s.getPhoneNumber() != null && s.getPhoneNumber().contains(keyword)))
                    .toList();
        }

        // Sort the FILTERED sales list (newest first)
        sales = sales.stream()
                .sorted(Comparator.comparing(Sale::getSaleDateTime).reversed())
                .collect(Collectors.toList());

        // Build the Accordion Map using the FILTERED list
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
        Map<String, List<Sale>> groupedSales = new LinkedHashMap<>();
        Map<String, Double> monthlyTotals = new HashMap<>();

        for (Sale sale : sales) {
            if (sale.getSaleDateTime() != null) {
                String monthYear = sale.getSaleDateTime().format(formatter).toUpperCase();
                groupedSales.computeIfAbsent(monthYear, k -> new ArrayList<>()).add(sale);
            }
        }

        for (Map.Entry<String, List<Sale>> entry : groupedSales.entrySet()) {
            double total = entry.getValue().stream()
                    .filter(s -> !"DEBT".equalsIgnoreCase(s.getPaymentMethod())) // NEW: Exclude debts
                    .mapToDouble(Sale::getTotalAmount).sum();
            monthlyTotals.put(entry.getKey(), total);
        }

        // --- NEW: Group the FILTERED sales by seller for dynamic cards ---
        Map<String, Double> dynamicSalesBySeller = sales.stream()
                .filter(s -> !"Admin".equalsIgnoreCase(s.getSeller()))
                .filter(s -> !"DEBT".equalsIgnoreCase(s.getPaymentMethod())) // NEW: Exclude debts
                .collect(Collectors.groupingBy(
                        s -> {
                            String sellerName = s.getSeller();
                            if (sellerName == null || sellerName.trim().isEmpty()) {
                                return "KELVIN";
                            }
                            return sellerName.trim().toUpperCase();
                        },
                        Collectors.summingDouble(Sale::getTotalAmount)
                ));

        double totalRevenue = sales.stream()
                .filter(s -> !"DEBT".equalsIgnoreCase(s.getPaymentMethod()))
                .mapToDouble(Sale::getTotalAmount).sum();

        model.addAttribute("listSales", sales);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("periodTitle", title);
        model.addAttribute("selectedDate", specificDate);
        model.addAttribute("selectedMonth", (month != null) ? month : now.getMonthValue());
        model.addAttribute("selectedYear", (year != null) ? year : now.getYear());
        model.addAttribute("selectedPayment", paymentMethod);
        model.addAttribute("searchKeyword", search);
        model.addAttribute("sales", sales);
        model.addAttribute("groupedSales", groupedSales);
        model.addAttribute("monthlyTotals", monthlyTotals);

        // Bind the dynamically filtered map to the model
        model.addAttribute("salesBySeller", dynamicSalesBySeller);

        return "sales";
    }

    @org.springframework.web.bind.annotation.GetMapping("/sales/edit/{id}")
    public String editSale(@org.springframework.web.bind.annotation.PathVariable Long id, org.springframework.ui.Model model) {

        // 1. Grab the specific sale from the database
        Sale saleToEdit = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid sale Id:" + id));

        // 2. Fetch the sorted list so the table doesn't disappear
        java.util.List<Sale> salesList = saleRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Sale::getSaleDateTime).reversed())
                .collect(java.util.stream.Collectors.toList());

        // 3. Send the specific sale to the model so an HTML form can display it
        model.addAttribute("listProducts", productRepository.findAll());
        model.addAttribute("editSale", saleToEdit);
        model.addAttribute("sales", salesList);

        // Note: Add any other attributes your sales page requires (like totalRevenue) here so they don't load as null!

        return "sales";
    }

    @org.springframework.web.bind.annotation.PostMapping("/sales/save")
    public String saveEditedSale(@org.springframework.web.bind.annotation.ModelAttribute("editSale") Sale editedSale) {
        Sale existingSale = saleRepository.findById(editedSale.getId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid sale Id"));

        // Update the specific fields from the modal
        existingSale.setItemsSold(editedSale.getItemsSold());
        existingSale.setTotalAmount(editedSale.getTotalAmount());
        existingSale.setQuantity(editedSale.getQuantity());
        existingSale.setPaymentMethod(editedSale.getPaymentMethod());

        saleRepository.save(existingSale);
        return "redirect:/sales";
    }

    @GetMapping("/sales/delete/{id}")
    public String deleteSale(@PathVariable Long id, Principal principal) {
        // Double-check security: only Kelvin can do this
        if (principal != null && "Kelvin".equals(principal.getName())) {
            saleRepository.deleteById(id);
        }
        return "redirect:/sales";
    }

    // --- EXPENSES ---
    @org.springframework.web.bind.annotation.GetMapping("/expenses")
    public String showExpenses(@org.springframework.web.bind.annotation.RequestParam(value = "period", required = false) String period,
                               @org.springframework.web.bind.annotation.RequestParam(value = "month", required = false) Integer month,
                               @org.springframework.web.bind.annotation.RequestParam(value = "year", required = false) Integer year,
                               @org.springframework.web.bind.annotation.RequestParam(value = "date", required = false) String specificDate, // <-- NEW PARAMETER
                               @org.springframework.web.bind.annotation.RequestParam(value = "category", required = false) String category,
                               @org.springframework.web.bind.annotation.RequestParam(value = "search", required = false) String search,
                               @org.springframework.web.bind.annotation.RequestParam(value = "specificYear", required = false) Integer specificYear,
                               org.springframework.ui.Model model) {

        java.time.LocalDate now = java.time.LocalDate.now();
        java.time.LocalDate start = now.minusYears(100);
        java.time.LocalDate end = now;
        String title = "All Time";

        // 1. SPECIFIC DATE FILTER (Checked first!)
        if (specificDate != null && !specificDate.isEmpty()) {
            java.time.LocalDate exactDate = java.time.LocalDate.parse(specificDate);
            start = exactDate;
            end = exactDate;
            title = "Transactions on " + exactDate.toString();
        }
        // 2. ARCHIVE FILTER
        else if (month != null && year != null) {
            start = java.time.LocalDate.of(year, month, 1);
            end = start.plusMonths(1).minusDays(1);
            title = java.time.Month.of(month).name() + " " + year;
        }
        else if (specificYear != null) {
            start = java.time.LocalDate.of(specificYear, 1, 1);
            end = java.time.LocalDate.of(specificYear, 12, 31);
            title = "Year " + specificYear;
            List<Expense> expenses = expenseRepository.findByDateBetween(start, end);
        }
        // 3. PERIOD FILTER
        else if ("today".equals(period)) {
            start = now;
            title = "Today";
        } else if ("week".equals(period)) {
            start = now.minusDays(now.getDayOfWeek().getValue() - 1);
            title = "This Week";
        } else if ("month".equals(period)) {
            start = now.withDayOfMonth(1);
            title = "This Month";
        }

        // Hit the database using the dates we calculated above
        java.util.List<Expense> expenses = expenseRepository.findByDateBetween(start, end);

        // 4. CATEGORY FILTER (Filters the database results)
        if (category != null && !category.isEmpty() && !"ALL".equals(category)) {
            if ("GROUP_STOCKS".equals(category)) {
                expenses = expenses.stream().filter(e -> java.util.Arrays.asList("Sugar", "Juice Ingredients", "Uji Ingredients", "Coffee Ingredients", "Snacks", "Spices", "Packaging", "Blending Water").contains(e.getCategory())).toList();
            } else if ("GROUP_UTILITIES".equals(category)) {
                expenses = expenses.stream().filter(e -> java.util.Arrays.asList("Rent", "Gas", "Tap Water", "Garbage", "Token").contains(e.getCategory())).toList();
            } else if ("GROUP_OPERATIONS".equals(category)) {
                expenses = expenses.stream().filter(e -> java.util.Arrays.asList("Transport", "Lunch", "Maintenance").contains(e.getCategory())).toList();
            } else if ("GROUP_HONORARIUM".equals(category)) {
                expenses = expenses.stream().filter(e -> java.util.List.of("Honorarium").contains(e.getCategory())).toList();
            } else {
                String cat = category;
                expenses = expenses.stream().filter(e -> cat.equals(e.getCategory())).toList();
            }
        }

        // 5. SORT THE LIST: Most recent dates at the top
        expenses = expenses.stream()
                .sorted(java.util.Comparator.comparing(Expense::getDate).reversed())
                .collect(java.util.stream.Collectors.toList());

        // NEW: GROUP BY MONTH FOR THE ACCORDION
        // This creates a map where the key is "SEPTEMBER 2026" and the value is the list of expenses for that month.
        java.util.Map<String, java.util.List<Expense>> groupedExpenses = new java.util.LinkedHashMap<>();
        for (Expense e : expenses) {
            String monthYear = e.getDate().getMonth().name() + " " + e.getDate().getYear();
            groupedExpenses.computeIfAbsent(monthYear, k -> new java.util.ArrayList<>()).add(e);
        }

        //THE NEW SEARCH LOGIC
        if (search != null && !search.trim().isEmpty()) {
            String keyword = search.toLowerCase();
            expenses = expenses.stream()
                    .filter(e -> (e.getItemName() != null && e.getItemName().toLowerCase().contains(keyword)) ||
                            (e.getCategory() != null && e.getCategory().toLowerCase().contains(keyword)))
                    .toList();
        }

        // Calculate the total for the main cost
        double totalBaseCost = expenses.stream()
                .mapToDouble(e -> e.getAmount()) // Use getAmount() if your variable is named amount
                .sum();

        // Calculate the total for the transaction costs
        double totalTxCost = expenses.stream()
                .mapToDouble(e -> e.getTransactionCost() != null ? e.getTransactionCost() : 0.0)
                .sum();

        // Calculate combined total
        double grandTotal = totalBaseCost + totalTxCost;

        model.addAttribute("totalCost", totalBaseCost);
        model.addAttribute("totalTxCost", totalTxCost);
        model.addAttribute("grandTotal", grandTotal);
        model.addAttribute("listExpenses", expenses);
        model.addAttribute("newExpense", new Expense());
        model.addAttribute("periodTitle", title);
        model.addAttribute("selectedMonth", (month != null) ? month : now.getMonthValue());
        model.addAttribute("selectedYear", (year != null) ? year : now.getYear());
        model.addAttribute("searchKeyword", search);
        model.addAttribute("expense", new Expense());
        model.addAttribute("selectedDate", specificDate);
        model.addAttribute("groupedExpenses", groupedExpenses);

        return "expenses";
    }

    @PostMapping("/save-expense")
    public String saveExpense(@ModelAttribute Expense expense,
                              @RequestParam(value = "manualDate", required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate manualDate,
                              Authentication authentication) {

        // 1. SAFELY check for both variations of the Admin role
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN") || r.getAuthority().equals("ADMIN"));

        // 2. If Admin and date is valid, use the manual date.
        if (isAdmin && manualDate != null && !manualDate.isAfter(LocalDate.now())) {
            expense.setDate(manualDate);
        } else {
            // 3. If cashier, or future date, default to today
            expense.setDate(LocalDate.now());
        }

        expenseRepository.save(expense);
        return "redirect:/expenses";
    }

    // --- DELETE EXPENSE ---
    @org.springframework.web.bind.annotation.GetMapping("/expenses/delete/{id}")
    public String deleteExpense(@org.springframework.web.bind.annotation.PathVariable Long id) {
        expenseRepository.deleteById(id);

        // Instantly redirect back to the clean expenses page
        return "redirect:/expenses";
    }

    // --- EDIT EXPENSE ---
    @org.springframework.web.bind.annotation.GetMapping("/expenses/edit/{id}")
    public String editExpense(@org.springframework.web.bind.annotation.PathVariable Long id, org.springframework.ui.Model model) {

        // 1. Find the exact expense they want to edit (This replaces the empty "new Expense()")
        Expense expenseToEdit = expenseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid expense Id:" + id));

        // 2. Fetch the full list for the table
        java.util.List<Expense> expensesList = expenseRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Expense::getDate).reversed())
                .collect(java.util.stream.Collectors.toList());

        // 3. Calculate any totals your HTML page requires (so it doesn't crash on a null value!)
        double totalExpenses = expensesList.stream().mapToDouble(Expense::getAmount).sum();

        // 4. Pass EVERYTHING the HTML needs to survive
        model.addAttribute("newExpense", expenseToEdit);
        model.addAttribute("listExpenses", expensesList);   // Populates the table
        model.addAttribute("totalExpenses", totalExpenses); // Populates the summary cards

        return "expenses";
    }

    // --- INCOME STATEMENT (FIXED: Added Calculations back) ---
    // Helper 1: Groups COGS items by their Item Name
    private Map<String, Double> groupExpensesByName(List<Expense> expenses) {
        return expenses.stream().collect(Collectors.groupingBy(
                e -> {
                    // Determine the name
                    if (e.getItemName() != null && !e.getItemName().isEmpty()) {
                        return e.getItemName();
                    }
                    return e.getCategory() != null ? e.getCategory() : "Other";
                },
                Collectors.summingDouble(e -> {
                    // Sum the amounts
                    double amount = e.getAmount();
                    double txCost = e.getTransactionCost() != null ? e.getTransactionCost() : 0.0;
                    return amount + txCost;
                })
        ));
    }

    // Helper 2: Groups Operations items by "Category: Name"
    private Map<String, Double> groupOpsExpenses(List<Expense> expenses) {
        return expenses.stream().collect(Collectors.groupingBy(
                e -> {
                    // Combine category and name
                    String itemName = e.getItemName() != null ? e.getItemName() : "";
                    return e.getCategory() + ": " + itemName;
                },
                Collectors.summingDouble(e -> {
                    // Sum the amounts
                    double amount = e.getAmount();
                    double txCost = e.getTransactionCost() != null ? e.getTransactionCost() : 0.0;
                    return amount + txCost;
                })
        ));
    }

    @GetMapping("/income-statement")
    public String showIncomeStatement(@RequestParam(value = "month", required = false) Integer month,
                                      @RequestParam(value = "year", required = false) Integer year,
                                      Model model) {
        LocalDate now = LocalDate.now();
        int curMonth = (month != null) ? month : now.getMonthValue();
        int curYear = (year != null) ? year : now.getYear();
        LocalDate start = LocalDate.of(curYear, curMonth, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        // 1. REVENUE CALCULATIONS
        List<Sale> sales = saleRepository.findAll();
        double juiceCash = 0, juiceMpesa = 0;
        double coffeeCash = 0, coffeeMpesa = 0;
        double ujiCash = 0, ujiMpesa = 0;
        double cakeCash = 0, cakeMpesa = 0;
        double cookCash = 0, cookMpesa = 0;
        double spiceCash = 0, spiceMpesa = 0;
        double snackCash = 0, snackMpesa = 0;

        for (Sale s : sales) {
            if (s.getSaleDateTime() != null) {
                LocalDate d = s.getSaleDateTime().toLocalDate();
                // Ignore debts and ensure date falls within selected month
                if (!d.isBefore(start) && !d.isAfter(end) && !"DEBT".equalsIgnoreCase(s.getPaymentMethod())) {
                    String item = (s.getItemsSold() != null) ? s.getItemsSold().toLowerCase() : "";
                    double amt = s.getTotalAmount();

                    // Group SPLIT payments properly or count MPESA
                    boolean isMp = s.getPaymentMethod() != null && s.getPaymentMethod().toUpperCase().contains("MPESA");

                    if (item.contains("uji")) {
                        if (isMp) ujiMpesa += amt; else ujiCash += amt;
                    }
                    else if (item.contains("black coffee") || item.contains("white coffee")) {
                        if (isMp) coffeeMpesa += amt; else coffeeCash += amt;
                    }
                    else if (item.contains("cupcake") || item.contains("cake")) {
                        if (isMp) cakeMpesa += amt; else cakeCash += amt;
                    }
                    else if (item.contains("cookie") || item.contains("biscuit")) {
                        if (isMp) cookMpesa += amt; else cookCash += amt;
                    }
                    else if (item.contains("spice") || item.contains("masala") || item.contains("pilau") || item.contains("ginger")) {
                        if (isMp) spiceMpesa += amt; else spiceCash += amt;
                    }
                    else if (item.contains("samosa") || item.contains("smokie") || item.contains("mandazi") || item.contains("snack")) {
                        if (isMp) snackMpesa += amt; else snackCash += amt;
                    }
                    else {
                        if (isMp) juiceMpesa += amt; else juiceCash += amt;
                    }
                }
            }
        }
        double totalSales = juiceCash + juiceMpesa + coffeeCash + coffeeMpesa + ujiCash + ujiMpesa + cakeCash + cakeMpesa + cookCash + cookMpesa + spiceCash + spiceMpesa + snackCash + snackMpesa;

        // 2. EXPENSE CALCULATIONS
        List<Expense> exps = expenseRepository.findByDateBetween(start, end);
        List<Expense> cJuice = new ArrayList<>(), cCoffee = new ArrayList<>(), cUji = new ArrayList<>(), cShared = new ArrayList<>(), eOps = new ArrayList<>();

        for (Expense e : exps) {
            String c = (e.getCategory() != null) ? e.getCategory().trim() : "Other";
            if ("Juice Ingredients".equalsIgnoreCase(c)) cJuice.add(e);
            else if ("Uji Ingredients".equalsIgnoreCase(c)) cUji.add(e);
            else if ("Coffee Ingredients".equalsIgnoreCase(c)) cCoffee.add(e);
            else if ("Sugar".equalsIgnoreCase(c) || "Spices".equalsIgnoreCase(c) || "Shared".equalsIgnoreCase(c) || "Blending Water".equalsIgnoreCase(c) || "Packaging".equalsIgnoreCase(c) || "Snacks".equalsIgnoreCase(c)) cShared.add(e);
            else eOps.add(e);
        }

        // Ensure totals include both amount and transaction costs
        double tCogs = 0.0;
        for (Expense e : cJuice) tCogs += e.getAmount() + e.getTransactionCost();
        for (Expense e : cCoffee) tCogs += e.getAmount() + e.getTransactionCost();
        for (Expense e : cUji) tCogs += e.getAmount() + e.getTransactionCost();
        for (Expense e : cShared) tCogs += e.getAmount() + e.getTransactionCost();

        double tOps = 0.0;
        for (Expense e : eOps) tOps += e.getAmount() + e.getTransactionCost();
        double gross = totalSales - tCogs;
        double net = gross - tOps;

        // 3. PASS TO MODEL
        model.addAttribute("periodTitle", Month.of(curMonth).name() + " " + curYear);
        model.addAttribute("juiceCash", juiceCash); model.addAttribute("juiceMpesa", juiceMpesa);
        model.addAttribute("ujiCash", ujiCash); model.addAttribute("ujiMpesa", ujiMpesa);
        model.addAttribute("coffeeCash", coffeeCash); model.addAttribute("coffeeMpesa", coffeeMpesa);
        model.addAttribute("cakeCash", cakeCash); model.addAttribute("cakeMpesa", cakeMpesa);
        model.addAttribute("cookCash", cookCash); model.addAttribute("cookMpesa", cookMpesa);
        model.addAttribute("spiceCash", spiceCash); model.addAttribute("spiceMpesa", spiceMpesa);
        model.addAttribute("snackCash", snackCash); model.addAttribute("snackMpesa", snackMpesa);
        model.addAttribute("totalSales", totalSales);

        // Use the helper methods to group identical items
        model.addAttribute("cogsJuice", groupExpensesByName(cJuice));
        model.addAttribute("cogsUji", groupExpensesByName(cUji));
        model.addAttribute("cogsCoffee", groupExpensesByName(cCoffee));
        model.addAttribute("cogsShared", groupExpensesByName(cShared));
        model.addAttribute("expOps", groupOpsExpenses(eOps));

        model.addAttribute("totalCogs", tCogs); model.addAttribute("totalOps", tOps);
        model.addAttribute("grossProfit", gross); model.addAttribute("netProfit", net);
        model.addAttribute("selectedMonth", curMonth); model.addAttribute("selectedYear", curYear);

        return "income-statement";
    }

    // ==========================================
    // 7. TEAM HUB & CHAT
    // ==========================================

    @GetMapping("/team")
    public String showTeamHub(@RequestParam(value = "staff", required = false) String staffUsername,
                              Model model,
                              Principal principal) {
        String currentUser = principal.getName();

        // 1. Get Notices
        model.addAttribute("notices", teamLogRepository.findByTypeOrderByTimestampDesc("NOTICE"));

        List<TeamLog> chats = new ArrayList<>();
        String recipientForForm = "";

        // Admin Logic (Now specifically looking for 'kelvin')
        if ("Kelvin".equals(currentUser)) {
            List<User> staffList = userRepository.findAll();
            // Remove yourself from the staff list so you don't chat with yourself
            staffList.removeIf(u -> "Kelvin".equals(u.getUsername()));
            model.addAttribute("staffList", staffList);

            if (staffUsername != null && !staffUsername.isEmpty()) {
                chats = teamLogRepository.findChatHistory("Kelvin", staffUsername);
                model.addAttribute("chatTarget", staffUsername);
                recipientForForm = staffUsername;
            } else {
                model.addAttribute("chatTarget", "Select a Staff Member");
            }
        }
        // Staff Logic (Staff send messages directly to 'kelvin')
        else {
            chats = teamLogRepository.findChatHistory(currentUser, "Kelvin");
            model.addAttribute("chatTarget", "Manager (Kelvin)");
            recipientForForm = "Kelvin";
        }

        model.addAttribute("chats", chats);
        model.addAttribute("currentRecipient", recipientForForm);

        return "team-hub";
    }

    @PostMapping("/save-chat")
    public String saveChat(@RequestParam("content") String content,
                           @RequestParam("recipient") String recipient,
                           Principal principal) {
        // FIX: Reject empty messages to prevent empty bubbles
        if (recipient == null || recipient.isEmpty()) return "redirect:/team";
        if (content == null || content.trim().isEmpty()) {
            // Just refresh page, do not save "ghost" message
            if ("admin".equals(principal.getName())) return "redirect:/team?staff=" + recipient;
            return "redirect:/team";
        }

        TeamLog log = new TeamLog();
        log.setContent(content.trim());
        log.setType("CHAT");
        log.setAuthor(principal.getName());
        log.setRecipient(recipient);
        log.setTimestamp(LocalDateTime.now());
        teamLogRepository.save(log);

        if ("admin".equals(principal.getName())) {
            return "redirect:/team?staff=" + recipient;
        } else {
            return "redirect:/team";
        }
    }

    @PostMapping("/save-update")
    public String saveUpdate(@RequestParam("subject") String subject,
                             @RequestParam("content") String content,
                             Principal principal) {
        TeamLog log = new TeamLog();
        log.setSubject(subject);
        log.setContent(content);
        log.setType("NOTICE");
        log.setAuthor(principal != null ? principal.getName() : "Unknown");
        log.setTimestamp(LocalDateTime.now());
        teamLogRepository.save(log);
        return "redirect:/team?tab=active";
    }

    // --- MANAGE NOTICES (ARCHIVE/DELETE) ---
    @PostMapping("/manage-notices")
    public String manageNotices(@RequestParam("action") String action,
                                @RequestParam(value = "selectedIds", required = false) List<Long> selectedIds) {
        if (selectedIds != null && !selectedIds.isEmpty()) {
            List<TeamLog> logs = teamLogRepository.findAllById(selectedIds);
            for (TeamLog log : logs) {
                if ("NOTICE".equals(log.getType())) {
                    switch (action) {
                        case "archive":
                            log.setArchived(true);
                            teamLogRepository.save(log);
                            break;
                        case "unarchive":
                            log.setArchived(false);
                            teamLogRepository.save(log);
                            break;
                        case "delete":
                            teamLogRepository.delete(log);
                            break;
                    }
                }
            }
        }
        if ("archive".equals(action)) return "redirect:/team?tab=active";
        if ("unarchive".equals(action)) return "redirect:/team?tab=archived";
        return "redirect:/team";
    }

    // ==========================================
    // 8. MANAGE STAFF
    // ==========================================

    @GetMapping("/users")
    public String showUsers(Model model) {
        // Only query the database once and attach it to "users"
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("newUser", new User());
        return "users";
    }

    @PostMapping("/save-user")
    public String saveUser(User user) {
        if(user.getId() != null) {
            User existing = userRepository.findById(user.getId()).orElse(null);
            if(existing != null) user.setRole(existing.getRole());
        } else {
            user.setRole("STAFF");
        }
        userRepository.save(user);
        return "redirect:/users";
    }

    @GetMapping("/delete-user/{id}")
    public String deleteUser(@PathVariable("id") Long id) {
        userRepository.deleteById(id);
        return "redirect:/users";
    }

    @GetMapping("/edit-user/{id}")
    public String showEditUserForm(@PathVariable("id") Long id, Model model) {
        User user = userRepository.findById(id).orElse(null);
        model.addAttribute("user", user);
        return "edit-user";
    }

    // ==========================================
    // 9. ANALYTICS & FINANCIAL REVIEW (DYNAMIC)
    // ==========================================
    // HELPER: Groups raw items into main categories
    private String getExpenseMainCategory(String category) {
        if (category == null) return "Other";
        if (List.of("Sugar", "Juice Ingredients", "Uji Ingredients", "Coffee Ingredients", "Snacks", "Spices", "Packaging", "Blending Water").contains(category)) return "Stocks";
        if (List.of("Rent", "Gas", "Tap Water", "Token", "Garbage").contains(category)) return "Utilities";
        if (List.of("Transport", "Lunch", "Maintenance").contains(category)) return "Operations";
        if ("Honorarium".equals(category)) return "Honorarium";
        return "Other";
    }

    @GetMapping("/analytics")
    public String showAnalytics(@RequestParam(value = "period", defaultValue = "monthly") String period,
                                @RequestParam(required = false) String specificMonth,
                                @RequestParam(required = false) String specificYear,
                                Model model, Principal principal) {

        if (principal == null || !"Kelvin".equals(principal.getName())) return "redirect:/";

        LocalDate today = LocalDate.now();
        LocalDateTime startDateTime = today.minusYears(100).atStartOfDay(); // Default to all time unless filtered
        LocalDateTime endDateTime = LocalDateTime.now();
        String trendTitle = "Sales Trend";

        // 1. SETUP BASE TREND MAP
        Map<String, Double> trendMap = new LinkedHashMap<>();
        if ("daily".equals(period)) {
            startDateTime = today.atStartOfDay();
            trendTitle = "Today's Hourly Performance";
            for (int i = 8; i <= 22; i++) trendMap.put(String.format("%02d:00", i), 0.0);
        } else if ("weekly".equals(period)) {
            startDateTime = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay();
            trendTitle = "This Week (Daily)";
            String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            for (String d : days) trendMap.put(d, 0.0);
        } else if ("monthly".equals(period)) {
            startDateTime = today.withDayOfMonth(1).atStartOfDay();
            trendTitle = "This Month (Weekly)";
            for (int i = 1; i <= 5; i++) trendMap.put("Week " + i, 0.0);
        } else if ("annually".equals(period)) {
            startDateTime = today.withDayOfYear(1).atStartOfDay();
            trendTitle = "This Year (Monthly)";
            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            for (String m : months) trendMap.put(m, 0.0);
        } else if ("all".equals(period)) {
            startDateTime = today.minusYears(4).withDayOfYear(1).atStartOfDay();
            trendTitle = "All Time (Yearly)";
            int currentYear = today.getYear();
            for (int i = currentYear - 4; i <= currentYear; i++) trendMap.put(String.valueOf(i), 0.0);
        }

        // 2. FETCH AND FILTER SALES
        List<Sale> allSales = saleRepository.findAll();
        LocalDateTime finalStartDateTime = startDateTime;

        List<Sale> filteredSales = allSales.stream().filter(sale -> {
            if (sale.getSaleDateTime() == null) return false;
            LocalDateTime time = sale.getSaleDateTime();

            // Check if the user is using the explicit search boxes
            boolean hasExplicitFilter = (specificMonth != null && !specificMonth.isEmpty()) ||
                    (specificYear != null && !specificYear.isEmpty());

            if (hasExplicitFilter) {
                // Apply custom Month/Year filters and ignore the default "Current Month" cutoff
                if (specificMonth != null && !specificMonth.isEmpty()) {
                    String saleYearMonth = String.format("%04d-%02d", time.getYear(), time.getMonthValue());
                    if (!saleYearMonth.equals(specificMonth)) return false;
                }
                if (specificYear != null && !specificYear.isEmpty()) {
                    if (time.getYear() != Integer.parseInt(specificYear)) return false;
                }
            } else {
                // Standard Quick View bounds (e.g., this week, today, this month)
                if (time.isBefore(finalStartDateTime) || time.isAfter(endDateTime)) return false;
            }

            return true;
        }).collect(Collectors.toList());

        // 3. AGGREGATE SALES DATA
        double totalRevenue = 0;
        Map<String, Double> catRevenue = new HashMap<>();
        Map<String, Double> paymentMap = new HashMap<>();

        Map<String, Double> salesByDay = new LinkedHashMap<>();
        Arrays.asList("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
                .forEach(d -> salesByDay.put(d, 0.0));

        for (Sale s : filteredSales) {
            if ("DEBT".equalsIgnoreCase(s.getPaymentMethod())) continue;

            double amt = s.getTotalAmount();
            totalRevenue += amt;

            // NEW: Robust grouping for Sales Distribution Chart
            String rawItem = (s.getItemsSold() != null) ? s.getItemsSold().toLowerCase() : "";
            String displayCategory = "Other";

            if (rawItem.contains("uji")) {
                displayCategory = "Uji";
            } else if (rawItem.contains("coffee")) {
                displayCategory = "Coffee";
            } else if (rawItem.contains("cake") || rawItem.contains("cupcake")) {
                displayCategory = "Cupcakes";
            } else if (rawItem.contains("cookie") || rawItem.contains("biscuit")) {
                displayCategory = "Cookies";
            } else if (rawItem.contains("spice") || rawItem.contains("masala") || rawItem.contains("pilau") || rawItem.contains("ginger")) {
                displayCategory = "Spices";
            } else if (rawItem.contains("samosa") || rawItem.contains("smokie") || rawItem.contains("mandazi") || rawItem.contains("snack")) {
                displayCategory = "Snacks";
            } else if (rawItem.contains("mango") || rawItem.contains("passion") || rawItem.contains("pineapple") || rawItem.contains("ukwaju") || rawItem.contains("mix") || rawItem.contains("juice") || rawItem.contains("mint")) {
                // Catches all the specific fruit variations and Custom Mixes
                displayCategory = "Juices";
            }

            catRevenue.put(displayCategory, catRevenue.getOrDefault(displayCategory, 0.0) + amt);

            // Standardize Payment Methods (Fixes duplicates)
            String method = (s.getPaymentMethod() != null) ? s.getPaymentMethod().toUpperCase() : "UNKNOWN";
            if (method.startsWith("SPLIT")) method = "SPLIT";
            paymentMap.put(method, paymentMap.getOrDefault(method, 0.0) + amt);

            // Day of Week Line Chart Data
            String dayName = s.getSaleDateTime().getDayOfWeek().name();
            salesByDay.put(dayName, salesByDay.getOrDefault(dayName, 0.0) + amt);

            // Trend Area Chart Logic
            LocalDateTime time = s.getSaleDateTime();
            String key = "";
            if ("daily".equals(period)) {
                int h = time.getHour();
                if(h >= 8 && h <= 22) key = String.format("%02d:00", h);
            } else if ("weekly".equals(period)) {
                String d = time.getDayOfWeek().name();
                key = d.substring(0, 1) + d.substring(1, 3).toLowerCase();
            } else if ("monthly".equals(period)) {
                int day = time.getDayOfMonth();
                int week = (day - 1) / 7 + 1;
                key = "Week " + (Math.min(week, 5));
            } else if ("annually".equals(period)) {
                String m = time.getMonth().name();
                key = m.substring(0, 1) + m.substring(1, 3).toLowerCase();
            } else {
                key = String.valueOf(time.getYear());
            }

            if (trendMap.containsKey(key)) trendMap.put(key, trendMap.get(key) + amt);
        }

        // 4. FETCH AND FILTER EXPENSES
        List<Expense> allExpenses = expenseRepository.findAll();
        double totalExpenses = 0.0;
        Map<String, Double> expMap = new HashMap<>();

        for (Expense e : allExpenses) {
            if (e.getDate() != null) {
                boolean hasExplicitFilter = (specificMonth != null && !specificMonth.isEmpty()) ||
                        (specificYear != null && !specificYear.isEmpty());

                if (hasExplicitFilter) {
                    if (specificMonth != null && !specificMonth.isEmpty()) {
                        String expYearMonth = String.format("%04d-%02d", e.getDate().getYear(), e.getDate().getMonthValue());
                        if (!expYearMonth.equals(specificMonth)) continue;
                    }
                    if (specificYear != null && !specificYear.isEmpty()) {
                        if (e.getDate().getYear() != Integer.parseInt(specificYear)) continue;
                    }
                } else {
                    if (e.getDate().isBefore(finalStartDateTime.toLocalDate())) continue;
                }

                double baseCost = e.getAmount();
                double txCost = e.getTransactionCost() != null ? e.getTransactionCost() : 0.0;
                double totalCost = baseCost + txCost;

                totalExpenses += totalCost;

                String mainCategory = getExpenseMainCategory(e.getCategory());
                expMap.put(mainCategory, expMap.getOrDefault(mainCategory, 0.0) + totalCost);
            }
        }

        double netProfit = totalRevenue - totalExpenses;
        double profitMargin = (totalRevenue > 0) ? (netProfit / totalRevenue) * 100 : 0.0;

        // 5. PASS TO MODEL
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalExpenses", totalExpenses); // Fixes the Bar Chart
        model.addAttribute("netProfit", netProfit);
        model.addAttribute("profitMargin", String.format("%.1f", profitMargin));

        model.addAttribute("period", period);
        model.addAttribute("trendTitle", trendTitle);

        // Chart Arrays
        model.addAttribute("dayLabels", trendMap.keySet());
        model.addAttribute("dayData", trendMap.values());
        model.addAttribute("dowLabels", salesByDay.keySet());
        model.addAttribute("dowData", salesByDay.values());
        model.addAttribute("catLabels", catRevenue.keySet());
        model.addAttribute("catData", catRevenue.values());
        model.addAttribute("payLabels", paymentMap.keySet());
        model.addAttribute("payData", paymentMap.values());
        model.addAttribute("expLabels", expMap.keySet());
        model.addAttribute("expData", expMap.values());

        model.addAttribute("sales", filteredSales);

        return "analytics";
    }

    // 1. Hourly Average Calculation for Peak Hours Graph
    @GetMapping("/api/financial/hourly-traffic")
    public ResponseEntity<?> getHourlyTraffic() {
        List<Sale> allSales = saleRepository.findAll();
        LocalDateTime cutoffDate = LocalDateTime.of(2026, 9, 10, 0, 0);

        // Initialize map for 7 AM to 11 PM with nicely formatted labels (e.g., "07:00")
        Map<String, Double> hourlySales = new LinkedHashMap<>();
        for (int i = 7; i <= 23; i++) {
            hourlySales.put(String.format("%02d:00", i), 0.0);
        }

        for (Sale sale : allSales) {
            LocalDateTime saleTime = sale.getSaleDateTime();

            // Enforce the September 10th cutoff
            if (saleTime == null || saleTime.isBefore(cutoffDate)) {
                continue;
            }

            int hour = saleTime.getHour();

            // Only record if between 7 AM and 11 PM
            if (hour >= 7 && hour <= 23) {
                String timeLabel = String.format("%02d:00", hour);
                hourlySales.put(timeLabel, hourlySales.get(timeLabel) + sale.getTotalAmount());
            }
        }

        // Return the correct map!
        return ResponseEntity.ok(hourlySales);
    }
    

    // ==========================================
    // 10. API FOR REAL-TIME NOTIFICATIONS
    // ==========================================
    @GetMapping("/api/check-latest-message")
    @ResponseBody
    public Map<String, Object> checkLatestMessage() {
        Map<String, Object> response = new HashMap<>();

        List<TeamLog> chats = teamLogRepository.findByTypeOrderByTimestampDesc("CHAT");
        if (!chats.isEmpty()) {
            TeamLog last = chats.get(0);
            response.put("id", last.getId());
            response.put("author", last.getAuthor());
        } else {
            response.put("id", 0);
        }
        return response;
    }
}
