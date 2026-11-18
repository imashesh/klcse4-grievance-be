package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    @Autowired
    private GrievanceDeletionAuditRepository deletionAuditRepo;

    // Requires a verified production database backup before enabling on Railway.
    @Value("${grievances.admin-deletion.enabled:false}")
    private boolean adminDeletionEnabled;

    // Independent secret: prevents public/default admin credentials from
    // becoming sufficient to authorize an irreversible operation.
    @Value("${grievances.admin-deletion.security-key:}")
    private String adminDeletionSecurityKey;

    // Single-instance counters; seed from both live grievances and permanent deletion
    // audit records so a deleted reference number can never be reissued on restart.
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
            String prefix = "CSE4/" + type + "/" + year + "/";
            int liveMax = grievanceRepo.findAllByOrderByCreatedAtDesc().stream()
                    .map(Grievance::getRefNo)
                    .mapToInt(ref -> referenceSequence(ref, prefix))
                    .max().orElse(0);
            int deletedMax = deletionAuditRepo.findByRefNoStartingWith(prefix).stream()
                    .map(GrievanceDeletionAudit::getRefNo)
                    .mapToInt(ref -> referenceSequence(ref, prefix))
                    .max().orElse(0);
            return new AtomicInteger(Math.max(liveMax, deletedMax));
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

    /**
     * Intentionally requires fresh server-side admin authentication for every
     * deletion: the existing frontend localStorage role is NOT authentication.
     * Audit insertion and physical deletion are committed atomically.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> permanentlyDelete(@PathVariable String id,
                                               @RequestBody Map<String, String> body) {
        if (!adminDeletionEnabled || adminDeletionSecurityKey == null
                || adminDeletionSecurityKey.length() < 24) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Admin deletion is disabled until a backup and deletion security key are configured."));
        }

        String loginId = body.get("adminLoginId");
        String password = body.get("adminPassword");
        String securityKey = body.get("securityKey");
        String confirmedRef = body.get("referenceConfirmation");
        String reason = body.get("reason");
        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Admin credentials are required."));
        }

        Optional<AppUser> admin = userRepo.findByLoginIdIgnoreCaseAndRole(loginId.trim(), "admin");
        Optional<AppUser> deputy = "4868".equals(loginId.trim())
                ? userRepo.findByLoginIdIgnoreCaseAndRole("4868", "deputy_hod")
                : Optional.empty();

        // Employee 4868 is a designated dual-role user. Re-authenticate with
        // either of their own account passwords, but only if both role
        // registrations exist. Other Deputy HODs receive no Admin access.
        AppUser verifiedActor = null;
        if (admin.isPresent() && MessageDigest.isEqual(
                admin.get().getPassword().getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_8))) {
            verifiedActor = admin.get();
        } else if ("4868".equals(loginId.trim()) && admin.isPresent() && deputy.isPresent()
                && MessageDigest.isEqual(
                    deputy.get().getPassword().getBytes(StandardCharsets.UTF_8),
                    password.getBytes(StandardCharsets.UTF_8))) {
            verifiedActor = deputy.get();
        }
        if (verifiedActor == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Administrator verification failed."));
        }

        if (securityKey == null || !MessageDigest.isEqual(
                adminDeletionSecurityKey.getBytes(StandardCharsets.UTF_8),
                securityKey.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Deletion security key is incorrect."));
        }

        if (reason == null || reason.trim().length() < 10 || reason.trim().length() > 500) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "A deletion reason of 10 to 500 characters is required."));
        }

        Optional<Grievance> target = grievanceRepo.findByIdForDeletion(id);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Grievance no longer exists."));
        }

        Grievance grievance = target.get();
        if (!"Resolved".equals(grievance.getStatus()) && !"Rejected".equals(grievance.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Only Resolved or Rejected grievances can be deleted."));
        }
        if (!grievance.getRefNo().equals(confirmedRef)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Type the exact grievance reference number to confirm deletion."));
        }

        GrievanceDeletionAudit audit = new GrievanceDeletionAudit();
        audit.setGrievanceId(grievance.getId());
        audit.setRefNo(grievance.getRefNo());
        audit.setOriginalStatus(grievance.getStatus());
        audit.setAdminLoginId(verifiedActor.getLoginId());
        audit.setAdminName(verifiedActor.getName());
        audit.setReason(reason.trim());
        audit.setDeletedAt(System.currentTimeMillis());

        deletionAuditRepo.save(audit);
        grievanceRepo.delete(grievance); // cascades to grievance_history and attachment columns
        return ResponseEntity.ok(Map.of("message", "Grievance permanently deleted.",
                "refNo", grievance.getRefNo()));
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

    private static int referenceSequence(String ref, String prefix) {
        if (ref == null || !ref.startsWith(prefix)) return 0;
        try {
            return Integer.parseInt(ref.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
