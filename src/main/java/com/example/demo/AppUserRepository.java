package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByLoginIdIgnoreCaseAndRole(String loginId, String role);
    List<AppUser> findByLoginIdIgnoreCase(String loginId);
    List<AppUser> findByRole(String role);
    boolean existsByLoginIdIgnoreCase(String loginId);
    long countByRole(String role);
}
