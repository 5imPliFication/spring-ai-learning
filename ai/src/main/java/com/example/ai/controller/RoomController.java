package com.example.ai.controller;

import com.example.ai.dto.CreateProtectedRoomRequest;
import com.example.ai.dto.JoinRoomRequest;
import com.example.ai.dto.MessageResponse;
import com.example.ai.dto.RoomResponse;
import com.example.ai.entity.User;
import com.example.ai.services.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/joined")
    public ResponseEntity<List<RoomResponse>> getJoinedRooms(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(roomService.getJoinedRooms(user.getId()));
    }

    @GetMapping("/search")
    public ResponseEntity<List<RoomResponse>> searchRooms(@RequestParam String query) {
        return ResponseEntity.ok(roomService.searchRooms(query));
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateProtectedRoomRequest request,
                                                    @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(roomService.createRoom(request, user.getId()));
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(@PathVariable String roomId) {
        return ResponseEntity.ok(roomService.getMessages(roomId));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<Void> joinRoom(@PathVariable String roomId,
                                         @RequestBody(required = false) JoinRoomRequest request,
                                         @AuthenticationPrincipal User user) {
        String password = request != null ? request.password() : null;
        roomService.joinRoom(roomId, user.getId(), password);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId,
                                           @AuthenticationPrincipal User user) {
        roomService.deleteRoom(roomId, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roomId}/members/{targetUserId}")
    public ResponseEntity<Void> kickMember(@PathVariable String roomId,
                                           @PathVariable String targetUserId,
                                           @AuthenticationPrincipal User user) {
        roomService.kickMember(roomId, targetUserId, user);
        return ResponseEntity.noContent().build();
    }
}
