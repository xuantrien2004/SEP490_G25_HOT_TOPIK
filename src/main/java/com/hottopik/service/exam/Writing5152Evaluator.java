package com.hottopik.service.exam;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đánh giá và chấm điểm tự động câu 51 và câu 52 theo chuẩn tiêu chí TOPIK II:
 * 1. Nội dung và hoàn thành nhiệm vụ (Content & task execution) — 2.5 điểm / ý:
 *    - Câu 51: Phù hợp mục đích giao tiếp (thư từ, thông báo, nhờ vả, xin lỗi, cảm ơn, hỏi đáp, mời hẹn...)
 *    - Câu 52: Logic, định nghĩa, đối lập hoặc nhân quả dựa trên gợi ý trong bài.
 *    - Trừ điểm: Hiểu đúng mạch văn nhưng từ vựng lệch ngữ cảnh trừ 1.0 - 1.5đ.
 * 2. Sử dụng ngôn ngữ (Use of language) — 2.5 điểm / ý:
 *    - Câu 51: Bắt buộc dùng đuôi kính ngữ trang trọng (-습니다/습니까?, -아/어/여야 합니다, -십시오, -바랍니다).
 *      Nếu dùng đuôi văn viết (-ㄴ/는다, -다) hoặc đuôi thân mật (-아요/어요): trừ nặng đuôi câu (trừ 1.5đ).
 *    - Câu 52: Bắt buộc dùng đuôi văn viết kết thúc bằng -ㄴ/는다, -다. Tuyệt đối KHÔNG dùng kính ngữ ở câu 52.
 *      Nếu dùng kính ngữ (-습니다, -습니까, -아요/어요, -십시오): trừ nặng đuôi câu (trừ 1.5đ).
 *    - Độ chính xác: Lỗi chính tả nhỏ hoặc chia sai trợ từ (이/가, 을/를) trừ 0.5 - 1.0đ.
 * Tổng: Mỗi câu tối đa 10 điểm (Ý ㉠ 5đ, Ý ㉡ 5đ).
 */
public final class Writing5152Evaluator {

    public record PartScore(double contentScore, double languageScore, String feedback) {
        public double totalScore() {
            return Math.min(5.0, Math.round((contentScore + languageScore) * 10.0) / 10.0);
        }
    }

    public record EvaluationResult(double totalScore, PartScore part1, PartScore part2, String summary) {}

    private static final Pattern PATTERN_SUB_MARKERS = Pattern.compile(
            "(?s)(?:㉠|\\(ㄱ\\)|\\[ㄱ\\]|ㄱ\\.|1\\.)\\s*(.*?)(?:㉡|\\(ㄴ\\)|\\[ㄴ\\]|ㄴ\\.|2\\.)\\s*(.*)"
    );

    private Writing5152Evaluator() {
    }

    public static EvaluationResult evaluate(int questionNo, String studentAnswer, String keys1, String keys2, String modelAnswer) {
        if (studentAnswer == null || studentAnswer.isBlank()) {
            return new EvaluationResult(0.0,
                    new PartScore(0.0, 0.0, "Ý ㉠: Chưa làm"),
                    new PartScore(0.0, 0.0, "Ý ㉡: Chưa làm"),
                    "Chưa làm bài");
        }

        String[] subParts = splitSubParts(studentAnswer);
        String ans1 = subParts[0];
        String ans2 = subParts[1];

        List<String> options1 = extractOptions(keys1, modelAnswer, "㉠");
        List<String> options2 = extractOptions(keys2, modelAnswer, "㉡");

        PartScore p1 = evaluatePart(questionNo, ans1, options1, "Ý ㉠");
        PartScore p2 = evaluatePart(questionNo, ans2, options2, "Ý ㉡");

        double total = Math.min(10.0, Math.round((p1.totalScore() + p2.totalScore()) * 10.0) / 10.0);
        String summary = "Ý ㉠: " + p1.totalScore() + "/5đ · Ý ㉡: " + p2.totalScore() + "/5đ";

        return new EvaluationResult(total, p1, p2, summary);
    }

