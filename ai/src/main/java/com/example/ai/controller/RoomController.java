package com.example.ai.controller;

import com.example.ai.dto.ChatMessage;
import com.example.ai.dto.CreateProtectedRoomRequest;
import com.example.ai.dto.JoinRoomRequest;
import com.example.ai.dto.MessageResponse;
import com.example.ai.dto.RoomMemberResponse;
import com.example.ai.dto.RoomResponse;
import com.example.ai.dto.UpdateRoomRequest;
import com.example.ai.entity.User;
import com.example.ai.services.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @GetMapping("/joined")
    public ResponseEntity<List<RoomResponse>> getJoinedRooms(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(roomService.getJoinedRooms(user.getId()));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(roomService.getRoom(roomId));
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

    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<RoomMemberResponse>> getMembers(@PathVariable String roomId,
                                                               @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(roomService.getMembers(roomId, user));
    }

    @GetMapping("/{roomId}/invite")
    public ResponseEntity<Map<String, String>> getInviteLink(@PathVariable String roomId,
                                                             @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(roomService.getInviteLink(roomId, user));
    }

    @PatchMapping("/{roomId}")
    public ResponseEntity<Void> updateRoom(@PathVariable String roomId,
                                           @Valid @RequestBody UpdateRoomRequest request,
                                           @AuthenticationPrincipal User user) {
        roomService.updateRoom(roomId, request, user);
        return ResponseEntity.ok().build();
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

    @DeleteMapping("/{roomId}/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String roomId,
                                              @PathVariable Long messageId,
                                              @AuthenticationPrincipal User user) {
        roomService.deleteMessage(roomId, messageId, user);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, ChatMessage.delete(messageId));
        return ResponseEntity.noContent().build();
    }
}
