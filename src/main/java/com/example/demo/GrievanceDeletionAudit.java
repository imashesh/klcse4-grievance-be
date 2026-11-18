package com.example.demo;

import jakarta.persistence.*;

/** Permanent audit record; deliberately has no FK to the deleted grievance. */
@Entity
@Table(name = "grievance_deletion_audit")
public class GrievanceDeletionAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String grievanceId;

    @Column(nullable = false, unique = true)
    private String refNo;

    @Column(nullable = false)
    private String originalStatus;

    @Column(nullable = false)
    private String adminLoginId;

    @Column(nullable = false)
    private String adminName;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private long deletedAt;

    public Long getId() { return id; }
    public String getGrievanceId() { return grievanceId; }
    public void setGrievanceId(String grievanceId) { this.grievanceId = grievanceId; }
    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
    public String getOriginalStatus() { return originalStatus; }
    public void setOriginalStatus(String originalStatus) { this.originalStatus = originalStatus; }
    public String getAdminLoginId() { return adminLoginId; }
    public void setAdminLoginId(String adminLoginId) { this.adminLoginId = adminLoginId; }
    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }
}
