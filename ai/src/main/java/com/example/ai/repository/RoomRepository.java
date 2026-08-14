package com.example.ai.repository;

import com.example.ai.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, String> {
    List<Room> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<Room> findByNameContainingIgnoreCaseAndDeletedAtIsNull(String name);
    long countByDeletedAtIsNull();
    Optional<Room> findByIdAndDeletedAtIsNull(String id);
}