    public static String[] splitSubParts(String text) {
        if (text == null || text.isBlank()) {
            return new String[]{"", ""};
        }
        String trimmed = text.trim();

        // 1. Dạng ký hiệu ㉠ và ㉡
        int idx1 = trimmed.indexOf('㉠');
        int idx2 = trimmed.indexOf('㉡');
        if (idx1 != -1 && idx2 != -1 && idx2 > idx1) {
            return new String[]{
                    cleanPrefix(trimmed.substring(idx1 + 1, idx2)),
                    cleanPrefix(trimmed.substring(idx2 + 1))
            };
        }

        // 2. Dạng (ㄱ) và (ㄴ), [ㄱ] và [ㄴ], 1. và 2., ㄱ. và ㄴ.
        Matcher m = PATTERN_SUB_MARKERS.matcher(trimmed);
        if (m.find()) {
            return new String[]{
                    cleanPrefix(m.group(1)),
                    cleanPrefix(m.group(2))
            };
        }

        // 3. Dạng xuống dòng
        String[] lines = trimmed.split("\\r?\\n");
        if (lines.length >= 2 && !lines[0].isBlank() && !lines[1].isBlank()) {
            return new String[]{
                    cleanPrefix(lines[0]),
                    cleanPrefix(lines[1])
            };
        }

        // 4. Dạng phân tách bởi dấu phẩy hoặc chấm phẩy nếu có 2 vế rõ ràng
        if (trimmed.contains(",") || trimmed.contains(";")) {
            String[] parts = trimmed.split("[,;]", 2);
            if (!parts[0].isBlank() && !parts[1].isBlank()) {
                return new String[]{
                        cleanPrefix(parts[0]),
                        cleanPrefix(parts[1])
                };
            }
        }

        // 5. Mặc định: đưa vào vế 1
        return new String[]{cleanPrefix(trimmed), ""};
    }

    private static String cleanPrefix(String s) {
        if (s == null) return "";
        return s.replaceAll("^[㉠㉡ㄱㄴ12\\(\\)\\[\\]\\.\\-:\\s]+", "").trim();
    }

    private static List<String> extractOptions(String keys, String modelAnswer, String label) {
        List<String> list = new ArrayList<>();
        if (keys != null && !keys.isBlank()) {
            for (String part : keys.split("\\|")) {
                String clean = part.trim();
                if (!clean.isBlank()) list.add(clean);
            }
        }
        if (list.isEmpty() && modelAnswer != null && !modelAnswer.isBlank()) {
            if (modelAnswer.contains(label)) {
                int start = modelAnswer.indexOf(label) + label.length();
                String otherLabel = label.equals("㉠") ? "㉡" : "㉠";
                int end = modelAnswer.indexOf(otherLabel, start);
                String sub = end != -1 ? modelAnswer.substring(start, end) : modelAnswer.substring(start);
                for (String part : sub.split("\\|")) {
                    String clean = cleanPrefix(part.trim());
                    if (!clean.isBlank()) list.add(clean);
                }
            } else {
                list.add(modelAnswer.trim());
            }
        }
        return list;
    }

