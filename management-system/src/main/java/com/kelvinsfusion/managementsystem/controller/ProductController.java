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
                              @RequestParam("size") String size) { // <--- New: Reads the dropdown (250ml/500ml)

        Product product = productRepository.findById(id).orElse(null);

        if (product != null) {
            Sale newSale = new Sale();
            newSale.setItemsSold(product.getName() + " (" + size + ")");
            newSale.setSaleDateTime(java.time.LocalDateTime.now()); // Ensure time is set

            // Logic: Check which size was chosen -> Deduct correct stock -> Set correct price
            if (size.equals("250ml")) {
                if (product.getStockSmall() > 0) {
                    newSale.setTotalAmount(product.getPriceSmall());
                    product.setStockSmall(product.getStockSmall() - 1);
                    saleRepository.save(newSale);
                    productRepository.save(product);
                }
            } else if (size.equals("500ml")) {
                if (product.getStockLarge() > 0) {
                    newSale.setTotalAmount(product.getPriceLarge());
                    product.setStockLarge(product.getStockLarge() - 1);
                    saleRepository.save(newSale);
                    productRepository.save(product);
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
    public String showExpenses(Model model) {
        model.addAttribute("listExpenses", expenseRepository.findAll());
        model.addAttribute("newExpense", new com.kelvinsfusion.managementsystem.model.Expense());
        return "expenses"; // This is the HTML file we will make next
    }

    // 2. Save a new Expense
    @PostMapping("/save-expense")
    public String saveExpense(com.kelvinsfusion.managementsystem.model.Expense expense) {
        expenseRepository.save(expense);
        return "redirect:/expenses";
    }
}