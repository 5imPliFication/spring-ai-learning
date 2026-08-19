package com.example.ai.services;

import com.example.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserRepository userRepository;

    private final Set<String> activeUserIds = ConcurrentHashMap.newKeySet();

    public void recordActivity(String userId) {
        activeUserIds.add(userId);
    }

    @Scheduled(fixedDelayString = "60000")
    @Transactional
    public void flushLastActive() {
        if (activeUserIds.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(activeUserIds);
        activeUserIds.clear();
        userRepository.bulkUpdateLastActiveAt(Instant.now(), ids);
    }
}