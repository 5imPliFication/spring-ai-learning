package com.example.ai.repository;

import com.example.ai.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    List<RoomMember> findByRoomId(String roomId);
    List<RoomMember> findByUserId(String userId);
    Optional<RoomMember> findByRoomIdAndUserId(String roomId, String userId);
    boolean existsByRoomIdAndUserId(String roomId, String userId);
    void deleteByRoomIdAndUserId(String roomId, String userId);
    void deleteByRoomId(String roomId);
}
