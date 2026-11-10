package com.zhx.controller;

import com.zhx.exception.HttpError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 售后证据图片上传：落盘到 data/uploads，返回可直接访问的 /uploads/xxx 路径。
 */
@RestController
public class UploadController {
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_SIZE = 8 * 1024 * 1024L;

    @PostMapping("/api/uploads")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw HttpError.bad("请选择要上传的图片");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) throw HttpError.bad("仅支持图片文件");
        if (file.getSize() > MAX_SIZE) throw HttpError.bad("单张图片不能超过 8MB");

        String ext = extensionOf(file.getOriginalFilename(), contentType);
        Path dir = Paths.get("data", "uploads").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = dir.resolve(name);
        file.transferTo(target.toFile());
        return Map.of("url", "/uploads/" + name);
    }

    private static String extensionOf(String filename, String contentType) {
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) {
                String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (ALLOWED_EXT.contains(ext)) return ext;
            }
        }
        return contentType.contains("png") ? "png" : "jpg";
    }
}
