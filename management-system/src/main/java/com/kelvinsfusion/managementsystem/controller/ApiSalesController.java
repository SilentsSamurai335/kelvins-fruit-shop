package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.Product;
import com.kelvinsfusion.managementsystem.model.Sale;
import com.kelvinsfusion.managementsystem.repository.ProductRepository;
import com.kelvinsfusion.managementsystem.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/sales")
public class ApiSalesController {

    @Autowired private ProductRepository productRepository;
    @Autowired private SaleRepository saleRepository;
    // We don't even need MpesaService here anymore!

    // --- REMOVED THE STK-PUSH METHOD ---

    // ONLY THIS METHOD REMAINS
    @PostMapping("/save")
    public ResponseEntity<?> saveSale(@RequestBody SaleRequest request) {
        List<String> sold = new ArrayList<>();

        if (request.getItems() != null) {
            for (CartItem item : request.getItems()) {
                Product product = productRepository.findById(item.getId()).orElse(null);
                if (product != null) {
                    // Logic to reduce stock for Non-Beverages
                    if (!"Beverage".equalsIgnoreCase(product.getCategory())) {
                        if(product.getStockSmall() < item.getQty()) {
                            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Out of stock: " + product.getName()));
                        }
                        product.setStockSmall(product.getStockSmall() - item.getQty());
                        productRepository.save(product);
                    }

                    // Create and Save Sale Record
                    Sale sale = new Sale();
                    sale.setItemsSold(item.getName());
                    sale.setQuantity(item.getQty());
                    sale.setTotalAmount(item.getPrice() * item.getQty());
                    sale.setPaymentMethod(request.getPaymentMethod()); // Saves "CASH" or "MPESA"
                    sale.setSaleDateTime(LocalDateTime.now());

                    if(request.getPhoneNumber() != null) {
                        sale.setPhoneNumber(request.getPhoneNumber());
                    }

                    saleRepository.save(sale);
                    sold.add(item.getName());
                }
            }
        }
        return ResponseEntity.ok(Collections.singletonMap("message", "Sale Recorded Successfully"));
    }

    // INTERNAL DTO CLASSES (Keep these)
    public static class SaleRequest {
        private String paymentMethod;
        private String phoneNumber;
        private Double amount;
        private List<CartItem> items;
        // Getters & Setters...
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String p) { this.paymentMethod = p; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String p) { this.phoneNumber = p; }
        public Double getAmount() { return amount; }
        public void setAmount(Double a) { this.amount = a; }
        public List<CartItem> getItems() { return items; }
        public void setItems(List<CartItem> i) { this.items = i; }
    }

    public static class CartItem {
        private Long id;
        private String name;
        private int qty;
        private Double price;
        // Getters & Setters...
        public Long getId() { return id; }
        public void setId(Long i) { this.id = i; }
        public String getName() { return name; }
        public void setName(String n) { this.name = n; }
        public int getQty() { return qty; }
        public void setQty(int q) { this.qty = q; }
        public Double getPrice() { return price; }
        public void setPrice(Double p) { this.price = p; }
    }
}