package com.example.ai.repository;

import com.example.ai.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, String> {
    Page<Room> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);
    Page<Room> findByNameContainingIgnoreCaseAndDeletedAtIsNull(String name, Pageable pageable);
    List<Room> findByCreatedByAndDeletedAtIsNullOrderByCreatedAtDesc(String createdBy);

    @Query("SELECT r FROM Room r WHERE r.deletedAt IS NULL AND r.isPrivate = false ORDER BY r.createdAt DESC")
    Page<Room> findPublicRooms(Pageable pageable);

    @Query("SELECT r FROM Room r WHERE r.deletedAt IS NULL AND r.isPrivate = false " +
           "AND LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY r.createdAt DESC")
    Page<Room> findPublicRoomsByNameContaining(@Param("name") String name, Pageable pageable);

    @Query("SELECT r FROM Room r JOIN RoomMember m ON m.roomId = r.id " +
           "WHERE m.userId = :userId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<Room> findJoinedRoomsByUserId(@Param("userId") String userId);

    long countByDeletedAtIsNull();
    Optional<Room> findByIdAndDeletedAtIsNull(String id);
}