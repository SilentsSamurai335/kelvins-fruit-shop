package com.kelvinsfusion.managementsystem.repository;

import com.kelvinsfusion.managementsystem.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
}