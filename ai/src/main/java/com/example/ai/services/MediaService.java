package com.example.ai.services;

import com.example.ai.dto.PresignedUploadRequest;
import com.example.ai.dto.PresignedUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MediaService {

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "image/", "audio/", "video/",
            "application/pdf", "application/msword",
            "application/vnd.ms-", "application/vnd.openxmlformats-",
            "text/plain", "application/zip", "application/json", "application/javascript"
    );

    private static final Pattern SAFE_NAME = Pattern.compile("[^A-Za-z0-9._-]");

    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif"
    );

    private static final long MAX_AVATAR_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int MIN_AVATAR_DIMENSION = 64;
    private static final int MAX_AVATAR_DIMENSION = 2048;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.r2.bucket}")
    private String bucket;

    @Value("${app.r2.public-base-url}")
    private String publicBaseUrl;

    @Value("${app.r2.upload-expiry-minutes:15}")
    private long uploadExpiryMinutes;

    @Value("${app.r2.max-upload-size-bytes:26214400}")
    private long maxUploadSizeBytes;

    public MediaService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public PresignedUploadResponse createPresignedUpload(PresignedUploadRequest request, String userId) {
        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);

        if (request.size() > maxUploadSizeBytes) {
            throw new IllegalArgumentException("File is too large. Maximum allowed size is " + (maxUploadSizeBytes / 1024 / 1024) + "MB.");
        }
        if (!isAllowedContentType(contentType)) {
            throw new IllegalArgumentException("File type is not allowed: " + request.contentType());
        }

        String key = buildObjectKey(userId, request.fileName());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(uploadExpiryMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        String mediaUrl = publicBaseUrl.replaceAll("/+$", "") + "/" + key;
        String messageType = classify(contentType);

        return new PresignedUploadResponse(uploadUrl, mediaUrl, messageType);
    }

    public void deleteObject(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) return;
        try {
            String base = publicBaseUrl.replaceAll("/+$", "");
            if (!mediaUrl.startsWith(base)) return;
            String key = mediaUrl.substring(base.length()).replaceFirst("^/", "");
            if (key.isBlank()) return;
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (Exception e) {
            // Best-effort cleanup: never block message deletion on storage failures.
            e.printStackTrace();
        }
    }

    public String uploadAvatar(MultipartFile file, String userId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No image provided");
        }
        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new IllegalArgumentException("Avatar is too large. Maximum size is " + (MAX_AVATAR_SIZE_BYTES / 1024 / 1024) + "MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Avatar must be a JPEG, PNG or GIF image.");
        }

        BufferedImage image;
        try (InputStream in = file.getInputStream()) {
            image = ImageIO.read(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the image file.");
        }
        if (image == null) {
            throw new IllegalArgumentException("Unsupported or corrupted image. Use a JPEG, PNG or GIF file.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width < MIN_AVATAR_DIMENSION || height < MIN_AVATAR_DIMENSION) {
            throw new IllegalArgumentException("Image is too small. Minimum is " + MIN_AVATAR_DIMENSION + "x" + MIN_AVATAR_DIMENSION + " pixels.");
        }
        if (width > MAX_AVATAR_DIMENSION || height > MAX_AVATAR_DIMENSION) {
            throw new IllegalArgumentException("Image is too large. Maximum is " + MAX_AVATAR_DIMENSION + "x" + MAX_AVATAR_DIMENSION + " pixels.");
        }

        String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + avatarExtension(contentType);
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the image file.");
        }

        return publicBaseUrl.replaceAll("/+$", "") + "/" + key;
    }

    private String avatarExtension(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            default -> "img";
        };
    }

    private String classify(String contentType) {
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("audio/")) return "AUDIO";
        return "FILE";
    }

    private boolean isAllowedContentType(String contentType) {
        return ALLOWED_PREFIXES.stream().anyMatch(contentType::startsWith);
    }

    private String buildObjectKey(String userId, String fileName) {
        String sanitized = SAFE_NAME.matcher(fileName).replaceAll("_");
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(sanitized.length() - 80);
        }
        return "uploads/" + userId + "/" + UUID.randomUUID() + "-" + sanitized;
    }
}