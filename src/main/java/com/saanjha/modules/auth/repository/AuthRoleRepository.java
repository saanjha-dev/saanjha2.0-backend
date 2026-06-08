package com.saanjha.modules.auth.repository;

import com.saanjha.modules.auth.entity.AuthRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AuthRoleRepository extends JpaRepository<AuthRole, UUID> {
    Optional<AuthRole> findByName(String name);
}