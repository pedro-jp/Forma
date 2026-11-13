package com.forma.backend.repository;

import com.forma.backend.entity.DeletedAccountTombstoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletedAccountTombstoneRepository extends JpaRepository<DeletedAccountTombstoneEntity, String> {
}
