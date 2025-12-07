package com.kelvinsfusion.managementsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TeamLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String content;
    private String author; // "admin" or "staff"
    private String type;   // "CHAT" or "NOTICE"
    private LocalDateTime timestamp;
    private String recipient; // NEW: Stores "admin" or "john"

    public TeamLog() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
}