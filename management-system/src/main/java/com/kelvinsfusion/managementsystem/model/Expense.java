package com.kelvinsfusion.managementsystem.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemName; // e.g. "Sugar 10kg"
    private double amount;   // e.g. 1500.00
    private LocalDate date;  // When did we buy it?
    private String quantity; // NEW: e.g. "2 kgs", "5 packets"

    public Expense() {
        this.date = LocalDate.now(); // Default to today
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
}