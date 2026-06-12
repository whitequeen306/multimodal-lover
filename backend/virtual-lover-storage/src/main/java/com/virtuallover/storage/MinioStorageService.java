package com.virtuallover.storage;

import com.virtuallover.common.base.ErrorCode;
import com.virtuallover.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class MinioStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.public-base-url}")
    private String publicBaseUrl;

    private final String endpoint;

    public MinioStorageService(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey) {
        this.endpoint = endpoint;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket check failed, endpoint={}, reason={}", endpoint, e.getMessage());
        }
    }

    /**
     * 上传聊天图片到 MinIO，返回可存入数据库的访问 URL。
     */
    public String uploadChatImage(MultipartFile file, Long userId) {
        return uploadImage(file, userId, "chat-images");
    }

    /**
     * 上传用户或角色头像。
     */
    public String uploadAvatar(MultipartFile file, Long userId, String category) {
        if (!Set.of("user-avatars", "character-avatars").contains(category)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的头像类型");
        }
        return uploadImage(file, userId, category);
    }

    private String uploadImage(MultipartFile file, Long userId, String category) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的图片类型，仅允许 jpg/png/gif/webp");
        }

        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "图片大小不能超过 5MB");
        }

        String ext = getExtension(file.getOriginalFilename(), contentType);
        String key = String.format("%s/%d/%s.%s", category, userId, UUID.randomUUID(), ext);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build());
            String url = buildPublicUrl(key);
            log.info("Image uploaded to MinIO: {}", url);
            return url;
        } catch (MinioException | IOException e) {
            log.error("MinIO upload error", e);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "图片上传失败");
        } catch (Exception e) {
            log.error("MinIO upload error", e);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "图片上传失败");
        }
    }

    /**
     * 校验用户头像 URL 属于当前用户 MinIO 路径。
     */
    public void validateUserAvatarUrl(Long userId, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }
        try {
            String key = extractKey(avatarUrl.trim());
            String expectedPrefix = "user-avatars/" + userId + "/";
            if (!key.startsWith(expectedPrefix)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的头像地址");
            }
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.BAD_REQUEST) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的头像地址");
            }
            throw e;
        }
    }

    /**
     * 校验角色头像 URL：用户 MinIO 路径或内置静态资源 /avatars/。
     */
    public void validateCharacterAvatarUrl(Long userId, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }
        String trimmed = avatarUrl.trim();
        if (trimmed.startsWith("/avatars/") && !trimmed.contains("..")) {
            return;
        }
        try {
            String key = extractKey(trimmed);
            String expectedPrefix = "character-avatars/" + userId + "/";
            if (!key.startsWith(expectedPrefix)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的角色头像地址");
            }
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.BAD_REQUEST) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的角色头像地址");
            }
            throw e;
        }
    }

    /**
     * 校验聊天图片是否来自当前 MinIO 桶（防 SSRF），返回规范化 URL。
     */
    public String resolveTrustedImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return imageUrl;
        }
        String key = extractKey(imageUrl);
        if (!key.startsWith("chat-images/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的图片地址");
        }
        return buildPublicUrl(key);
    }

    /**
     * 从图片 URL 提取 MinIO object key（用于识图缓存等）。
     */
    public String toObjectKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return "";
        }
        return extractKey(imageUrl);
    }

    /**
     * 从 MinIO 读取图片字节。
     */
    public byte[] fetchImageBytes(String imageUrl) {
        byte[] bytes = tryFetchImageBytes(imageUrl);
        if (bytes == null) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "图片读取失败");
        }
        return bytes;
    }

    /**
     * 读取图片字节，失败时返回 null（供图片代理静默失败）。
     */
    public byte[] tryFetchImageBytes(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        if (imageUrl.startsWith("/avatars/")) {
            return null;
        }
        try {
            String key = extractKey(imageUrl);
            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(key).build())) {
                return inputStream.readAllBytes();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("MinIO read failed, url={}, reason={}", imageUrl, e.getMessage());
            return null;
        }
    }

    public String guessContentType(String imageUrl) {
        String key = extractKey(imageUrl).toLowerCase();
        if (key.endsWith(".png")) {
            return "image/png";
        }
        if (key.endsWith(".gif")) {
            return "image/gif";
        }
        if (key.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String buildPublicUrl(String key) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isBlank()) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "未配置 MinIO 公开访问地址");
        }
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        return base + "/" + bucket + "/" + normalizedKey;
    }

    private String extractKey(String imageUrl) {
        String trimmed = imageUrl.trim();
        if (!trimmed.contains("://")) {
            String key = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
            validateKey(key);
            return key;
        }

        try {
            URI uri = URI.create(trimmed.split("\\?")[0]);
            validateHost(uri.getHost());
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                throw invalidUrl();
            }
            String prefix = "/" + bucket + "/";
            if (!path.startsWith(prefix)) {
                throw invalidUrl();
            }
            String key = path.substring(prefix.length());
            validateKey(key);
            return key;
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw invalidUrl();
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw invalidUrl();
        }
        boolean allowed = key.startsWith("chat-images/")
                || key.startsWith("user-avatars/")
                || key.startsWith("character-avatars/");
        if (!allowed) {
            throw invalidUrl();
        }
    }

    private void validateHost(String host) {
        if (host == null || host.isBlank()) {
            throw invalidUrl();
        }
        String normalizedHost = host.toLowerCase();
        if (allowedHosts().stream().noneMatch(normalizedHost::equals)) {
            throw invalidUrl();
        }
    }

    private Set<String> allowedHosts() {
        Set<String> hosts = new java.util.HashSet<>();
        addHost(hosts, endpoint);
        addHost(hosts, publicBaseUrl);
        hosts.add("localhost");
        hosts.add("127.0.0.1");
        return hosts;
    }

    private void addHost(Set<String> hosts, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            if (uri.getHost() != null) {
                hosts.add(uri.getHost().toLowerCase());
            }
        } catch (IllegalArgumentException ignored) {
            // ignore malformed config values
        }
    }

    private BusinessException invalidUrl() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "无效的图片地址");
    }

    private String getExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            if (Set.of("jpg", "jpeg", "png", "gif", "webp").contains(ext)) {
                return ext;
            }
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
