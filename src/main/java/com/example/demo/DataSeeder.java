package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the database with all CSE-4 department users on first run.
 * Data is loaded from JSON files in src/main/resources/seed/.
 * Uses a simple manual JSON parser to avoid Jackson dependency.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private AppUserRepository userRepo;

    @Override
    public void run(String... args) throws Exception {
        if (userRepo.count() > 0) {
            System.out.println("[DataSeeder] Database already has users — skipping seed.");
            return;
        }

        System.out.println("[DataSeeder] Seeding users...");

        // 1. Admin users
        seedFromJson("seed/admin_users.json");

        // 2. Faculty users
        seedFromJson("seed/faculty_users.json");

        // 3. Student users
        seedFromJson("seed/student_users.json");

        System.out.println("[DataSeeder] Done. Total users: " + userRepo.count());
    }

    private void seedFromJson(String path) throws Exception {
        InputStream is = new ClassPathResource(path).getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        List<AppUser> users = parseJsonArray(sb.toString());
        userRepo.saveAll(users);
        System.out.println("[DataSeeder] Loaded " + users.size() + " from " + path);
    }

    /**
     * Minimal JSON array-of-objects parser. Expects format:
     * [{"loginId":"...","name":"...","role":"...","password":"...",...}, ...]
     */
    private List<AppUser> parseJsonArray(String json) {
        List<AppUser> users = new ArrayList<>();
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) return users;

        // Remove outer brackets
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return users;

        // Split by objects - find matching braces
        List<String> objects = splitObjects(json);

        for (String obj : objects) {
            String loginId = extractField(obj, "loginId");
            String name = extractField(obj, "name");
            String role = extractField(obj, "role");
            String password = extractField(obj, "password");
            String batch = extractField(obj, "batch");
            String facultyId = extractField(obj, "facultyId");
            String categories = extractField(obj, "categories");

            if (loginId != null && name != null && role != null) {
                users.add(new AppUser(loginId, name, role,
                        password != null ? password : loginId,
                        batch, facultyId, categories));
            }
        }

        return users;
    }

    private List<String> splitObjects(String json) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private String extractField(String obj, String key) {
        String pattern = "\"" + key + "\"";
        int idx = obj.indexOf(pattern);
        if (idx == -1) return null;

        // Find the colon
        int colonIdx = obj.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) return null;

        // Find the value
        int valueStart = colonIdx + 1;
        while (valueStart < obj.length() && obj.charAt(valueStart) == ' ') valueStart++;

        if (valueStart >= obj.length()) return null;

        char first = obj.charAt(valueStart);
        if (first == '"') {
            // String value
            int valueEnd = obj.indexOf('"', valueStart + 1);
            if (valueEnd == -1) return null;
            return obj.substring(valueStart + 1, valueEnd);
        } else if (first == 'n') {
            return null; // null
        } else {
            // Number or other
            int valueEnd = valueStart;
            while (valueEnd < obj.length() && obj.charAt(valueEnd) != ',' && obj.charAt(valueEnd) != '}') {
                valueEnd++;
            }
            return obj.substring(valueStart, valueEnd).trim();
        }
    }
}
