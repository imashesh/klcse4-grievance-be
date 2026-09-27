package com.example.demo;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grievances")
public class Grievance {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, unique = true)
    private String refNo;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String studentName;

    private String batch;
    private String facultyId;

    @Column(nullable = false, length = 10)
    private String type;       // REG, TT, EXM, etc.

    @Column(nullable = false)
    private String typeLabel;   // "Registration Issue", etc.

    @Column(nullable = false)
    private String category;    // Academics, Evaluations, etc.

    @Column(columnDefinition = "TEXT")
    private String description;

    private String attachmentName;

    @Column(columnDefinition = "LONGTEXT")
    private String attachmentData;

    @Column(nullable = false)
    private String status;      // Pending, Resolved, Rejected

    @Column(nullable = false)
    private String currentStage; // faculty, coordinator, deputy_hod, hod, closed

    private long createdAt;

    @OneToMany(mappedBy = "grievance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("timestamp ASC")
    private List<GrievanceHistory> history = new ArrayList<>();

    public Grievance() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public String getFacultyId() { return facultyId; }
    public void setFacultyId(String facultyId) { this.facultyId = facultyId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public String getAttachmentData() { return attachmentData; }
    public void setAttachmentData(String attachmentData) { this.attachmentData = attachmentData; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public List<GrievanceHistory> getHistory() { return history; }
    public void setHistory(List<GrievanceHistory> history) { this.history = history; }
}