    private static PartScore evaluatePart(int questionNo, String studentPart, List<String> targetOptions, String partLabel) {
        if (studentPart == null || studentPart.isBlank()) {
            return new PartScore(0.0, 0.0, partLabel + ": Chưa làm (0đ)");
        }

        String cleaned = studentPart.trim().replaceAll("[\\.?,!]+$", "").trim();
        if (cleaned.isBlank()) {
            return new PartScore(0.0, 0.0, partLabel + ": Chưa làm (0đ)");
        }

        // 1. Đánh giá Sử dụng ngôn ngữ (Language - 2.5đ)
        // a. Đuôi câu (1.5đ)
        double verbEndingScore = 1.5;
        String langNote = null;

        if (questionNo == 51) {
            // Câu 51: Bắt buộc dùng đuôi kính ngữ trang trọng (-습니다/습니까?, -ㅂ니다/ㅂ니까, -아/어/여야 합니다, -십시오, -바랍니다)
            boolean isFormalEnding = endsWithAny(cleaned,
                    "니다", "니까", "십시오", "으십시오", "바랍니다", "시기 바랍니다", "으시기 바랍니다",
                    "드립니다", "드리겠습니다", "고자 합니다", "려고 합니다", "려 합니다", "야 합니다", "해야 합니다",
                    "되어야 합니다", "하여야 합니다", "부탁드립니다", "감사합니다", "죄송합니다");

            boolean isInformalPolite = endsWithAny(cleaned, "요", "죠", "네요");
            boolean isPlainWritten = endsWithAny(cleaned, "ㄴ다", "는다", "다") && !isFormalEnding;

            if (isInformalPolite) {
                verbEndingScore = 0.0;
                langNote = "Dùng đuôi thân mật (-아요/어요) thay vì đuôi trang trọng (-습니다/습니까)";
            } else if (isPlainWritten) {
                verbEndingScore = 0.0;
                langNote = "Dùng đuôi văn viết (-ㄴ/는다) thay vì đuôi kính ngữ trang trọng (-습니다/습니까)";
            } else if (isFormalEnding) {
                verbEndingScore = 1.5;
            } else {
                verbEndingScore = 0.5;
                langNote = "Đuôi câu chưa chuẩn thể kính ngữ trang trọng";
            }
        } else if (questionNo == 52) {
            // Câu 52: Bắt buộc dùng đuôi văn viết (-ㄴ/는다, -다). Tuyệt đối không dùng kính ngữ.
            boolean hasHonorific = endsWithAny(cleaned, "니다", "니까", "요", "죠", "십시오", "으십시오", "드립니다", "바랍니다");
            boolean isPlainWritten = endsWithAny(cleaned, "ㄴ다", "는다", "다");

            if (hasHonorific) {
                verbEndingScore = 0.0;
                langNote = "Tuyệt đối không dùng đuôi kính ngữ ở câu 52, bắt buộc dùng đuôi văn viết (-ㄴ/는다, -다)";
            } else if (isPlainWritten) {
                verbEndingScore = 1.5;
            } else {
                verbEndingScore = 0.5;
                langNote = "Đuôi câu chưa đúng thể văn viết (-ㄴ/는다, -다)";
            }
        }

        // b. Độ chính xác từ vựng, chính tả, trợ từ (1.0đ)
        double bestSimilarity = calculateBestSimilarity(cleaned, targetOptions);
        double stemSimilarity = calculateBestStemSimilarity(cleaned, targetOptions);
        double effectiveSimilarity = Math.max(bestSimilarity, stemSimilarity);
        double accuracyScore;
        if (effectiveSimilarity >= 0.70) {
            accuracyScore = 1.0;
        } else if (effectiveSimilarity >= 0.40) {
            accuracyScore = 0.5; // trừ 0.5đ lỗi nhỏ
        } else {
            accuracyScore = 0.0;
        }

        double languageScore = Math.min(2.5, verbEndingScore + accuracyScore);

        // 2. Đánh giá Nội dung & Hoàn thành nhiệm vụ (Content - 2.5đ)
        double contentScore;
        if (effectiveSimilarity >= 0.70) {
            contentScore = 2.5;
        } else {
            // Kiểm tra mức độ bao phủ từ khóa chính (semantic stem overlap)
            double keywordMatch = calculateKeywordMatch(cleaned, targetOptions);
            if (keywordMatch >= 0.70) {
                contentScore = 2.5;
            } else if (keywordMatch >= 0.35) {
                contentScore = 1.5; // hiểu đúng mạch văn nhưng từ vựng hơi lệch ngữ cảnh một chút (trừ 1đ)
            } else if (keywordMatch > 0.15) {
                contentScore = 1.0;
            } else {
                contentScore = 0.0; // lạc đề
            }
        }

        double partTotal = Math.min(5.0, Math.round((contentScore + languageScore) * 10.0) / 10.0);
        String feedback = partLabel + ": " + partTotal + "/5đ (Nội dung: " + contentScore + "/2.5đ, Ngôn ngữ: " + languageScore + "/2.5đ"
                + (langNote != null ? " · " + langNote : "") + ")";

        return new PartScore(contentScore, languageScore, feedback);
    }

