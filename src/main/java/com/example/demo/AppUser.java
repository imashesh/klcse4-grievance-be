package com.example.demo;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(columnNames = {"login_id", "role"}))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false)
    private String loginId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role; // STUDENT, FACULTY, COORDINATOR, DEPUTY_HOD, HOD, ADMIN

    @Column(nullable = false)
    private String password;

    private String batch;       // Y25, Y24, Y23 — for students/coordinators
    private String facultyId;   // for students: mapped faculty counsellor ID
    private String categories;  // for deputy_hod: comma-separated categories

    public AppUser() {}

    public AppUser(String loginId, String name, String role, String password,
                   String batch, String facultyId, String categories) {
        this.loginId = loginId;
        this.name = name;
        this.role = role;
        this.password = password;
        this.batch = batch;
        this.facultyId = facultyId;
        this.categories = categories;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public String getFacultyId() { return facultyId; }
    public void setFacultyId(String facultyId) { this.facultyId = facultyId; }

    public String getCategories() { return categories; }
    public void setCategories(String categories) { this.categories = categories; }
}
