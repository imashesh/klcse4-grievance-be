package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AppUserRepository userRepo;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String loginId = body.get("loginId");
        String password = body.get("password");
        String role = body.get("role"); // optional now

        if (loginId == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "loginId and password are required."));
        }

        AppUser user = null;

        if (role != null && !role.isBlank()) {
            // Legacy: role-based lookup
            Optional<AppUser> userOpt =
                    userRepo.findByLoginIdIgnoreCaseAndRole(
                            loginId.trim(),
                            role.trim()
                    );

            if (userOpt.isPresent()) {
                user = userOpt.get();
            }

        } else {
            // New: auto-detect role
            List<AppUser> matches =
                    userRepo.findByLoginIdIgnoreCase(loginId.trim());

            if (!matches.isEmpty()) {

                /*
                 * Some users may have the same Login ID for more than one role.
                 *
                 * Example:
                 * 4868 -> admin
                 * 4868 -> deputy_hod
                 *
                 * When no role is explicitly supplied by the frontend,
                 * prefer the admin account.
                 */
                user = matches.stream()
                        .filter(u -> "admin".equalsIgnoreCase(u.getRole()))
                        .findFirst()
                        .orElse(matches.get(0));
            }
        }

        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "No account found with that ID."));
        }

        if (!user.getPassword().equals(password)) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Incorrect password."));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("loginId", user.getLoginId());
        response.put("name", user.getName());
        response.put("role", user.getRole());
        response.put("batch", user.getBatch());
        response.put("facultyId", user.getFacultyId());
        response.put("categories", user.getCategories());

        return ResponseEntity.ok(response);
    }
}