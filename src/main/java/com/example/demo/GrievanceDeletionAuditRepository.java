package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GrievanceDeletionAuditRepository extends JpaRepository<GrievanceDeletionAudit, Long> {
    List<GrievanceDeletionAudit> findByRefNoStartingWith(String prefix);
}
