package com.kelvinsfusion.managementsystem.config;

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
    public boolean checkMessages(Principal principal) {
        // If not logged in, no notifications
        if (principal == null) return false;

        try {
            // Get all chats
            List<TeamLog> allChats = teamLogRepository.findByTypeOrderByTimestampDesc("CHAT");

            if (!allChats.isEmpty()) {
                TeamLog lastMsg = allChats.get(0);
                // Notification Logic: If the last message was NOT written by me, and is recent (last 24h)
                boolean notMyMessage = !lastMsg.getAuthor().equals(principal.getName());
                boolean isRecent = lastMsg.getTimestamp().isAfter(LocalDateTime.now().minusHours(24));

                return notMyMessage && isRecent;
            }
        } catch (Exception e) {
            // Ignore errors if table missing during startup
        }
        return false;
    }
}