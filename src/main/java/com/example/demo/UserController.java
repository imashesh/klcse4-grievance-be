package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private AppUserRepository userRepo;

    // ---- Get users by role ----
    @GetMapping
    public ResponseEntity<?> getByRole(@RequestParam String role) {
        return ResponseEntity.ok(userRepo.findByRole(role));
    }

    // ---- Add single user (admin) ----
    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody Map<String, String> body) {
        String loginId = body.get("loginId");
        String name = body.get("name");
        String role = body.get("role");
        String password = body.getOrDefault("password", loginId);
        String batch = body.get("batch");
        String facultyId = body.get("facultyId");
        String categories = body.get("categories");

        if (userRepo.findByLoginIdIgnoreCaseAndRole(loginId, role).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ID \"" + loginId + "\" already exists for role " + role + "."));
        }

        AppUser user = new AppUser(loginId, name, role, password, batch, facultyId, categories);
        userRepo.save(user);

        return ResponseEntity.ok(Map.of("message", name + " added as " + role + "."));
    }

    // ---- Update user (admin) ----
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<AppUser> opt = userRepo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AppUser user = opt.get();

        if (body.containsKey("name") && !body.get("name").isBlank()) {
            user.setName(body.get("name").trim());
        }
        if (body.containsKey("password") && !body.get("password").isBlank()) {
            user.setPassword(body.get("password").trim());
        }
        if (body.containsKey("batch")) {
            user.setBatch(body.get("batch"));
        }
        if (body.containsKey("facultyId")) {
            user.setFacultyId(body.get("facultyId"));
        }
        if (body.containsKey("categories")) {
            user.setCategories(body.get("categories"));
        }

        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", user.getName() + " updated successfully."));
    }

    // ---- Delete user (admin) ----
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        Optional<AppUser> opt = userRepo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AppUser user = opt.get();
        String name = user.getName();
        userRepo.delete(user);
        return ResponseEntity.ok(Map.of("message", name + " deleted successfully."));
    }

    // ---- Bulk add users (admin) ----
    @PostMapping("/bulk")
    public ResponseEntity<?> bulkAdd(@RequestBody Map<String, Object> body) {
        String role = (String) body.get("role");
        @SuppressWarnings("unchecked")
        List<List<String>> rows = (List<List<String>>) body.get("rows");

        int added = 0;
        int skipped = 0;

        for (List<String> row : rows) {
            if (row.isEmpty() || row.get(0) == null || row.get(0).isBlank()) continue;

            if ("student".equals(role)) {
                String id = row.get(0).trim();
                String name = row.size() > 1 ? row.get(1).trim() : "";
                String fId = row.size() > 2 ? row.get(2).trim() : "";
                String pwd = row.size() > 3 ? row.get(3).trim() : id;
                String batch = "Y" + id.substring(0, 2);

                if (name.isBlank()) continue;
                if (userRepo.findByLoginIdIgnoreCaseAndRole(id, "student").isPresent()) {
                    skipped++;
                    continue;
                }
                userRepo.save(new AppUser(id, name, "student", pwd, batch, fId, null));
                added++;
            } else {
                String id = row.get(0).trim();
                String name = row.size() > 1 ? row.get(1).trim() : "";
                String pwd = row.size() > 2 ? row.get(2).trim() : id;

                if (name.isBlank()) continue;
                if (userRepo.findByLoginIdIgnoreCaseAndRole(id, role).isPresent()) {
                    skipped++;
                    continue;
                }
                userRepo.save(new AppUser(id, name, role, pwd, null, null, null));
                added++;
            }
        }

        return ResponseEntity.ok(Map.of("added", added, "skipped", skipped));
    }

    // ---- Stats ----
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("student", userRepo.countByRole("student"));
        s.put("faculty", userRepo.countByRole("faculty"));
        s.put("coordinator", userRepo.countByRole("coordinator"));
        s.put("deputy_hod", userRepo.countByRole("deputy_hod"));
        s.put("hod", userRepo.countByRole("hod"));
        s.put("admin", userRepo.countByRole("admin"));
        return ResponseEntity.ok(s);
    }
}
