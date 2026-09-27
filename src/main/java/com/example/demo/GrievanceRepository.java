package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GrievanceRepository extends JpaRepository<Grievance, String> {
    List<Grievance> findByStudentIdOrderByCreatedAtDesc(String studentId);
    List<Grievance> findAllByOrderByCreatedAtDesc();
    List<Grievance> findByCurrentStageOrderByCreatedAtDesc(String stage);
    long countByStatus(String status);
}
