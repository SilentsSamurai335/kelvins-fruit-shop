package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.*;
import com.kelvinsfusion.managementsystem.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

@Controller
public class ProductController {

    @Autowired private ProductRepository productRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamLogRepository teamLogRepository;

    // --- LOGIN & DASHBOARD ---
    @GetMapping("/login")
    public String showLoginPage() { return "login"; }

    @GetMapping("/")
    public String showLandingPage(Model model, Principal principal) {
        List<Sale> allSales = saleRepository.findAll();

        Map<String, Integer> productMap = new HashMap<>();
        Map<LocalDate, Double> trendMap = new TreeMap<>();

        for (Sale sale : allSales) {
            if (sale.getItemsSold() != null) {
                String raw = sale.getItemsSold();
                String clean = raw.contains(" (") ? raw.substring(0, raw.lastIndexOf(" (")) : raw;
                productMap.put(clean, productMap.getOrDefault(clean, 0) + 1);
            }
            if (sale.getSaleDateTime() != null) {
                LocalDate date = sale.getSaleDateTime().toLocalDate();
                trendMap.put(date, trendMap.getOrDefault(date, 0.0) + sale.getTotalAmount());
            }
        }

        model.addAttribute("productLabels", productMap.keySet());
        model.addAttribute("productData", productMap.values());
        model.addAttribute("trendDates", trendMap.keySet());
        model.addAttribute("trendAmounts", trendMap.values());

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

    // --- POS / SELLING ---
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
    @GetMapping("/sales")
    public String showSales(@RequestParam(value = "period", required = false) String period,
                            @RequestParam(value = "month", required = false) Integer month,
                            @RequestParam(value = "year", required = false) Integer year,
                            @RequestParam(value = "date", required = false) java.time.LocalDate specificDate,
                            @RequestParam(value = "paymentMethod", required = false, defaultValue = "ALL") String paymentMethod,
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

        List<Sale> sales;
        if (paymentMethod.equals("ALL")) {
            sales = saleRepository.findBySaleDateTimeBetween(start, end);
        } else {
            sales = saleRepository.findBySaleDateTimeBetweenAndPaymentMethod(start, end, paymentMethod);
            title += " (" + paymentMethod + ")";
        }

        double totalRevenue = sales.stream().mapToDouble(Sale::getTotalAmount).sum();
        model.addAttribute("listSales", sales);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("periodTitle", title);
        model.addAttribute("selectedDate", specificDate);
        model.addAttribute("selectedMonth", (month != null) ? month : now.getMonthValue());
        model.addAttribute("selectedYear", (year != null) ? year : now.getYear());
        model.addAttribute("selectedPayment", paymentMethod);

        return "sales";
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
            if ("GROUP_UTILITIES".equals(category)) {
                expenses = expenses.stream().filter(e -> Arrays.asList("Rent", "Gas", "Tap Water", "Garbage", "Token").contains(e.getCategory())).toList();
            } else if ("GROUP_OPERATIONS".equals(category)) {
                expenses = expenses.stream().filter(e -> Arrays.asList("Transport", "Lunch", "Maintenance").contains(e.getCategory())).toList();
            } else {
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
        expenseRepository.save(expense);
        return "redirect:/expenses";
    }

    // --- INCOME STATEMENT (FIXED: Added Calculations back) ---
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
        double ujiCash = 0, ujiMpesa = 0;
        double cakeCash = 0, cakeMpesa = 0;
        double cookCash = 0, cookMpesa = 0;
        double spiceCash = 0, spiceMpesa = 0;
        double snackCash = 0, snackMpesa = 0;

        for (Sale s : sales) {
            if (s.getSaleDateTime() != null) {
                LocalDate d = s.getSaleDateTime().toLocalDate();
                if (!d.isBefore(start) && !d.isAfter(end)) {
                    String item = (s.getItemsSold() != null) ? s.getItemsSold().toLowerCase() : "";
                    double amt = s.getTotalAmount();
                    boolean isMp = "Mpesa".equalsIgnoreCase(s.getPaymentMethod());

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
                        if (isMp) juiceMpesa += amt; else juiceCash += amt;
                    }
                }
            }
        }
        double totalSales = juiceCash + juiceMpesa + ujiCash + ujiMpesa + cakeCash + cakeMpesa + cookCash + cookMpesa + spiceCash + spiceMpesa + snackCash + snackMpesa;

        // 2. EXPENSE CALCULATIONS
        List<Expense> exps = expenseRepository.findByDateBetween(start, end);
        List<Expense> cJuice = new ArrayList<>(), cUji = new ArrayList<>(), cShared = new ArrayList<>(), eOps = new ArrayList<>();

        for (Expense e : exps) {
            String c = (e.getCategory() != null) ? e.getCategory().trim() : "Other";
            if ("Juice Ingredients".equalsIgnoreCase(c)) cJuice.add(e);
            else if ("Uji Ingredients".equalsIgnoreCase(c)) cUji.add(e);
            else if ("Sugar".equalsIgnoreCase(c) || "Spices".equalsIgnoreCase(c) || "Shared".equalsIgnoreCase(c)) cShared.add(e);
            else eOps.add(e);
        }

        double tCogs = cJuice.stream().mapToDouble(Expense::getAmount).sum() + cUji.stream().mapToDouble(Expense::getAmount).sum() + cShared.stream().mapToDouble(Expense::getAmount).sum();
        double tOps = eOps.stream().mapToDouble(Expense::getAmount).sum();
        double gross = totalSales - tCogs;
        double net = gross - tOps;

        model.addAttribute("periodTitle", Month.of(curMonth).name() + " " + curYear);
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

        // Admin Logic
        if ("admin".equals(currentUser)) {
            List<User> staffList = userRepository.findAll();
            staffList.removeIf(u -> "admin".equals(u.getUsername()));
            model.addAttribute("staffList", staffList);

            if (staffUsername != null && !staffUsername.isEmpty()) {
                chats = teamLogRepository.findChatHistory("admin", staffUsername);
                model.addAttribute("chatTarget", staffUsername);
                recipientForForm = staffUsername;
            } else {
                model.addAttribute("chatTarget", "Select a Staff Member");
            }
        }
        // Staff Logic
        else {
            chats = teamLogRepository.findChatHistory(currentUser, "admin");
            model.addAttribute("chatTarget", "Manager (Admin)");
            recipientForForm = "admin";
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
        model.addAttribute("listUsers", userRepository.findAll());
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
    @GetMapping("/analytics")
    public String showAnalytics(@RequestParam(value = "period", defaultValue = "monthly") String period,
                                Model model,
                                Principal principal) { // Added Principal

        // SECURITY CHECK: Redirect if not admin
        if (!"admin".equals(principal.getName())) {
            return "redirect:/";
        }

        // ... (Keep the rest of your existing analytics logic exactly the same) ...

        LocalDate today = LocalDate.now();
        List<String> labels = new ArrayList<>();
        List<Double> trendData = new ArrayList<>();

        // Stacked Bar Data Lists
        List<Double> stackJuice = new ArrayList<>();
        List<Double> stackUji = new ArrayList<>();
        List<Double> stackCake = new ArrayList<>();
        List<Double> stackCookie = new ArrayList<>();

        // 1. DETERMINE DATE RANGE & GRANULARITY
        LocalDate start = today;
        LocalDate end = today;

        if ("daily".equals(period)) {
            start = today.minusDays(29); // Last 30 Days
            // Loop day by day
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                labels.add(date.getDayOfMonth() + "/" + date.getMonthValue()); // "22/1"
            }
        }
        else if ("weekly".equals(period)) {
            start = today.minusWeeks(11); // Last 12 Weeks
            // Loop week by week
            for (int i = 0; i < 12; i++) {
                labels.add("W" + (11-i)); // "W11", "W10"...
            }
        }
        else if ("annually".equals(period)) {
            start = today.minusYears(4).withDayOfYear(1); // Last 5 Years
            // Loop year by year
            for (int i = 0; i < 5; i++) {
                labels.add(String.valueOf(start.getYear() + i));
            }
        }
        else if ("all".equals(period)) {
            // Find very first sale
            Sale firstSale = saleRepository.findFirstByOrderBySaleDateTimeAsc();
            start = (firstSale != null) ? firstSale.getSaleDateTime().toLocalDate() : today.minusMonths(1);
            // Default to Yearly view for All Time
            int startYear = start.getYear();
            int endYear = today.getYear();
            labels.clear();
            for(int y = startYear; y <= endYear; y++) labels.add(String.valueOf(y));
        }
        else {
            // DEFAULT: MONTHLY (This Year)
            start = LocalDate.of(today.getYear(), 1, 1);
            end = LocalDate.of(today.getYear(), 12, 31);
            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            labels.addAll(Arrays.asList(months));
        }

        // 2. FETCH DATA & AGGREGATE
        List<Sale> sales = saleRepository.findBySaleDateTimeBetween(start.atStartOfDay(), end.atTime(23, 59, 59));

        // Initialize Data Lists with 0.0 based on labels size
        for(int i=0; i<labels.size(); i++) {
            trendData.add(0.0); stackJuice.add(0.0); stackUji.add(0.0); stackCake.add(0.0); stackCookie.add(0.0);
        }

        // 3. CATEGORY & PAYMENT MAPS (For Pie Charts)
        Map<String, Double> catRevenue = new HashMap<>();
        Map<String, Double> paymentMap = new HashMap<>();
        // Table Data
        Map<String, Map<String, Double>> detailedTable = new HashMap<>();
        String[] cats = {"Juices", "Uji", "Cupcakes", "Cookies", "Other"};
        for(String c : cats) {
            detailedTable.put(c, new HashMap<>());
            detailedTable.get(c).put("Cash", 0.0);
            detailedTable.get(c).put("Mpesa", 0.0);
        }

        for (Sale s : sales) {
            LocalDateTime dt = s.getSaleDateTime();
            String item = (s.getItemsSold() != null) ? s.getItemsSold().toLowerCase() : "";
            double amt = s.getTotalAmount();
            String pay = s.getPaymentMethod();

            // A. Categorize
            String cat = "Other";
            if (item.contains("juice") || item.contains("passion") || item.contains("mango")) cat = "Juices";
            else if (item.contains("uji")) cat = "Uji";
            else if (item.contains("cupcake") || item.contains("cake")) cat = "Cupcakes";
            else if (item.contains("cookie") || item.contains("biscuit")) cat = "Cookies";

            // B. Fill Pie Charts & Table
            catRevenue.put(cat, catRevenue.getOrDefault(cat, 0.0) + amt);
            paymentMap.put(pay, paymentMap.getOrDefault(pay, 0.0) + amt);

            if(detailedTable.containsKey(cat)) {
                String pKey = "Mpesa".equalsIgnoreCase(pay) ? "Mpesa" : "Cash";
                detailedTable.get(cat).put(pKey, detailedTable.get(cat).get(pKey) + amt);
            }

            // C. Map to Graph Index
            int index = -1;
            if ("daily".equals(period)) {
                // Find index matching day/month. Simple logic: Days from start
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, dt.toLocalDate());
                if (daysBetween >= 0 && daysBetween < labels.size()) index = (int) daysBetween;
            }
            else if ("weekly".equals(period)) {
                // Map to week bucket (simplified)
                long weeksBetween = java.time.temporal.ChronoUnit.WEEKS.between(start, dt.toLocalDate());
                if (weeksBetween >= 0 && weeksBetween < labels.size()) index = (int) weeksBetween;
            }
            else if ("annually".equals(period) || "all".equals(period)) {
                // Map to Year
                int yearDiff = dt.getYear() - start.getYear();
                if (yearDiff >= 0 && yearDiff < labels.size()) index = yearDiff;
            }
            else {
                // Monthly
                index = dt.getMonthValue() - 1;
            }

            // D. Add to Stacked Arrays
            if (index >= 0 && index < labels.size()) {
                trendData.set(index, trendData.get(index) + amt);

                if (cat.equals("Juices")) stackJuice.set(index, stackJuice.get(index) + amt);
                else if (cat.equals("Uji")) stackUji.set(index, stackUji.get(index) + amt);
                else if (cat.equals("Cupcakes")) stackCake.set(index, stackCake.get(index) + amt);
                else if (cat.equals("Cookies")) stackCookie.set(index, stackCookie.get(index) + amt);
            }
        }

        double totalRevenue = sales.stream().mapToDouble(Sale::getTotalAmount).sum();

        model.addAttribute("period", period);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("labels", labels);
        model.addAttribute("trendData", trendData);
        model.addAttribute("catLabels", catRevenue.keySet());
        model.addAttribute("catData", catRevenue.values());
        model.addAttribute("payLabels", paymentMap.keySet());
        model.addAttribute("payData", paymentMap.values());
        model.addAttribute("stackJuice", stackJuice);
        model.addAttribute("stackUji", stackUji);
        model.addAttribute("stackCake", stackCake);
        model.addAttribute("stackCookie", stackCookie);
        model.addAttribute("detailedTable", detailedTable);

        return "analytics";
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