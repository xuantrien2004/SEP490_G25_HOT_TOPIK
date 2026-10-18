package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;

import java.util.*;

/**
 * Thư viện khuôn khởi tạo. Mã TPL là mã nội bộ.
 * Nhiều dạng câu chuẩn (I-N6, II-N6…) có thể trỏ cùng một khuôn khi cách hiển thị giống nhau.
 * Khoảng số câu nằm ở QuestionTypeCatalog, không phải ràng buộc của khuôn.
 */
public final class QuestionTemplateCatalog {

    public record Seed(String code, Track track, Skill skill, String name, String description,
                       int zoneMin, int zoneMax, String tags,
                       DisplayMode displayMode, AnswerType answerType,
                       ResourceScope audioScope, boolean audioRequired,
                       ResourceScope imageScope, boolean imageRequired,
                       ResourceScope passageScope, boolean passageRequired,
                       ResourceScope scriptScope, Integer optionCount,
                       int childMin, int childMax, GradingMode grading,
                       Integer minChars, Integer maxChars, String rubric) {
    }

    private static final List<Seed> SEEDS = new ArrayList<>();
    private static final Map<String, String> TYPE_TO_TEMPLATE = new LinkedHashMap<>();

    static {
        lis("TPL1-LIS-01", "Nghe câu hỏi, chọn câu trả lời", "Nghe một câu hỏi ngắn và chọn câu trả lời trong bốn phương án.",
                1, 1, DisplayMode.SINGLE_CHOICE, "I-N1");
        lis("TPL1-LIS-02", "Nghe và chọn lời nói tiếp theo", "Nghe hội thoại rồi chọn câu nói tiếp theo.",
                1, 1, DisplayMode.SINGLE_CHOICE, "I-N2");
        lis("TPL1-LIS-03", "Nghe, xác định địa điểm", "Nghe và chọn địa điểm phù hợp.",
                1, 1, DisplayMode.SINGLE_CHOICE, "I-N3");
        lis("TPL1-LIS-04", "Nghe, xác định chủ đề", "Nghe và chọn chủ đề cuộc nói chuyện.",
                1, 1, DisplayMode.SINGLE_CHOICE, "I-N4");
        lis("TPL1-LIS-05", "Nghe và chọn tranh", "Nghe hội thoại và chọn tranh đúng. Ảnh là bốn phương án.",
                1, 2, DisplayMode.IMAGE_CHOICE, "I-N5");
        lis("TPL1-LIS-06", "Nghe, chọn nội dung phù hợp", "Nghe và chọn câu có nội dung đúng với bài nghe.",
                2, 2, DisplayMode.SINGLE_CHOICE, "I-N6");
        lis("TPL1-LIS-07", "Nghe và xác định ý chính", "Nghe và chọn ý chính của người nói.",
                2, 2, DisplayMode.SINGLE_CHOICE, "I-N7");
        lisGroup("TPL1-LIS-08", "Nghe đoạn dài và trả lời nhiều câu", "Một file nghe dùng cho các câu con trong cùng bài. Mỗi câu con vẫn là một câu.",
                2, 2, 2, 2, "I-N8");

        rea("TPL1-REA-01", "Xác định nội dung câu hoặc văn bản ngắn", "Đọc câu hoặc đoạn rất ngắn và chọn nội dung.",
                1, 1, DisplayMode.SINGLE_CHOICE, 1, 1, "I-D1");
        rea("TPL1-REA-02", "Điền từ vào chỗ trống", "Chọn từ hoặc ngữ pháp điền vào chỗ trống. Đáp án là một trong bốn phương án.",
                1, 1, DisplayMode.FILL_BLANK, 1, 1, "I-D2");
        rea("TPL1-REA-03", "Chọn nội dung không đúng", "Đọc thông báo hoặc quảng cáo và chọn ý không đúng.",
                1, 1, DisplayMode.SINGLE_CHOICE, 1, 1, "I-D3");
        rea("TPL1-REA-04", "Chọn nội dung đúng", "Chọn ý trùng với nội dung đoạn văn.",
                1, 2, DisplayMode.SINGLE_CHOICE, 1, 1, "I-D4");
        rea("TPL1-REA-05", "Xác định nội dung chính", "Đọc và chọn ý chính.",
                2, 2, DisplayMode.SINGLE_CHOICE, 1, 1, "I-D5");
        rea("TPL1-REA-06", "Đọc đoạn văn và trả lời nhiều câu", "Một đoạn văn dùng chung. Các câu con tính riêng từng câu.",
                2, 2, DisplayMode.SINGLE_CHOICE, 2, 2, "I-D6", "I-D9");
        rea("TPL1-REA-07", "Sắp xếp câu", "Đề chuẩn chọn một trong bốn thứ tự có sẵn, không kéo thả.",
                2, 2, DisplayMode.ORDERING, 1, 1, "I-D7");
        rea("TPL1-REA-08", "Đọc văn bản dài và trả lời nhiều câu", "Đoạn dài dùng chung cho các câu con.",
                2, 2, DisplayMode.SINGLE_CHOICE, 2, 2, "I-D10");
        rea("TPL1-REA-09", "Chèn câu vào đoạn văn", "Chọn vị trí thích hợp để chèn câu cho sẵn. Tách riêng vì cách chọn khác dạng đọc nhiều câu.",
                2, 2, DisplayMode.INSERT, 2, 2, "I-D8");

        lis("TPL2-LIS-01", "Nghe và chọn tranh hoặc biểu đồ", "Nghe và chọn tranh hoặc biểu đồ đúng.",
                3, 3, DisplayMode.IMAGE_CHOICE, "II-N1");
        lis("TPL2-LIS-02", "Nghe và chọn lời nói tiếp theo", "Nghe hội thoại và chọn câu nói tiếp.",
                3, 3, DisplayMode.SINGLE_CHOICE, "II-N2");
        lis("TPL2-LIS-03", "Nghe và chọn hành động tiếp theo", "Chọn việc người nói sẽ làm tiếp.",
                3, 3, DisplayMode.SINGLE_CHOICE, "II-N3");
        lis("TPL2-LIS-04", "Nghe và chọn nội dung đúng", "Chọn câu khớp nội dung đã nghe.",
                3, 3, DisplayMode.SINGLE_CHOICE, "II-N4");
        lis("TPL2-LIS-05", "Nghe và xác định ý chính", "Chọn ý chính của người nói.",
                3, 4, DisplayMode.SINGLE_CHOICE, "II-N5");
        lisGroup("TPL2-LIS-06", "Nghe đoạn dài và trả lời nhiều câu",
                "Một file nghe cho cả bài. Hội thoại đời sống, phỏng vấn và bài giảng dùng chung khuôn này vì cách hiển thị giống nhau.",
                4, 6, 2, 2, "II-N6", "II-N7", "II-N8");

        rea("TPL2-REA-01", "Điền từ hoặc ngữ pháp vào chỗ trống", "Chọn ngữ pháp hoặc từ điền vào chỗ trống.",
                3, 3, DisplayMode.FILL_BLANK, 1, 1, "II-D1");
        rea("TPL2-REA-02", "Chọn từ hoặc cụm từ đồng nghĩa", "Chọn cụm có nghĩa gần với phần gạch chân.",
                3, 3, DisplayMode.SINGLE_CHOICE, 1, 1, "II-D2");
        rea("TPL2-REA-03", "Xác định chủ đề văn bản ngắn", "Biển báo, quảng cáo: chọn bài viết nói về việc gì.",
                3, 3, DisplayMode.SINGLE_CHOICE, 1, 1, "II-D3");
        reaChart("TPL2-REA-04", "Chọn nội dung phù hợp với văn bản hoặc biểu đồ",
                "Biểu đồ hoặc bảng, nếu có, nằm ở nhóm câu. Không phải bài nào cũng có ảnh.",
                3, 3, "II-D4");
        rea("TPL2-REA-05", "Sắp xếp câu hoặc đoạn", "Chọn một trong bốn thứ tự sắp sẵn.",
                3, 4, DisplayMode.ORDERING, 1, 1, "II-D5");
        rea("TPL2-REA-06", "Điền từ vào đoạn văn",
                "Đề chuẩn gộp câu 16–18 và 28–31 vào cùng một dạng vì cùng cách điền. Khuôn TPL2-REA-09 để dành khi muốn tách bộ ôn.",
                4, 5, DisplayMode.FILL_BLANK, 1, 1, "II-D6");
        rea("TPL2-REA-07", "Đọc đoạn văn và trả lời nhiều câu", "Một đoạn văn, nhiều câu con.",
                4, 4, DisplayMode.SINGLE_CHOICE, 2, 2, "II-D7");
        rea("TPL2-REA-08", "Giải thích tiêu đề bài báo", "Đọc tiêu đề báo và chọn câu giải thích đúng.",
                4, 4, DisplayMode.SINGLE_CHOICE, 1, 1, "II-D8");
        bare("TPL2-REA-09", Track.TOPIK_II, Skill.READING, "Điền từ vào đoạn văn theo ngữ cảnh",
                "Cùng cách hiển thị với TPL2-REA-06. Chưa gán số câu chuẩn, vì đề TOPIK II đang dùng một dạng cho cả hai cụm câu.",
                4, 5, "điền,ngữ cảnh", DisplayMode.FILL_BLANK, AnswerType.CHOICE_INDEX,
                ResourceScope.NONE, false, ResourceScope.NONE, false, ResourceScope.GROUP, true, ResourceScope.NONE,
                4, 1, 1, GradingMode.AUTO, null, null, null);
        rea("TPL2-REA-10", "Chọn nội dung đúng", "Đọc đoạn học thuật và chọn ý trùng nội dung.",
                5, 5, DisplayMode.SINGLE_CHOICE, 1, 1, "II-D9");
        rea("TPL2-REA-11", "Xác định chủ đề hoặc ý chính", "Chọn chủ đề của đoạn văn.",
                5, 5, DisplayMode.SINGLE_CHOICE, 1, 1, "II-D10");
        rea("TPL2-REA-12", "Chọn vị trí chèn câu", "Chọn chỗ thích hợp để đặt câu cho sẵn.",
                5, 5, DisplayMode.INSERT, 1, 1, "II-D11");
        rea("TPL2-REA-13", "Đọc bài dài và trả lời nhiều câu", "Bài dài dùng chung. Có bài 2 câu và bài 3 câu, số câu con không cố định một kiểu.",
                5, 6, DisplayMode.SINGLE_CHOICE, 2, 3, "II-D12");

        wri("TPL2-WRI-01", "Hoàn thành văn bản thực dụng ngắn",
                "Điền một câu vào mỗi chỗ ㉠ và ㉡. Chấm bằng các đáp án chấp nhận.",
                3, 3, AnswerType.ACCEPT_KEYS, GradingMode.AUTO, null, null, null, "II-V1");
        wri("TPL2-WRI-02", "Hoàn thành đoạn văn",
                "Điền một câu vào mỗi chỗ trống của đoạn giải thích. Chấm bằng các đáp án chấp nhận.",
                3, 4, AnswerType.ACCEPT_KEYS, GradingMode.AUTO, null, null, null, "II-V2");
        wri("TPL2-WRI-03", "Viết mô tả biểu đồ hoặc bảng số liệu",
                "Bài viết 200–300 chữ tiếng Hàn theo biểu đồ. Giới hạn lấy từ hướng dẫn đề chuẩn, không phải điểm từng câu.",
                4, 4, AnswerType.FREE_TEXT, GradingMode.AI_ASSISTED, 200, 300,
                "Có tiêu đề số liệu, xu hướng chính và so sánh. Không chép lại đề.", "II-V3");
        wri("TPL2-WRI-04", "Viết bài luận theo chủ đề",
                "Bài luận 600–700 chữ tiếng Hàn. Giới hạn lấy từ hướng dẫn đề chuẩn.",
                5, 6, AnswerType.FREE_TEXT, GradingMode.AI_ASSISTED, 600, 700,
                "Có mở bài, thân bài và kết bài. Người chấm hoặc AI hỗ trợ theo tiêu chí này, kết quả lịch sử không tự đổi khi sửa khuôn.",
                "II-V4");
    }

