package com.kelvinsfusion.managementsystem.repository;

import com.kelvinsfusion.managementsystem.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // Finds all sales between Start Date and End Date
    List<Sale> findBySaleDateTimeBetween(LocalDateTime start, LocalDateTime end);
    List<Sale> findBySaleDateTimeBetweenAndPaymentMethod(LocalDateTime start, LocalDateTime end, String paymentMethod);

    Sale findFirstByOrderBySaleDateTimeAsc();
}