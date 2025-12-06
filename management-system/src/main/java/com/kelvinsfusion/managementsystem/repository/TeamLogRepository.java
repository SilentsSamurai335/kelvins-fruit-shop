package com.kelvinsfusion.managementsystem.repository;

import com.kelvinsfusion.managementsystem.model.TeamLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamLogRepository extends JpaRepository<TeamLog, Long> {
    // We want newest messages at the bottom for Chat, but Top for Notices
    List<TeamLog> findByTypeOrderByTimestampDesc(String type);
    List<TeamLog> findByTypeOrderByTimestampAsc(String type);
}