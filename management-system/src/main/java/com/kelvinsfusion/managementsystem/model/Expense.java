package com.kelvinsfusion.managementsystem.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;

    private String category; // e.g., "Transport", "Utilities", "Juice Ingredients"
    private String itemName; // e.g., "Sugar", "Rent", "Lunch"
    private double amount;   // The Cost

    // --- Conditional Fields (Only used for some categories) ---
    private String quantity;      // For Ingredients

    // For Transport
    private Integer trips;
    private String startLocation; // "From"
    private String endLocation;   // "To"

    // For Maintenance/Transport descriptions
    private String description;

    // NEW: For Water/Electricity
    private Double units;

    public Expense() {
        this.date = LocalDate.now();
    }

    // --- GETTERS AND SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }

    public Integer getTrips() { return trips; }
    public void setTrips(Integer trips) { this.trips = trips; }

    public String getStartLocation() { return startLocation; }
    public void setStartLocation(String startLocation) { this.startLocation = startLocation; }

    public String getEndLocation() { return endLocation; }
    public void setEndLocation(String endLocation) { this.endLocation = endLocation; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getUnits() { return units; }
    public void setUnits(Double units) { this.units = units; }
}