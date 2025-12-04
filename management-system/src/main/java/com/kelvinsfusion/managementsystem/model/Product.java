package com.kelvinsfusion.managementsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String category; // "Juice", "Snack", "Spice"

    // --- 250ML Data ---
    private double priceSmall;
    private int stockSmall;

    // --- 500ML Data ---
    private double priceLarge;
    private int stockLarge;

    public Product() {}

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPriceSmall() { return priceSmall; }
    public void setPriceSmall(double priceSmall) { this.priceSmall = priceSmall; }

    public int getStockSmall() { return stockSmall; }
    public void setStockSmall(int stockSmall) { this.stockSmall = stockSmall; }

    public double getPriceLarge() { return priceLarge; }
    public void setPriceLarge(double priceLarge) { this.priceLarge = priceLarge; }

    public int getStockLarge() { return stockLarge; }
    public void setStockLarge(int stockLarge) { this.stockLarge = stockLarge; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}