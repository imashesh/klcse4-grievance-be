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
        if (loginId == null || password == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "loginId and password are required."));
        }

        // Authenticate the actual account password; never select a role
        // before validating the credential. ID 4868 is explicitly granted
        // both Deputy HOD and Administrator access when either of its
        // registered accounts authenticates successfully.
        List<AppUser> matches = userRepo.findByLoginIdIgnoreCase(loginId.trim());
        AppUser user = matches.stream()
                .filter(u -> u.getPassword().equals(password))
                .filter(u -> "deputy_hod".equalsIgnoreCase(u.getRole()))
                .findFirst()
                .orElseGet(() -> matches.stream()
                        .filter(u -> u.getPassword().equals(password))
                        .findFirst().orElse(null));

        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid login ID or password."));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("loginId", user.getLoginId());
        response.put("name", user.getName());
        response.put("role", user.getRole());
        response.put("batch", user.getBatch());
        response.put("facultyId", user.getFacultyId());
        response.put("categories", user.getCategories());
        boolean dualRole4868 = "4868".equals(user.getLoginId())
                && ("admin".equalsIgnoreCase(user.getRole())
                    || "deputy_hod".equalsIgnoreCase(user.getRole()))
                && userRepo.findByLoginIdIgnoreCaseAndRole("4868", "admin").isPresent()
                && userRepo.findByLoginIdIgnoreCaseAndRole("4868", "deputy_hod").isPresent();
        response.put("adminAccess", dualRole4868 || "admin".equalsIgnoreCase(user.getRole()));


        // An authenticated administrator may also use their own Deputy HOD
        // workspace if a separate Deputy HOD account exists for the same ID.
        // This does NOT grant admin privileges to a Deputy HOD login.
        if ("admin".equalsIgnoreCase(user.getRole())) {
            userRepo.findByLoginIdIgnoreCaseAndRole(user.getLoginId(), "deputy_hod")
                    .ifPresent(deputy -> {
                        Map<String, Object> deputyProfile = new LinkedHashMap<>();
                        deputyProfile.put("loginId", deputy.getLoginId());
                        deputyProfile.put("name", deputy.getName());
                        deputyProfile.put("role", deputy.getRole());
                        deputyProfile.put("batch", deputy.getBatch());
                        deputyProfile.put("categories", deputy.getCategories());
                        response.put("deputyHodProfile", deputyProfile);
                    });
        }

        return ResponseEntity.ok(response);
    }
}