package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.Product;
import com.kelvinsfusion.managementsystem.model.Sale;
import com.kelvinsfusion.managementsystem.model.Expense;

import com.kelvinsfusion.managementsystem.repository.ProductRepository;
import com.kelvinsfusion.managementsystem.repository.SaleRepository;
import com.kelvinsfusion.managementsystem.repository.ExpenseRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

@Controller
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    // --- LOGIN ---
    @GetMapping("/login")
    public String showLoginPage() { return "login"; }

    // --- DASHBOARD (Graphs Only - NO CHAT) ---
    @GetMapping("/")
    public String showLandingPage(Model model) {
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
    @GetMapping("/sales")
    public String showSales(@RequestParam(value = "period", required = false) String period,
                            @RequestParam(value = "month", required = false) Integer month,
                            @RequestParam(value = "year", required = false) Integer year,
                            Model model) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusYears(100);
        LocalDateTime end = now;
        String title = "All Time Sales";

        if (month != null && year != null) {
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

        List<Sale> sales = saleRepository.findBySaleDateTimeBetween(start, end);
        double totalRevenue = sales.stream().mapToDouble(Sale::getTotalAmount).sum();

        model.addAttribute("listSales", sales);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("periodTitle", title);
        model.addAttribute("selectedMonth", (month != null) ? month : now.getMonthValue());
        model.addAttribute("selectedYear", (year != null) ? year : now.getYear());
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
    @GetMapping("/income-statement")
    public String showIncomeStatement(@RequestParam(value = "month", required = false) Integer month,
                                      @RequestParam(value = "year", required = false) Integer year,
                                      Model model) {
        LocalDate now = LocalDate.now();
        int curMonth = (month != null) ? month : now.getMonthValue();
        int curYear = (year != null) ? year : now.getYear();
        LocalDate start = LocalDate.of(curYear, curMonth, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        String title = Month.of(curMonth).name() + " " + curYear;

        // Revenue
        List<Sale> sales = saleRepository.findAll();
        double jCash = 0, jMp = 0, uCash = 0, uMp = 0, sCash = 0, sMp = 0;

        for (Sale s : sales) {
            if (s.getSaleDateTime() != null) {
                LocalDate d = s.getSaleDateTime().toLocalDate();
                if (!d.isBefore(start) && !d.isAfter(end)) {
                    String i = (s.getItemsSold() != null) ? s.getItemsSold().toLowerCase() : "";
                    double a = s.getTotalAmount();
                    boolean isMp = "Mpesa".equalsIgnoreCase(s.getPaymentMethod());

                    if (i.contains("uji")) { if (isMp) uMp += a; else uCash += a; }
                    else if (i.contains("cake") || i.contains("cookie") || i.contains("samosa") || i.contains("smokie") || i.contains("snack")) { if (isMp) sMp += a; else sCash += a; }
                    else { if (isMp) jMp += a; else jCash += a; }
                }
            }
        }
        double totalRev = jCash + jMp + uCash + uMp + sCash + sMp;

        // Expenses
        List<Expense> exps = expenseRepository.findByDateBetween(start, end);
        List<Expense> cJuice = new ArrayList<>(), cUji = new ArrayList<>(), cShared = new ArrayList<>(), eOps = new ArrayList<>();

        for (Expense e : exps) {
            String c = e.getCategory();
            if ("Juice Ingredients".equals(c)) cJuice.add(e);
            else if ("Uji Ingredients".equals(c)) cUji.add(e);
            else if ("Sugar".equals(c) || "Spices".equals(c)) cShared.add(e);
            else eOps.add(e);
        }

        double tCogs = cJuice.stream().mapToDouble(Expense::getAmount).sum() + cUji.stream().mapToDouble(Expense::getAmount).sum() + cShared.stream().mapToDouble(Expense::getAmount).sum();
        double tOps = eOps.stream().mapToDouble(Expense::getAmount).sum();
        double gross = totalRev - tCogs;
        double net = gross - tOps;

        model.addAttribute("periodTitle", title);
        model.addAttribute("juiceCash", jCash); model.addAttribute("juiceMpesa", jMp);
        model.addAttribute("ujiCash", uCash); model.addAttribute("ujiMpesa", uMp);
        model.addAttribute("snackCash", sCash); model.addAttribute("snackMpesa", sMp);
        model.addAttribute("totalSales", totalRev);
        model.addAttribute("cogsJuice", cJuice); model.addAttribute("cogsUji", cUji); model.addAttribute("cogsShared", cShared);
        model.addAttribute("expOps", eOps);
        model.addAttribute("totalCogs", tCogs); model.addAttribute("totalOps", tOps);
        model.addAttribute("grossProfit", gross); model.addAttribute("netProfit", net);
        model.addAttribute("selectedMonth", curMonth); model.addAttribute("selectedYear", curYear);

        return "income-statement";
    }
}