    private QuestionTemplateCatalog() {
    }

    public static List<Seed> all() {
        return List.copyOf(SEEDS);
    }

    public static String templateCode(String typeCode) {
        return TYPE_TO_TEMPLATE.get(typeCode);
    }

    public static List<String> typeCodes(String templateCode) {
        List<String> codes = new ArrayList<>();
        TYPE_TO_TEMPLATE.forEach((type, template) -> {
            if (template.equals(templateCode)) codes.add(type);
        });
        return codes;
    }

    private static void lis(String code, String name, String description, int zoneMin, int zoneMax,
                            DisplayMode display, String typeCode) {
        boolean image = display == DisplayMode.IMAGE_CHOICE;
        Track track = code.startsWith("TPL2") ? Track.TOPIK_II : Track.TOPIK_I;
        add(new Seed(code, track, Skill.LISTENING, name, description, zoneMin, zoneMax, "nghe",
                display, AnswerType.CHOICE_INDEX,
                ResourceScope.GROUP, true,
                image ? ResourceScope.OPTIONS : ResourceScope.NONE, image,
                ResourceScope.NONE, false, ResourceScope.GROUP, 4,
                1, 1, GradingMode.AUTO, null, null, null), typeCode);
    }

    private static void lisGroup(String code, String name, String description, int zoneMin, int zoneMax,
                                 int childMin, int childMax, String... typeCodes) {
        Track track = typeCodes[0].startsWith("II") ? Track.TOPIK_II : Track.TOPIK_I;
        add(new Seed(code, track, Skill.LISTENING, name, description, zoneMin, zoneMax, "nghe,nhóm",
                DisplayMode.SINGLE_CHOICE, AnswerType.CHOICE_INDEX,
                ResourceScope.GROUP, true, ResourceScope.NONE, false,
                ResourceScope.NONE, false, ResourceScope.GROUP, 4,
                childMin, childMax, GradingMode.AUTO, null, null, null), typeCodes);
    }

