package com.example.ai.controller;

import com.example.ai.dto.PresignedUploadRequest;
import com.example.ai.dto.PresignedUploadResponse;
import com.example.ai.entity.User;
import com.example.ai.services.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/presign")
    public ResponseEntity<PresignedUploadResponse> presign(@Valid @RequestBody PresignedUploadRequest request,
                                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(mediaService.createPresignedUpload(request, user.getId()));
    }
}