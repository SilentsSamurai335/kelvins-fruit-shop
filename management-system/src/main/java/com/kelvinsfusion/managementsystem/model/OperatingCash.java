package com.kelvinsfusion.managementsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id; // <-- Use the jakarta.persistence version

@Entity
public class OperatingCash {
    @Id
    private Long id = 1L;

    private Double amount = 0.0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}