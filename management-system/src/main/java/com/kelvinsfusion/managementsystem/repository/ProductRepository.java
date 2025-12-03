
package com.kelvinsfusion.managementsystem.repository;

import com.kelvinsfusion.managementsystem.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

// This magic interface gives us methods like .save(), .findAll(), .delete() for free!
public interface ProductRepository extends JpaRepository<Product, Long> {
}