    private static void rea(String code, String name, String description, int zoneMin, int zoneMax,
                            DisplayMode display, int childMin, int childMax, String... typeCodes) {
        Track track = code.startsWith("TPL2") ? Track.TOPIK_II : Track.TOPIK_I;
        add(new Seed(code, track, Skill.READING, name, description, zoneMin, zoneMax, "đọc",
                display, AnswerType.CHOICE_INDEX,
                ResourceScope.NONE, false, ResourceScope.NONE, false,
                ResourceScope.GROUP, true, ResourceScope.NONE, 4,
                childMin, childMax, GradingMode.AUTO, null, null, null), typeCodes);
    }

    private static void reaChart(String code, String name, String description, int zoneMin, int zoneMax, String typeCode) {
        add(new Seed(code, Track.TOPIK_II, Skill.READING, name, description, zoneMin, zoneMax, "đọc,biểu đồ",
                DisplayMode.SINGLE_CHOICE, AnswerType.CHOICE_INDEX,
                ResourceScope.NONE, false, ResourceScope.GROUP, false,
                ResourceScope.GROUP, true, ResourceScope.NONE, 4,
                1, 1, GradingMode.AUTO, null, null, null), typeCode);
    }

    private static void bare(String code, Track track, Skill skill, String name, String description,
                             int zoneMin, int zoneMax, String tags, DisplayMode display, AnswerType answer,
                             ResourceScope audio, boolean audioRequired, ResourceScope image, boolean imageRequired,
                             ResourceScope passage, boolean passageRequired, ResourceScope script,
                             Integer optionCount, int childMin, int childMax, GradingMode grading,
                             Integer minChars, Integer maxChars, String rubric) {
        SEEDS.add(new Seed(code, track, skill, name, description, zoneMin, zoneMax, tags,
                display, answer, audio, audioRequired, image, imageRequired, passage, passageRequired, script,
                optionCount, childMin, childMax, grading, minChars, maxChars, rubric));
    }

    private static void wri(String code, String name, String description, int zoneMin, int zoneMax,
                            AnswerType answer, GradingMode grading, Integer minChars, Integer maxChars,
                            String rubric, String typeCode) {
        boolean chart = "TPL2-WRI-03".equals(code);
        add(new Seed(code, Track.TOPIK_II, Skill.WRITING, name, description, zoneMin, zoneMax, "viết",
                DisplayMode.TEXT_INPUT, answer,
                ResourceScope.NONE, false,
                chart ? ResourceScope.GROUP : ResourceScope.NONE, chart,
                ResourceScope.GROUP, true, ResourceScope.NONE, null,
                1, 1, grading, minChars, maxChars, rubric), typeCode);
    }

    private static void add(Seed seed, String... typeCodes) {
        SEEDS.add(seed);
        for (String typeCode : typeCodes) {
            if (TYPE_TO_TEMPLATE.put(typeCode, seed.code()) != null) {
                throw new IllegalStateException("Dạng " + typeCode + " đang trỏ hai khuôn.");
            }
        }
    }
}
