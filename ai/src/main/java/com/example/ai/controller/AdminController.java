package com.example.ai.controller;

import com.example.ai.dto.AdminDashboardStats;
import com.example.ai.entity.Room;
import com.example.ai.entity.RoomMessage;
import com.example.ai.entity.User;
import com.example.ai.services.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard/stats")
    public ResponseEntity<AdminDashboardStats> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    // Users Management
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId,
                                           @RequestParam(defaultValue = "false") boolean hard) {
        adminService.deleteUser(userId, hard);
        return ResponseEntity.noContent().build();
    }

    // Rooms Management
    @GetMapping("/rooms")
    public ResponseEntity<List<Room>> getAllRooms() {
        return ResponseEntity.ok(adminService.getAllRooms());
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId,
                                           @RequestParam(defaultValue = "false") boolean hard) {
        adminService.deleteRoom(roomId, hard);
        return ResponseEntity.noContent().build();
    }

    // Messages Management
    @GetMapping("/messages")
    public ResponseEntity<List<RoomMessage>> getAllMessages() {
        return ResponseEntity.ok(adminService.getAllMessages());
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId,
                                              @RequestParam(defaultValue = "false") boolean hard) {
        adminService.deleteMessage(messageId, hard);
        return ResponseEntity.noContent().build();
    }
}
