package com.kelvinsfusion.managementsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemsSold; // We will store names like "Mango, Passion"
    private double totalAmount;
    private LocalDateTime saleDateTime;

    // --- Constructor ---
    public Sale() {
        this.saleDateTime = LocalDateTime.now(); // Auto-set time to NOW when created
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getItemsSold() { return itemsSold; }
    public void setItemsSold(String itemsSold) { this.itemsSold = itemsSold; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public LocalDateTime getSaleDateTime() { return saleDateTime; }
    public void setSaleDateTime(LocalDateTime saleDateTime) { this.saleDateTime = saleDateTime; }
}