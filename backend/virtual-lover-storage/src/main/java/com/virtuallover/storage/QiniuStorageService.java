package com.virtuallover.storage;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.virtuallover.common.exception.BusinessException;
import com.virtuallover.common.base.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class QiniuStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB

    private final Auth auth;
    private final UploadManager uploadManager;

    @Value("${qiniu.bucket}")
    private String bucket;

    @Value("${qiniu.domain}")
    private String domain;

    public QiniuStorageService(
            @Value("${qiniu.access-key}") String accessKey,
            @Value("${qiniu.secret-key}") String secretKey) {
        this.auth = Auth.create(accessKey, secretKey);
        this.uploadManager = new UploadManager(new Configuration());
    }

    /**
     * 上传聊天图片到七牛云 Kodo
     */
    public String uploadChatImage(MultipartFile file, Long userId) {
        // 类型校验
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的图片类型，仅允许 jpg/png/gif/webp");
        }

        // 大小校验
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "图片大小不能超过 5MB");
        }

        // 生成文件名
        String ext = getExtension(file.getOriginalFilename(), contentType);
        String key = String.format("chat-images/%d/%s.%s", userId, UUID.randomUUID(), ext);

        try {
            String upToken = auth.uploadToken(bucket);
            Response response = uploadManager.put(file.getBytes(), key, upToken);
            if (!response.isOK()) {
                throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "七牛云上传失败");
            }
            String url = domain.endsWith("/") ? domain + key : domain + "/" + key;
            log.info("Image uploaded to Qiniu: {}", url);
            return url;
        } catch (QiniuException e) {
            log.error("Qiniu upload error", e);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "图片上传失败");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, "读取图片失败");
        }
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
