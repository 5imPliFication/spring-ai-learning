package com.example.ai.repository;

import com.example.ai.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FriendRepository extends JpaRepository<Friend, Long> {
    List<Friend> findByUserIdAndStatus(String userId, String status);
    List<Friend> findByFriendIdAndStatus(String friendId, String status);
    Optional<Friend> findByUserIdAndFriendId(String userId, String friendId);
}
