package com.kelvinsfusion.managementsystem.controller;

import com.kelvinsfusion.managementsystem.model.TeamLog;
import com.kelvinsfusion.managementsystem.repository.TeamLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@ControllerAdvice
public class GlobalModelAttributes {

    @Autowired
    private TeamLogRepository teamLogRepository;

    @ModelAttribute("hasNewMessage")
    public boolean checkNewMessages(Principal principal) {
        if (principal == null) return false;

        // 1. Get all chats
        List<TeamLog> allChats = teamLogRepository.findByTypeOrderByTimestampDesc("CHAT");

        if (allChats.isEmpty()) return false;

        // 2. Check the very last message
        TeamLog lastMsg = allChats.get(0);

        // 3. Logic: If the last message was NOT written by me, it's "New"
        // (Simple logic: If I didn't write it, I need to read it)
        return !lastMsg.getAuthor().equals(principal.getName());
    }
}