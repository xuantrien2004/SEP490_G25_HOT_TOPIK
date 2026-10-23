package com.hottopik.service.exam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Quy tắc của một bản lộ trình: mốc ôn, ba chặng, số lần học và số câu mỗi bài. */
public final class ProgramRules {

    private ProgramRules() {
    }

    public static int[] reviews(String raw) {
        int[] n = parse(raw, 1, 3, "Mốc ôn");
        for (int i = 0; i < n.length; i++) {
            if (n[i] < 1 || n[i] > 60) {
                throw new ExamDomainException("Mỗi mốc ôn từ 1 đến 60 ngày.");
            }
            if (i > 0 && n[i] <= n[i - 1]) {
                throw new ExamDomainException("Các mốc ôn phải tăng dần, ví dụ 2, 7, 14.");
            }
        }
        return n;
    }

    public static int[] phases(String raw) {
        int[] n = parse(raw, 3, 3, "Tỉ lệ ba chặng");
        int sum = 0;
        for (int v : n) {
            if (v < 1 || v > 90) {
                throw new ExamDomainException("Mỗi chặng chiếm từ 1% đến 90%.");
            }
            sum += v;
        }
        if (sum != 100) {
            throw new ExamDomainException("Ba chặng Làm quen, Tăng tốc và Nước rút phải cộng đủ 100%.");
        }
        return n;
    }

    public static void checkCounts(int sessions, int questions, int weekly, int[] reviews) {
        if (sessions < 1 || sessions > 4) {
            throw new ExamDomainException("Mỗi dạng học từ 1 đến 4 lần.");
        }
        if (questions < 5 || questions > 30) {
            throw new ExamDomainException("Mỗi bài lấy từ 5 đến 30 câu.");
        }
        if (weekly < 5 || weekly > 40) {
            throw new ExamDomainException("Ôn tuần lấy từ 5 đến 40 câu.");
        }
        int needed = sessions - 1;
        if (reviews.length < needed) {
            throw new ExamDomainException("Cần ít nhất " + needed + " mốc ôn cho " + sessions + " lần học mỗi dạng.");
        }
    }

    public static String join(int[] values) {
        return Arrays.stream(values).mapToObj(Integer::toString).collect(Collectors.joining(","));
    }

    private static int[] parse(String raw, int min, int max, String label) {
        if (raw == null || raw.isBlank()) {
            throw new ExamDomainException(label + " chưa có số.");
        }
        List<Integer> nums = new ArrayList<>();
        String values = raw.trim();
        // Existing SQL Server versions store these lists as JSON arrays.
        if (values.startsWith("[") && values.endsWith("]")) {
            values = values.substring(1, values.length() - 1);
        }
        for (String part : values.split("[,;\\s]+")) {
            if (part.isBlank()) continue;
            try {
                nums.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException e) {
                throw new ExamDomainException(label + " chỉ gồm các số, cách nhau bởi dấu phẩy.");
            }
        }
        if (nums.size() < min || nums.size() > max) {
            throw new ExamDomainException(label + " cần từ " + min + " đến " + max + " số.");
        }
        return nums.stream().mapToInt(Integer::intValue).toArray();
    }
}
