package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface GrievanceRepository extends JpaRepository<Grievance, String> {
    List<Grievance> findByStudentIdOrderByCreatedAtDesc(String studentId);
    List<Grievance> findAllByOrderByCreatedAtDesc();
    List<Grievance> findByCurrentStageOrderByCreatedAtDesc(String stage);
    long countByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Grievance g where g.id = :id")
    Optional<Grievance> findByIdForDeletion(@Param("id") String id);
}
