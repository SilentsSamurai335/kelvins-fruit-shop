package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.*;

import com.kelvinsfusion.managementsystem.repository.ProductRepository;
import com.kelvinsfusion.managementsystem.repository.SaleRepository;
import com.kelvinsfusion.managementsystem.repository.ExpenseRepository;
import com.kelvinsfusion.managementsystem.repository.TeamLogRepository;
import com.kelvinsfusion.managementsystem.repository.UserRepository;
import java.security.Principal;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.*;

@Controller
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamLogRepository teamLogRepository;

    // --- LOGIN ---
    @GetMapping("/login")
    public String showLoginPage() { return "login"; }

    // --- DASHBOARD (Graphs Only - NO CHAT) ---
    @GetMapping("/")
    public String showLandingPage(Model model, Principal principal) {
        List<Sale> allSales = saleRepository.findAll();

        // 1. Product Mix Data
        Map<String, Integer> productMap = new HashMap<>();
        for (Sale sale : allSales) {
            if (sale.getItemsSold() != null) {
                String raw = sale.getItemsSold();
                String clean = raw.contains(" (") ? raw.substring(0, raw.lastIndexOf(" (")) : raw;
                productMap.put(clean, productMap.getOrDefault(clean, 0) + 1);
            }
        }

        // 2. Trend Data
        Map<LocalDate, Double> trendMap = new TreeMap<>();
        for (Sale sale : allSales) {
            if (sale.getSaleDateTime() != null) {
                LocalDate date = sale.getSaleDateTime().toLocalDate();
                trendMap.put(date, trendMap.getOrDefault(date, 0.0) + sale.getTotalAmount());
            }
        }

        model.addAttribute("productLabels", productMap.keySet());
        model.addAttribute("productData", productMap.values());
        model.addAttribute("trendDates", trendMap.keySet());
        model.addAttribute("trendAmounts", trendMap.values());

        // --- NOTIFICATION LOGIC ---
        // Get the logged-in user
        String currentUser = "";
        if (model.getAttribute("username") != null) {
            // You might need to fetch username from SecurityContext if not in model
            // But for now, let's just pass a flag if ANY chat exists
        }

        // BETTER APPROACH: Add this to the Sidebar Fragment logic or Interceptor
        // For simplicity, we will calculate "HasUnread" on the Dashboard

        List<TeamLog> allChats = teamLogRepository.findByTypeOrderByTimestampDesc("CHAT");
        boolean hasNewMessage = false;

        if (!allChats.isEmpty()) {
            TeamLog lastMsg = allChats.get(0);
            // If the last message was NOT written by me, it's "New" for me
            // Note: We need the current user's name.
            // In a real app, use Principal. For now, we will just show the dot if there is recent activity (last 24 hours).
            if (lastMsg.getTimestamp().isAfter(LocalDateTime.now().minusHours(24))) {
                hasNewMessage = true;
            }
        }
        model.addAttribute("hasNewMessage", hasNewMessage);

        if (principal != null && !allChats.isEmpty()) {
            TeamLog lastMsg = allChats.get(0);
            if (!lastMsg.getAuthor().equals(principal.getName())) {
                hasNewMessage = true; // Someone else wrote the last message!
            }
        }

        return "index";
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

    @GetMapping("/sell/{id}")
    public String sellProduct(@PathVariable("id") Long id,
                              @RequestParam(value = "size", required = false) String size,
                              @RequestParam(value = "qty", defaultValue = "1") int qty,
                              @RequestParam(value = "payment", defaultValue = "Cash") String payment,
                              @RequestParam(value = "customPrice", required = false) Double customPrice,
                              @RequestParam(value = "customSizeName", required = false) String customSizeName) {

        Product product = productRepository.findById(id).orElse(null);
        if (product != null) {
            Sale newSale = new Sale();
            newSale.setSaleDateTime(LocalDateTime.now());
            newSale.setQuantity(qty);
            newSale.setPaymentMethod(payment);

            if ("Beverage".equals(product.getCategory())) {
                double price = 0.0;
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

    // --- SALES HISTORY ---
    // --- SALES HISTORY ---
    @GetMapping("/sales") // or "/sales-history" depending on your setup
    public String showSales(@RequestParam(value = "period", required = false) String period,
                            @RequestParam(value = "month", required = false) Integer month,
                            @RequestParam(value = "year", required = false) Integer year,
                            @RequestParam(value = "date", required = false) java.time.LocalDate specificDate,
                            @RequestParam(value = "paymentMethod", required = false, defaultValue = "ALL") String paymentMethod, // NEW PARAM
                            Model model) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusYears(100);
        LocalDateTime end = now;
        String title = "All Time Sales";

        // 1. DATE LOGIC (Your existing logic)
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

        // 2. FETCH DATA BASED ON PAYMENT METHOD
        List<Sale> sales;
        if (paymentMethod.equals("ALL")) {
            // Show everything for that date range
            sales = saleRepository.findBySaleDateTimeBetween(start, end);
        } else {
            // Filter by Date AND Payment (e.g., only MPESA for Today)
            sales = saleRepository.findBySaleDateTimeBetweenAndPaymentMethod(start, end, paymentMethod);
            title += " (" + paymentMethod + ")";
        }

        double totalRevenue = sales.stream().mapToDouble(Sale::getTotalAmount).sum();

        // 3. ADD ATTRIBUTES
        model.addAttribute("listSales", sales);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("periodTitle", title);

        // Keep inputs filled
        model.addAttribute("selectedDate", specificDate);
        model.addAttribute("selectedMonth", (month != null) ? month : now.getMonthValue());
        model.addAttribute("selectedYear", (year != null) ? year : now.getYear());
        model.addAttribute("selectedPayment", paymentMethod); // To keep dropdown selected

        return "sales"; // Make sure your HTML file is named 'sales.html' (or 'sales-history.html')
    }

    // --- EXPENSES ---
    @GetMapping("/expenses")
    public String showExpenses(@RequestParam(value = "period", required = false) String period,
                               @RequestParam(value = "month", required = false) Integer month,
                               @RequestParam(value = "year", required = false) Integer year,
                               @RequestParam(value = "category", required = false) String category,
                               Model model) {
        LocalDate now = LocalDate.now();
        LocalDate start = now.minusYears(100);
        LocalDate end = now;
        String title = "All Time";

        if (month != null && year != null) {
            start = LocalDate.of(year, month, 1);
            end = start.plusMonths(1).minusDays(1);
            title = Month.of(month).name() + " " + year;
        } else if ("today".equals(period)) {
            start = now;
        } else if ("week".equals(period)) {
            start = now.minusDays(now.getDayOfWeek().getValue() - 1);
        } else if ("month".equals(period)) {
            start = now.withDayOfMonth(1);
        }

        List<Expense> expenses = expenseRepository.findByDateBetween(start, end);

        if (category != null && !category.isEmpty() && !"ALL".equals(category)) {
            List<String> util = Arrays.asList("Rent", "Gas", "Tap Water", "Garbage", "Token");
            List<String> ingr = Arrays.asList("Juice Ingredients", "Uji Ingredients", "Snacks", "Spices", "Packaging", "Kitchen Utilities", "Sugar");
            List<String> ops = Arrays.asList("Transport", "Lunch", "Maintenance", "Honorarium");

            if ("GROUP_UTILITIES".equals(category)) expenses = expenses.stream().filter(e -> util.contains(e.getCategory())).toList();
            else if ("GROUP_INGREDIENTS".equals(category)) expenses = expenses.stream().filter(e -> ingr.contains(e.getCategory())).toList();
            else if ("GROUP_OPERATIONS".equals(category)) expenses = expenses.stream().filter(e -> ops.contains(e.getCategory())).toList();
            else {
                String cat = category;
                expenses = expenses.stream().filter(e -> cat.equals(e.getCategory())).toList();
            }
        }

        double total = expenses.stream().mapToDouble(Expense::getAmount).sum();
        model.addAttribute("listExpenses", expenses);
        model.addAttribute("newExpense", new Expense());
        model.addAttribute("totalExpense", total);
        model.addAttribute("periodTitle", title);
        model.addAttribute("selectedMonth", (month != null) ? month : now.getMonthValue());
        model.addAttribute("selectedYear", (year != null) ? year : now.getYear());
        return "expenses";
    }

    @PostMapping("/save-expense")
    public String saveExpense(Expense expense) {
        if (expense.getDate() == null) expense.setDate(LocalDate.now());
        if (expense.getCategory() == null) expense.setCategory("Other");
        expenseRepository.save(expense);
        return "redirect:/expenses";
    }

    // --- INCOME STATEMENT ---
    // --- INCOME STATEMENT (High Precision) ---
    @GetMapping("/income-statement")
    public String showIncomeStatement(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            Model model) {

        LocalDate now = LocalDate.now();
        int curMonth = (month != null) ? month : now.getMonthValue();
        int curYear = (year != null) ? year : now.getYear();
        LocalDate start = LocalDate.of(curYear, curMonth, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        String title = Month.of(curMonth).name() + " " + curYear;

        // 1. REVENUE BREAKDOWN
        List<Sale> sales = saleRepository.findAll();

        // Buckets
        double juiceCash = 0, juiceMpesa = 0;
        double ujiCash = 0, ujiMpesa = 0;
        double cakeCash = 0, cakeMpesa = 0;   // Cupcakes
        double cookCash = 0, cookMpesa = 0;   // Cookies
        double spiceCash = 0, spiceMpesa = 0; // Spices
        double snackCash = 0, snackMpesa = 0; // Other Snacks (Samosa, Smokie)

        for (Sale s : sales) {
            if (s.getSaleDateTime() != null) {
                LocalDate d = s.getSaleDateTime().toLocalDate();
                if (!d.isBefore(start) && !d.isAfter(end)) {
                    String item = (s.getItemsSold() != null) ? s.getItemsSold().toLowerCase() : "";
                    double amt = s.getTotalAmount();
                    boolean isMp = "Mpesa".equalsIgnoreCase(s.getPaymentMethod());

                    // LOGIC: Check name to categorize
                    if (item.contains("uji")) {
                        if (isMp) ujiMpesa += amt; else ujiCash += amt;
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
                        // Default to Juice
                        if (isMp) juiceMpesa += amt; else juiceCash += amt;
                    }
                }
            }
        }
        double totalSales = juiceCash + juiceMpesa + ujiCash + ujiMpesa + cakeCash + cakeMpesa + cookCash + cookMpesa + spiceCash + spiceMpesa + snackCash + snackMpesa;

        // 2. EXPENSE BREAKDOWN
        List<Expense> exps = expenseRepository.findByDateBetween(start, end);
        List<Expense> cJuice = new ArrayList<>(), cUji = new ArrayList<>(), cShared = new ArrayList<>(), eOps = new ArrayList<>();

        for (Expense e : exps) {
            String c = (e.getCategory() != null) ? e.getCategory().trim() : "Other";

            // Bucket Logic
            if ("Juice Ingredients".equalsIgnoreCase(c)) {
                cJuice.add(e);
            }
            else if ("Uji Ingredients".equalsIgnoreCase(c)) {
                cUji.add(e);
            }
            else if ("Sugar".equalsIgnoreCase(c) || "Spices".equalsIgnoreCase(c) || "Shared".equalsIgnoreCase(c)) {
                cShared.add(e);
            }
            else {
                // SAFETY NET: Anything that doesn't match above goes here!
                // (Rent, Transport, Tokens, Unknowns...)
                eOps.add(e);
            }
        }

        double tCogs = cJuice.stream().mapToDouble(Expense::getAmount).sum() + cUji.stream().mapToDouble(Expense::getAmount).sum() + cShared.stream().mapToDouble(Expense::getAmount).sum();
        double tOps = eOps.stream().mapToDouble(Expense::getAmount).sum();
        double gross = totalSales - tCogs;
        double net = gross - tOps;

        model.addAttribute("periodTitle", title);

        // Send ALL Buckets
        model.addAttribute("juiceCash", juiceCash); model.addAttribute("juiceMpesa", juiceMpesa);
        model.addAttribute("ujiCash", ujiCash); model.addAttribute("ujiMpesa", ujiMpesa);
        model.addAttribute("cakeCash", cakeCash); model.addAttribute("cakeMpesa", cakeMpesa);
        model.addAttribute("cookCash", cookCash); model.addAttribute("cookMpesa", cookMpesa);
        model.addAttribute("spiceCash", spiceCash); model.addAttribute("spiceMpesa", spiceMpesa);
        model.addAttribute("snackCash", snackCash); model.addAttribute("snackMpesa", snackMpesa);

        model.addAttribute("totalSales", totalSales);
        model.addAttribute("cogsJuice", cJuice); model.addAttribute("cogsUji", cUji); model.addAttribute("cogsShared", cShared);
        model.addAttribute("expOps", eOps);
        model.addAttribute("totalCogs", tCogs); model.addAttribute("totalOps", tOps);
        model.addAttribute("grossProfit", gross); model.addAttribute("netProfit", net);
        model.addAttribute("selectedMonth", curMonth); model.addAttribute("selectedYear", curYear);

        return "income-statement";
    }

    // ==========================================
    // 7. MANAGE STAFF (Admin Only)
    // ==========================================

    @GetMapping("/users")
    public String showUsers(Model model) {
        model.addAttribute("listUsers", userRepository.findAll());
        model.addAttribute("newUser", new com.kelvinsfusion.managementsystem.model.User());
        return "users";
    }

    @PostMapping("/save-user")
    public String saveUser(com.kelvinsfusion.managementsystem.model.User user) {
        // Force role to STAFF (Admins can only create Staff)
        user.setRole("STAFF");
        userRepository.save(user);
        return "redirect:/users";
    }

    @GetMapping("/delete-user/{id}")
    public String deleteUser(@PathVariable("id") Long id) {
        userRepository.deleteById(id);
        return "redirect:/users";
    }

    // ==========================================
    // 7. TEAM HUB & CHAT (Simplified Logic)
    // ==========================================

    @GetMapping("/team")
    public String showTeamHub(@RequestParam(value = "staff", required = false) String staffUsername,
                              Model model,
                              Principal principal) {

        String currentUser = principal.getName();

        // 1. Notices
        model.addAttribute("notices", teamLogRepository.findByTypeOrderByTimestampDesc("NOTICE"));

        List<TeamLog> chats = new ArrayList<>();
        String recipientForForm = ""; // We will calculate who the form sends to

        // CASE A: ADMIN LOGGED IN
        if ("admin".equals(currentUser)) {
            List<com.kelvinsfusion.managementsystem.model.User> staffList = userRepository.findAll();
            staffList.removeIf(u -> "admin".equals(u.getUsername()));
            model.addAttribute("staffList", staffList);

            if (staffUsername != null && !staffUsername.isEmpty()) {
                // Admin selected someone
                chats = teamLogRepository.findChatHistory("admin", staffUsername);
                model.addAttribute("chatTarget", staffUsername);
                recipientForForm = staffUsername; // Admin sends to Selected Staff
            } else {
                model.addAttribute("chatTarget", "Select a Staff Member");
                recipientForForm = ""; // No target yet
            }
        }

        // CASE B: STAFF LOGGED IN
        else {
            // Staff always talks to Admin
            chats = teamLogRepository.findChatHistory(currentUser, "admin");
            model.addAttribute("chatTarget", "Manager (Admin)");
            recipientForForm = "admin"; // Staff sends to Admin
        }

        model.addAttribute("chats", chats);
        model.addAttribute("currentRecipient", recipientForForm); // Send this to HTML

        return "team-hub";
    }

    @PostMapping("/save-chat")
    public String saveChat(@RequestParam("content") String content,
                           @RequestParam("recipient") String recipient,
                           Principal principal) {

        // Safety Check: Don't save if no recipient
        if (recipient == null || recipient.isEmpty()) {
            return "redirect:/team";
        }

        TeamLog log = new TeamLog();
        log.setContent(content);
        log.setType("CHAT");
        log.setAuthor(principal.getName());
        log.setRecipient(recipient);

        teamLogRepository.save(log);

        if ("admin".equals(principal.getName())) {
            return "redirect:/team?staff=" + recipient;
        } else {
            return "redirect:/team";
        }
    }


    @PostMapping("/save-update")
    public String saveUpdate(@RequestParam("subject") String subject, // NEW PARAMETER
                             @RequestParam("content") String content,
                             Principal principal) {

        // Use full class path to avoid import errors
        com.kelvinsfusion.managementsystem.model.TeamLog log = new com.kelvinsfusion.managementsystem.model.TeamLog();

        log.setSubject(subject); // Set Subject
        log.setContent(content);
        log.setType("NOTICE");
        log.setAuthor(principal != null ? principal.getName() : "Unknown");

        teamLogRepository.save(log);
        return "redirect:/team";
    }


    @GetMapping("/edit-user/{id}")
    public String showUpdateForm(@PathVariable("id") Integer id, Model model) {
        // 1. Find the user by ID
        User user = userRepository.findById(Long.valueOf(id))
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));

        // 2. Add user to model so the form is pre-filled
        model.addAttribute("user", user);

        // 3. Return the registration/edit HTML file name
        // (Assuming you reuse your 'signup' or 'add-staff' form)
        return "add-staff";
    }

    @GetMapping("/api/check-latest-message")
    @ResponseBody
    public Map<String, Object> checkLatestMessage() {
        Map<String, Object> response = new HashMap<>();

        // 1. Get the very last message sent to admin
        // Note: Assuming "admin" is the username. If yours is different, change it here.
        TeamLog lastMsg = teamLogRepository.findTopByRecipientOrderByTimestampDesc("admin");

        if (lastMsg != null) {
            response.put("id", lastMsg.getId());
            response.put("sender", lastMsg.getAuthor()); // Or getSender() depending on your model
            response.put("content", lastMsg.getContent()); // Optional: to show preview
        } else {
            response.put("id", 0);
        }

        return response;
    }
}