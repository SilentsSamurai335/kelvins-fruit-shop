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
    private Double priceSmall;
    private Int stockSmall;

    // --- 500ML Data ---
    private Double priceLarge;
    private Int stockLarge;

    public Product() {}

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getPriceSmall() { return priceSmall; }
    public void setPriceSmall(Double priceSmall) { this.priceSmall = priceSmall; }

    public Int getStockSmall() { return stockSmall; }
    public void setStockSmall(Int stockSmall) { this.stockSmall = stockSmall; }

    public Double getPriceLarge() { return priceLarge; }
    public void setPriceLarge(Double priceLarge) { this.priceLarge = priceLarge; }

    public Int getStockLarge() { return stockLarge; }
    public void setStockLarge(Int stockLarge) { this.stockLarge = stockLarge; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
