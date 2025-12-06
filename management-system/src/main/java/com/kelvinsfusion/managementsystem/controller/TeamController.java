package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.TeamLog;
import com.kelvinsfusion.managementsystem.repository.TeamLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.ArrayList;

@Controller
public class TeamController {

    @Autowired
    private TeamLogRepository teamLogRepository;

    @GetMapping("/team")
    public String showTeamHub(Model model) {
        try {
            // Load Notices (Newest First)
            model.addAttribute("notices", teamLogRepository.findByTypeOrderByTimestampDesc("NOTICE"));
            // Load Chat (Oldest First - like WhatsApp)
            model.addAttribute("chats", teamLogRepository.findByTypeOrderByTimestampAsc("CHAT"));
        } catch (Exception e) {
            // SAFE MODE: If DB fails, send empty lists so page doesn't crash
            model.addAttribute("notices", new ArrayList<>());
            model.addAttribute("chats", new ArrayList<>());
        }
        return "team-hub";
    }

    @PostMapping("/team/send")
    public String sendMessage(@RequestParam("content") String content,
                              @RequestParam("type") String type,
                              Principal principal) {
        TeamLog log = new TeamLog();
        log.setContent(content);
        log.setType(type); // "CHAT" or "NOTICE"
        log.setAuthor(principal != null ? principal.getName() : "Unknown");

        teamLogRepository.save(log);
        return "redirect:/team";
    }
}