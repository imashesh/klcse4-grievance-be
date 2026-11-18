package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Optional UI-review accounts. Passwords are provided only through the
 * GRIEVANCE_DEMO_USERS Railway environment variable, never in source control.
 * Format: id:role:password,id:role:password
 * Existing users are never modified.
 */
@Component
@Order(2)
public class DemoAccountSeeder implements CommandLineRunner {
    @Autowired
    private AppUserRepository userRepo;

    @Override
    public void run(String... args) {
        String config = System.getenv("GRIEVANCE_DEMO_USERS");
        if (config == null || config.isBlank()) return;

        int created = 0;
        for (String entry : config.split(",")) {
            String[] fields = entry.trim().split(":", -1);
            if (fields.length != 3) continue;
            String id = fields[0].trim();
            String role = fields[1].trim();
            String password = fields[2].trim();
            if (!id.matches("\\d{4}|\\d{10}") || !password.matches("\\d{10}")) continue;
            if (!(role.equals("admin") || role.equals("faculty") ||
                  role.equals("coordinator") || role.equals("deputy_hod") ||
                  role.equals("hod") || role.equals("student"))) continue;
            if (userRepo.findByLoginIdIgnoreCaseAndRole(id, role).isPresent()) continue;
            String name = "Demo " + switch (role) {
                case "admin" -> "Administrator";
                case "faculty" -> "Faculty Mentor";
                case "coordinator" -> "Year Coordinator";
                case "deputy_hod" -> "Deputy HOD";
                case "hod" -> "HOD";
                default -> "Student";
            };
            String batch = role.equals("student") || role.equals("coordinator") ? "Y25" : null;
            String facultyId = role.equals("student") ? "9902" : null;
            String categories = role.equals("deputy_hod") ? "Academics,Evaluations,Student Affairs,Placements" : null;
            userRepo.save(new AppUser(id, name, role, password, batch, facultyId, categories));
            created++;
        }
        System.out.println("[DemoAccountSeeder] Created " + created + " UI review account(s).");
    }
}