    private static boolean endsWithAny(String text, String... suffixes) {
        if (text == null) return false;
        String s = text.trim().replaceAll("[\\.?,!]+$", "").trim();
        for (String suffix : suffixes) {
            if (s.endsWith(suffix)) return true;
        }
        return false;
    }

    private static double calculateBestSimilarity(String student, List<String> targetOptions) {
        if (targetOptions == null || targetOptions.isEmpty()) {
            return student.length() > 2 ? 0.6 : 0.0;
        }
        double best = 0.0;
        for (String opt : targetOptions) {
            double sim = stringSimilarity(student, opt);
            if (sim > best) best = sim;
        }
        return best;
    }

    private static double calculateBestStemSimilarity(String student, List<String> targetOptions) {
        if (targetOptions == null || targetOptions.isEmpty()) return 0.5;
        String sStem = stripEnding(student);
        double best = 0.0;
        for (String opt : targetOptions) {
            String oStem = stripEnding(opt);
            double sim = stringSimilarity(sStem, oStem);
            if (sim > best) best = sim;
        }
        return best;
    }

    private static String stripEnding(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("([습납합칩됩립]니다|[습납합칩됩립]니까|습니다|습니까|니다|니까|십시오|으십시오|바랍니다|드립니다|싶습니다|싶어요|주십시오|주세요|합니다|해요|ㄴ다|는다|[난친된린]다|다)$", "").trim();
    }

    private static double stringSimilarity(String s1, String s2) {
        String c1 = s1.replaceAll("\\s+", "");
        String c2 = s2.replaceAll("\\s+", "");
        if (c1.equals(c2)) return 1.0;
        if (c1.contains(c2) || c2.contains(c1)) {
            int minLen = Math.min(c1.length(), c2.length());
            int maxLen = Math.max(c1.length(), c2.length());
            return (double) minLen / maxLen;
        }
        int dist = levenshteinDistance(c1, c2);
        int maxLen = Math.max(c1.length(), c2.length());
        if (maxLen == 0) return 1.0;
        return Math.max(0.0, 1.0 - ((double) dist / maxLen));
    }

    private static int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) costs[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }

    private static double calculateKeywordMatch(String student, List<String> targetOptions) {
        if (targetOptions == null || targetOptions.isEmpty()) return 0.5;
        double bestMatch = 0.0;
        for (String opt : targetOptions) {
            List<String> keywords = extractKeywords(opt);
            if (keywords.isEmpty()) continue;
            int hit = 0;
            for (String kw : keywords) {
                if (student.contains(kw)) hit++;
            }
            double ratio = (double) hit / keywords.size();
            if (ratio > bestMatch) bestMatch = ratio;
        }
        return bestMatch;
    }

    private static List<String> extractKeywords(String target) {
        String cleaned = target.replaceAll("[㉠㉡\\(ㄱ\\)\\(ㄴ\\)\\[ㄱ\\]\\[ㄴ\\]\\.?,!]", " ")
                .replaceAll("습니다|습니까|십시오|으십시오|바랍니다|드립니다|려고합니다|고자합니다|야합니다|해야합니다|ㄴ다|는다", " ")
                .replaceAll("을|를|이|가|에|에서|은|는|도|로|으로", " ");
        String[] tokens = cleaned.split("\\s+");
        List<String> list = new ArrayList<>();
        for (String t : tokens) {
            String tr = t.trim();
            if (tr.length() >= 2) list.add(tr);
        }
        return list;
    }
}
