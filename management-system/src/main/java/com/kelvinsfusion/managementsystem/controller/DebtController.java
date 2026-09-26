package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.Sale;
import com.kelvinsfusion.managementsystem.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class DebtController {

    @Autowired
    private SaleRepository saleRepository;

    @GetMapping("/debts")
    public String showDebts(Model model) {
        // Fetch all sales marked as "DEBT" and sort by newest first
        List<Sale> unpaidDebts = saleRepository.findAll().stream()
                .filter(sale -> "DEBT".equalsIgnoreCase(sale.getPaymentMethod()))
                .sorted(Comparator.comparing(Sale::getSaleDateTime).reversed())
                .collect(Collectors.toList());

        double totalDebt = unpaidDebts.stream().mapToDouble(Sale::getTotalAmount).sum();

        model.addAttribute("debts", unpaidDebts);
        model.addAttribute("totalDebt", totalDebt);
        return "debts";
    }

    @PostMapping("/debts/settle")
    public String settleDebt(@RequestParam Long id,
                             @RequestParam String settleMethod,
                             @RequestParam(required = false) Double splitMpesa,
                             @RequestParam(required = false) Double splitCash) {
        Sale sale = saleRepository.findById(id).orElse(null);
        if (sale != null) {
            if ("SPLIT".equals(settleMethod)) {
                double mpesaAmt = (splitMpesa != null) ? splitMpesa : 0;
                double cashAmt = (splitCash != null) ? splitCash : 0;
                sale.setPaymentMethod("Split (M-Pesa: " + mpesaAmt + ", Cash: " + cashAmt + ")");
            } else {
                sale.setPaymentMethod(settleMethod);
            }
            saleRepository.save(sale);
        }
        return "redirect:/debts";
    }
}