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

import java.util.List;

@Controller
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired // <--- Add this
    private com.kelvinsfusion.managementsystem.repository.SaleRepository saleRepository;

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
        // 1. Fetch all sales
        List<Sale> allSales = saleRepository.findAll();

        // 2. Calculate the count for each juice (e.g., Mango -> 5, Passion -> 2)
        // We use a "Map" which is like a dictionary
        java.util.Map<String, Integer> salesData = new java.util.HashMap<>();

        for (com.kelvinsfusion.managementsystem.model.Sale sale : allSales) {
            String juiceName = sale.getItemsSold();
            salesData.put(juiceName, salesData.getOrDefault(juiceName, 0) + 1);
        }

        // 3. Separate the data into two lists for the Chart (Labels vs Numbers)
        model.addAttribute("chartLabels", salesData.keySet()); // ["Mango", "Passion"]
        model.addAttribute("chartData", salesData.values());   // [5, 2]

        return "index";
    }
    // 5. Show the Edit Form (Reuses add-product.html but fills it with data)
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product Id:" + id));

        model.addAttribute("product", product);
        return "add-product";
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
}