package com.hottopik.service.exam;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;

/**
 * Lưu file của nguồn đề lên đĩa (thư mục cấu hình hottopik.storage.dir).
 * Khi lên production chỉ cần thay lớp này bằng bản object storage (S3/MinIO), phần còn lại giữ nguyên.
 */
@Service
public class MediaStorageService {

    public static final long MAX_EXCEL_BYTES = 10L * 1024 * 1024;
    public static final long MAX_AUDIO_BYTES = 15L * 1024 * 1024;
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    public static final long MAX_ZIP_BYTES = 300L * 1024 * 1024;

    private final Path root;

    public MediaStorageService(@Value("${hottopik.storage.dir:./data/storage}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    public Path sourceDir(Long sourceId) throws IOException {
        Path dir = root.resolve("sources").resolve(String.valueOf(sourceId));
        Files.createDirectories(dir.resolve("media"));
        Files.createDirectories(dir.resolve("excel"));
        return dir;
    }

    public Path storeExcel(Long sourceId, MultipartFile file) throws IOException {
        Path target = sourceDir(sourceId).resolve("excel").resolve(UUID.randomUUID() + ".xlsx");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    public Path storeMedia(Long sourceId, String filename, InputStream in) throws IOException {
        Path dir = sourceDir(sourceId).resolve("media");
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir)) throw new IOException("Tên file không hợp lệ: " + filename);
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** Giá trị lưu DB: đường dẫn tương đối so với thư mục gốc, để đổi máy / đổi thư mục chạy không làm hỏng link file. */
    public String toStored(Path p) {
        Path abs = p.toAbsolutePath().normalize();
        return abs.startsWith(root) ? root.relativize(abs).toString().replace('\\', '/') : abs.toString();
    }

    public Path resolveStored(String stored) {
        Path p = Paths.get(stored);
        if (!p.isAbsolute()) return root.resolve(p).normalize();
        if (Files.exists(p)) return p;
        // Bản ghi cũ lưu đường dẫn tuyệt đối: thử lấy phần sau "sources/" dưới thư mục gốc hiện tại.
        String s = stored.replace('\\', '/');
        int i = s.lastIndexOf("/sources/");
        return i >= 0 ? root.resolve(s.substring(i + 1)).normalize() : p;
    }

    public Path resolveMedia(Long sourceId, String filename) throws IOException {
        Path dir = sourceDir(sourceId).resolve("media");
        Path p = dir.resolve(filename).normalize();
        if (!p.startsWith(dir)) throw new IOException("Đường dẫn không hợp lệ");
        return p;
    }

    public void deleteSourceDir(Long sourceId) {
        Path dir = root.resolve("sources").resolve(String.valueOf(sourceId));
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // file đang bị khoá (Windows): bỏ qua, không ảnh hưởng dữ liệu
                }
            });
        } catch (IOException ignored) {
            // thư mục đã bị xoá giữa chừng
        }
    }

    /** Bỏ đường dẫn thư mục (file trong zip), chỉ giữ tên file. */
    public static String baseName(String name) {
        if (name == null) return null;
        String n = name.replace('\\', '/');
        int i = n.lastIndexOf('/');
        return (i >= 0 ? n.substring(i + 1) : n).trim();
    }

    public static String extension(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isAudio(String name) {
        return extension(name).equals("mp3");
    }

    public static boolean isImage(String name) {
        return switch (extension(name)) {
            case "png", "jpg", "jpeg", "webp", "gif" -> true;
            default -> false;
        };
    }
}
