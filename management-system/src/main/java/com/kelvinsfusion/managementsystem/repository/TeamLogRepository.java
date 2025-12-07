package com.kelvinsfusion.managementsystem.repository;

import com.kelvinsfusion.managementsystem.model.TeamLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TeamLogRepository extends JpaRepository<TeamLog, Long> {

    // 1. For Public Notices (Newest First)
    List<TeamLog> findByTypeOrderByTimestampDesc(String type);

    // 2. For Private Chat (The WhatsApp Logic)
    // This finds messages where:
    // (Sender is Me AND Receiver is You) OR (Sender is You AND Receiver is Me)
    @Query("SELECT t FROM TeamLog t WHERE t.type = 'CHAT' AND ((t.author = ?1 AND t.recipient = ?2) OR (t.author = ?2 AND t.recipient = ?1)) ORDER BY t.timestamp ASC")
    List<TeamLog> findChatHistory(String user1, String user2);
}