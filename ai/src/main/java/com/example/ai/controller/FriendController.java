package com.example.ai.controller;

import com.example.ai.dto.FriendRequestPayload;
import com.example.ai.dto.FriendResponse;
import com.example.ai.dto.RoomResponse;
import com.example.ai.entity.User;
import com.example.ai.services.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/request")
    public ResponseEntity<Void> sendFriendRequest(@AuthenticationPrincipal User user,
                                                  @Valid @RequestBody FriendRequestPayload request) {
        friendService.sendFriendRequest(user, request.friendId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/accept/{friendshipId}")
    public ResponseEntity<Void> acceptFriendRequest(@AuthenticationPrincipal User user,
                                                     @PathVariable Long friendshipId) {
        friendService.acceptFriendRequest(user, friendshipId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<FriendResponse>> getFriends(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(friendService.getFriends(user));
    }

    @PostMapping("/dm")
    public ResponseEntity<RoomResponse> createOrGetDM(@AuthenticationPrincipal User user,
                                                      @RequestParam String friendId) {
        return ResponseEntity.ok(friendService.createOrGetDirectMessageRoom(user, friendId));
    }
}
