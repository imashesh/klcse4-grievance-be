package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/grievances")
public class GrievanceController {

    @Autowired
    private GrievanceRepository grievanceRepo;

    @Autowired
    private AppUserRepository userRepo;

    // In-memory ref-number counters (production would use a DB sequence)
    private final ConcurrentHashMap<String, AtomicInteger> refCounters = new ConcurrentHashMap<>();

    private static final String[] STAGE_ORDER = {"faculty", "coordinator", "deputy_hod", "hod"};

    // ---- Create grievance ----
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String studentLoginId = (String) body.get("studentId");
        String studentName = (String) body.get("studentName");
        String batch = (String) body.get("batch");
        String facultyId = (String) body.get("facultyId");
        String type = (String) body.get("type");
        String typeLabel = (String) body.get("typeLabel");
        String category = (String) body.get("category");
        String description = (String) body.get("description");
        String attachmentName = (String) body.get("attachmentName");
        String attachmentData = (String) body.get("attachmentData");

        int year = java.time.Year.now().getValue();
        String counterKey = type + "-" + year;
        AtomicInteger counter = refCounters.computeIfAbsent(counterKey, k -> {
            // Initialize from existing count in DB
            long existing = grievanceRepo.findAllByOrderByCreatedAtDesc().stream()
                    .filter(g -> g.getRefNo().startsWith("CSE4/" + type + "/" + year + "/"))
                    .count();
            return new AtomicInteger((int) existing);
        });
        int nextNum = counter.incrementAndGet();
        String refNo = String.format("CSE4/%s/%d/%04d", type, year, nextNum);

        String id = "G-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4);

        Grievance g = new Grievance();
        g.setId(id);
        g.setRefNo(refNo);
        g.setStudentId(studentLoginId);
        g.setStudentName(studentName);
        g.setBatch(batch);
        g.setFacultyId(facultyId);
        g.setType(type);
        g.setTypeLabel(typeLabel);
        g.setCategory(category);
        g.setDescription(description);
        g.setAttachmentName(attachmentName);
        g.setAttachmentData(attachmentData);
        g.setStatus("Pending");
        g.setCurrentStage("faculty");
        g.setCreatedAt(System.currentTimeMillis());

        grievanceRepo.save(g);

        return ResponseEntity.ok(Map.of("refNo", refNo, "id", id));
    }

    // ---- Get grievances for a student ----
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getByStudent(@PathVariable String studentId) {
        return ResponseEntity.ok(grievanceRepo.findByStudentIdOrderByCreatedAtDesc(studentId));
    }

    // ---- Get all grievances (admin full register) ----
    @GetMapping
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(grievanceRepo.findAllByOrderByCreatedAtDesc());
    }

    // ---- Get grievances for approver queue ----
    @GetMapping("/queue")
    public ResponseEntity<?> getQueue(
            @RequestParam String stage,
            @RequestParam String role,
            @RequestParam String userId,
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) String categories,
            @RequestParam(required = false) String facultyId
    ) {
        List<Grievance> all = grievanceRepo.findAllByOrderByCreatedAtDesc();
        List<Grievance> result = new ArrayList<>();

        for (Grievance g : all) {
            boolean inScope = false;
            if ("faculty".equals(role)) {
                inScope = userId.equals(g.getFacultyId());
            } else if ("coordinator".equals(role)) {
                inScope = batch != null && batch.equals(g.getBatch());
            } else if ("deputy_hod".equals(role)) {
                if (categories != null) {
                    for (String cat : categories.split(",")) {
                        if (cat.trim().equals(g.getCategory())) {
                            inScope = true;
                            break;
                        }
                    }
                }
            } else if ("hod".equals(role)) {
                inScope = true;
            }
            if (inScope) result.add(g);
        }

        return ResponseEntity.ok(result);
    }

    // ---- Act on a grievance (approve/reject/resolve) ----
    @PutMapping("/{id}/action")
    public ResponseEntity<?> act(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<Grievance> opt = grievanceRepo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Grievance g = opt.get();
        String action = body.get("action");
        String remarks = body.get("remarks");
        String actorId = body.get("actorId");
        String actorName = body.get("actorName");
        String actorRole = body.get("actorRole");

        GrievanceHistory entry = new GrievanceHistory();
        entry.setGrievance(g);
        entry.setStage(g.getCurrentStage());
        entry.setActorId(actorId);
        entry.setActorName(actorName);
        entry.setActorRole(actorRole);
        entry.setAction(action);
        entry.setRemarks(remarks);
        entry.setTimestamp(System.currentTimeMillis());
        g.getHistory().add(entry);

        if ("rejected".equals(action)) {
            g.setCurrentStage("closed");
            g.setStatus("Rejected");
        } else if ("resolved".equals(action)) {
            g.setCurrentStage("closed");
            g.setStatus("Resolved");
        } else { // approved
            String ns = nextStage(g.getCurrentStage());
            g.setCurrentStage(ns);
            g.setStatus("closed".equals(ns) ? "Resolved" : "Pending");
        }

        grievanceRepo.save(g);

        return ResponseEntity.ok(Map.of("status", g.getStatus(), "currentStage", g.getCurrentStage()));
    }

    // ---- Stats ----
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("total", grievanceRepo.count());
        s.put("pending", grievanceRepo.countByStatus("Pending"));
        s.put("resolved", grievanceRepo.countByStatus("Resolved"));
        s.put("rejected", grievanceRepo.countByStatus("Rejected"));
        return ResponseEntity.ok(s);
    }

    private String nextStage(String stage) {
        for (int i = 0; i < STAGE_ORDER.length; i++) {
            if (STAGE_ORDER[i].equals(stage)) {
                if (i == STAGE_ORDER.length - 1) return "closed";
                return STAGE_ORDER[i + 1];
            }
        }
        return "closed";
    }
}
