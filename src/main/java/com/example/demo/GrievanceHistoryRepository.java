package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GrievanceHistoryRepository extends JpaRepository<GrievanceHistory, Long> {
    List<GrievanceHistory> findByGrievanceIdOrderByTimestampAsc(String grievanceId);
}
