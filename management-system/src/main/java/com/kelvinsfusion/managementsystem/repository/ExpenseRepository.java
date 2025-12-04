package com.kelvinsfusion.managementsystem.repository;

import com.kelvinsfusion.managementsystem.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // Find expenses between two dates
    List<Expense> findByDateBetween(LocalDate startDate, LocalDate endDate);
}