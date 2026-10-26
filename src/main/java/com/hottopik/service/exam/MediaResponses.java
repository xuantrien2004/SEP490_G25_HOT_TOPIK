package com.hottopik.service.exam;

import com.hottopik.entity.exam.MediaKind;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Ghi file nghe và ảnh thẳng ra response.
 * Không đi qua bộ chuyển Resource của Spring, vì bộ đó gắn {@code ;charset=UTF-8}
 * vào audio/mpeg và image/png khiến thẻ audio/img không phát được.
 */
public final class MediaResponses {

    private MediaResponses() {
    }

    public static String contentType(String filename, MediaKind kind) {
        return switch (MediaStorageService.extension(filename)) {
            case "mp3" -> "audio/mpeg";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> kind == MediaKind.AUDIO ? "audio/mpeg" : "application/octet-stream";
        };
    }

    public static void play(Path path, String filename, MediaKind kind, String rangeHeader,
                            HttpServletResponse response) throws IOException {
        long size = Files.size(path);
        String type = contentType(filename, kind);
        long start = 0;
        long end = size == 0 ? 0 : size - 1;
        boolean partial = false;
        if (rangeHeader != null && rangeHeader.startsWith("bytes=") && size > 0) {
            try {
                List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                if (!ranges.isEmpty()) {
                    HttpRange range = ranges.get(0);
                    start = range.getRangeStart(size);
                    end = range.getRangeEnd(size);
                    if (start >= size || start > end) {
                        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
                        response.flushBuffer();
                        return;
                    }
                    partial = true;
                }
            } catch (IllegalArgumentException ignored) {
                start = 0;
                end = size - 1;
            }
        }
        response.setStatus(partial ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Bộ lọc mã hóa ký tự đặt UTF-8 cho mọi response. Nếu để nguyên, audio/mpeg
        // thành audio/mpeg;charset=UTF-8 và trình duyệt không phát.
        response.setCharacterEncoding(null);
        response.setContentType(type);
        long length = size == 0 ? 0 : end - start + 1;
        if (partial) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + size);
        }
        response.setContentLengthLong(length);
        if (length > 0) {
            try (InputStream in = Files.newInputStream(path); OutputStream out = response.getOutputStream()) {
                in.skipNBytes(start);
                byte[] buf = new byte[8192];
                long remaining = length;
                while (remaining > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                    if (n < 0) break;
                    out.write(buf, 0, n);
                    remaining -= n;
                }
            }
        }
        response.flushBuffer();
    }
}
