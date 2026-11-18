package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrievanceDeletionTests {

    @Mock private GrievanceRepository grievanceRepo;
    @Mock private GrievanceDeletionAuditRepository deletionAuditRepo;
    @Mock private AppUserRepository userRepo;
    @InjectMocks private GrievanceController controller;

    private static final String REF = "CSE4/EXM/2026/0042";

    @BeforeEach
    void enabledForTests() {
        ReflectionTestUtils.setField(controller, "adminDeletionEnabled", true);
        ReflectionTestUtils.setField(controller, "adminDeletionSecurityKey", "unit-test-only-deletion-security-key");
    }

    private Map<String, String> request() {
        return Map.of(
                "adminLoginId", "admin-1",
                "adminPassword", "correct-password",
                "securityKey", "unit-test-only-deletion-security-key",
                "referenceConfirmation", REF,
                "reason", "Duplicate case filed in error"
        );
    }

    private void validAdmin() {
        AppUser admin = new AppUser("admin-1", "Department Admin", "admin",
                "correct-password", null, null, null);
        when(userRepo.findByLoginIdIgnoreCaseAndRole("admin-1", "admin"))
                .thenReturn(Optional.of(admin));
    }

    private Grievance grievance(String status) {
        Grievance g = new Grievance();
        g.setId("g-1");
        g.setRefNo(REF);
        g.setStatus(status);
        return g;
    }

    @Test
    void deletionIsDisabledByDefault() {
        ReflectionTestUtils.setField(controller, "adminDeletionEnabled", false);
        ResponseEntity<?> result = controller.permanentlyDelete("g-1", request());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.getStatusCode());
        verifyNoInteractions(userRepo, grievanceRepo, deletionAuditRepo);
    }

    @Test
    void rejectsMissingAdminCredentials() {
        ResponseEntity<?> result = controller.permanentlyDelete("g-1", Map.of(
                "reason", "Duplicate case filed in error", "referenceConfirmation", REF,
                "securityKey", "unit-test-only-deletion-security-key"));
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verifyNoInteractions(grievanceRepo, deletionAuditRepo);
    }

    @Test
    void rejectsNonAdminAccountEvenWithPassword() {
        when(userRepo.findByLoginIdIgnoreCaseAndRole("admin-1", "admin"))
                .thenReturn(Optional.empty());
        ResponseEntity<?> result = controller.permanentlyDelete("g-1", request());
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        verifyNoInteractions(grievanceRepo, deletionAuditRepo);
    }

    @Test
    void rejectsWrongDeletionSecurityKey() {
        validAdmin();
        ResponseEntity<?> result = controller.permanentlyDelete("g-1", Map.of(
                "adminLoginId", "admin-1", "adminPassword", "correct-password",
                "securityKey", "wrong", "referenceConfirmation", REF,
                "reason", "Duplicate case filed in error"));
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        verifyNoInteractions(grievanceRepo, deletionAuditRepo);
    }

    @Test
    void rejectsIncorrectAdminPassword() {
        AppUser admin = new AppUser("admin-1", "Department Admin", "admin",
                "another-password", null, null, null);
        when(userRepo.findByLoginIdIgnoreCaseAndRole("admin-1", "admin"))
                .thenReturn(Optional.of(admin));
        ResponseEntity<?> result = controller.permanentlyDelete("g-1", request());
        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        verifyNoInteractions(grievanceRepo, deletionAuditRepo);
    }

    @Test
    void pendingGrievancesCannotBeDeleted() {
        validAdmin();
        when(grievanceRepo.findByIdForDeletion("g-1"))
                .thenReturn(Optional.of(grievance("Pending")));
        ResponseEntity<?> result = controller.permanentlyDelete("g-1", request());
        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        verify(grievanceRepo, never()).delete(any());
        verifyNoInteractions(deletionAuditRepo);
    }

    @Test
    void requiresCorrectReferenceAndReason() {
        validAdmin();
        ResponseEntity<?> shortReason = controller.permanentlyDelete("g-1", Map.of(
                "adminLoginId", "admin-1", "adminPassword", "correct-password",
                "securityKey", "unit-test-only-deletion-security-key",
                "referenceConfirmation", REF, "reason", "short"));
        assertEquals(HttpStatus.BAD_REQUEST, shortReason.getStatusCode());
        when(grievanceRepo.findByIdForDeletion("g-1"))
                .thenReturn(Optional.of(grievance("Rejected")));
        ResponseEntity<?> wrongRef = controller.permanentlyDelete("g-1", Map.of(
                "adminLoginId", "admin-1", "adminPassword", "correct-password",
                "securityKey", "unit-test-only-deletion-security-key",
                "referenceConfirmation", "WRONG", "reason", "Duplicate case filed in error"));
        assertEquals(HttpStatus.BAD_REQUEST, wrongRef.getStatusCode());
        verify(grievanceRepo, never()).delete(any());
        verifyNoInteractions(deletionAuditRepo);
    }

    @Test
    void deletingResolvedGrievancePreservesIndependentAuditRecord() {
        validAdmin();
        Grievance resolved = grievance("Resolved");
        when(grievanceRepo.findByIdForDeletion("g-1"))
                .thenReturn(Optional.of(resolved));

        ResponseEntity<?> result = controller.permanentlyDelete("g-1", request());
        assertEquals(HttpStatus.OK, result.getStatusCode());
        ArgumentCaptor<GrievanceDeletionAudit> captor =
                ArgumentCaptor.forClass(GrievanceDeletionAudit.class);
        var order = inOrder(deletionAuditRepo, grievanceRepo);
        order.verify(deletionAuditRepo).save(captor.capture());
        order.verify(grievanceRepo).delete(resolved);
        assertEquals(REF, captor.getValue().getRefNo());
        assertEquals("admin-1", captor.getValue().getAdminLoginId());
        assertEquals("Resolved", captor.getValue().getOriginalStatus());
        assertEquals("Duplicate case filed in error", captor.getValue().getReason());
        assertTrue(captor.getValue().getDeletedAt() > 0);
    }

    @Test
    void deletingRejectedGrievanceIsAllowed() {
        validAdmin();
        Grievance rejected = grievance("Rejected");
        when(grievanceRepo.findByIdForDeletion("g-1"))
                .thenReturn(Optional.of(rejected));
        assertEquals(HttpStatus.OK, controller.permanentlyDelete("g-1", request()).getStatusCode());
        verify(grievanceRepo).delete(rejected);
    }

    @Test
    void employee4868DeputyPasswordMayDeleteWithIndependentSecurityKey() {
        AppUser admin = new AppUser("4868", "Dual Role Admin", "admin",
                "different-admin-password", null, null, null);
        AppUser deputy = new AppUser("4868", "Dual Role Deputy", "deputy_hod",
                "deputy-password", null, null, "Academics,Evaluations");
        when(userRepo.findByLoginIdIgnoreCaseAndRole("4868", "admin"))
                .thenReturn(Optional.of(admin));
        when(userRepo.findByLoginIdIgnoreCaseAndRole("4868", "deputy_hod"))
                .thenReturn(Optional.of(deputy));
        when(grievanceRepo.findByIdForDeletion("g-1"))
                .thenReturn(Optional.of(grievance("Resolved")));

        ResponseEntity<?> result = controller.permanentlyDelete("g-1", Map.of(
                "adminLoginId", "4868", "adminPassword", "deputy-password",
                "securityKey", "unit-test-only-deletion-security-key",
                "referenceConfirmation", REF, "reason", "Duplicate case filed in error"));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(grievanceRepo).delete(any());
    }

    @Test
    void employee4868DeputyPasswordCannotDeleteWithoutAdminRegistration() {
        AppUser deputy = new AppUser("4868", "Deputy Only", "deputy_hod",
                "deputy-password", null, null, "Academics");
        when(userRepo.findByLoginIdIgnoreCaseAndRole("4868", "admin"))
                .thenReturn(Optional.empty());
        when(userRepo.findByLoginIdIgnoreCaseAndRole("4868", "deputy_hod"))
                .thenReturn(Optional.of(deputy));

        ResponseEntity<?> result = controller.permanentlyDelete("g-1", Map.of(
                "adminLoginId", "4868", "adminPassword", "deputy-password",
                "securityKey", "unit-test-only-deletion-security-key",
                "referenceConfirmation", REF, "reason", "Duplicate case filed in error"));

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        verifyNoInteractions(grievanceRepo, deletionAuditRepo);
    }

    @Test
    void deletedReferenceNumberIsNotReusedAfterCounterInitialization() {
        int year = Year.now().getValue();
        String prefix = "CSE4/TT/" + year + "/";
        Grievance old = grievance("Resolved");
        old.setRefNo(prefix + "0002");
        GrievanceDeletionAudit deleted = new GrievanceDeletionAudit();
        deleted.setRefNo(prefix + "0010");
        when(grievanceRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(old));
        when(deletionAuditRepo.findByRefNoStartingWith(prefix)).thenReturn(List.of(deleted));
        ResponseEntity<?> result = controller.create(Map.of(
                "studentId", "s-1", "studentName", "Test Student", "batch", "Y26",
                "facultyId", "f-1", "type", "TT", "typeLabel", "Timetable",
                "category", "Academics", "description", "Testing reference allocation"
        ));
        assertEquals(prefix + "0011", ((Map<?, ?>) result.getBody()).get("refNo"));
    }
}
