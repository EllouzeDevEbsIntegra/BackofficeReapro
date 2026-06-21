package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    List<UserPermission> findByUserId(Long userId);

    boolean existsByUserIdAndPermissionId(Long userId, Long permissionId);
}
