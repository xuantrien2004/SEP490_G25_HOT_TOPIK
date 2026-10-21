package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Nguồn sự thật cho 42 dạng câu và cấu trúc đề TOPIK (BRD 7.1, 11.3).
 * Mọi nơi (nhập Excel, soạn đề, tự sinh Mini Test, lộ trình) đều tra dạng câu qua lớp này.
 */
public final class QuestionTypeCatalog {

    public record Section(Skill skill, int firstNo, int lastNo, int minutes) {
        public int count() {
            return lastNo - firstNo + 1;
        }

        public boolean contains(int no) {
            return no >= firstNo && no <= lastNo;
        }
    }

    private static final List<QuestionTypeDef> TYPES = new ArrayList<>();
    private static final Map<String, QuestionTypeDef> BY_CODE = new LinkedHashMap<>();

    static {
        // ---------- TOPIK I · Nghe (1–30) ----------
        single("I-N1", Track.TOPIK_I, Skill.LISTENING, "Chọn câu trả lời phù hợp", 1, 1, "1-4",
                "※ [1~4] 다음을 듣고 <보기>와 같이 물음에 맞는 대답을 고르십시오.");
        single("I-N2", Track.TOPIK_I, Skill.LISTENING, "Chọn câu nói tiếp theo", 1, 1, "5-6",
                "※ [5~6] 다음을 듣고 <보기>와 같이 이어지는 말을 고르십시오.");
        single("I-N3", Track.TOPIK_I, Skill.LISTENING, "Đoán địa điểm", 1, 1, "7-10",
                "※ [7~10] 여기는 어디입니까? <보기>와 같이 알맞은 것을 고르십시오.");
        single("I-N4", Track.TOPIK_I, Skill.LISTENING, "Đoán chủ đề cuộc nói chuyện", 1, 1, "11-14",
                "※ [11~14] 다음은 무엇에 대해 말하고 있습니까? <보기>와 같이 알맞은 것을 고르십시오.");
        def("I-N5", Track.TOPIK_I, Skill.LISTENING, "Chọn tranh phù hợp", 1, 2, "15|16",
                "※ [15~16] 다음 대화를 듣고 알맞은 그림을 고르십시오.", false, true);
        single("I-N6", Track.TOPIK_I, Skill.LISTENING, "Chọn nội dung giống hội thoại", 2, 2, "17-21",
                "※ [17~21] 다음을 듣고 <보기>와 같이 대화 내용과 같은 것을 고르십시오.");
        single("I-N7", Track.TOPIK_I, Skill.LISTENING, "Ý chính của người nói", 2, 2, "22-24",
                "※ [22~24] 다음을 듣고 여자의 중심 생각을 고르십시오.");
        grouped("I-N8", Track.TOPIK_I, Skill.LISTENING, "Một đoạn nghe, 2 câu hỏi", 2, 2, "25-26|27-28|29-30",
                "※ [25~30] 다음을 듣고 물음에 답하십시오.");

        // ---------- TOPIK I · Đọc (31–70) ----------
        def("I-D1", Track.TOPIK_I, Skill.READING, "Câu văn nói về chủ đề gì", 1, 1, "31|32|33",
                "※ [31~33] 무엇에 대한 이야기입니까? <보기>와 같이 알맞은 것을 고르십시오.", false, false);
        def("I-D2", Track.TOPIK_I, Skill.READING, "Điền từ hoặc ngữ pháp", 1, 1, "34|35|36|37|38|39",
                "※ [34~39] <보기>와 같이 (    )에 들어갈 가장 알맞은 것을 고르십시오.", false, false);
        single("I-D3", Track.TOPIK_I, Skill.READING, "Thông báo, quảng cáo: chọn ý không đúng", 1, 1, "40-42",
                "※ [40~42] 다음을 읽고 맞지 않는 것을 고르십시오.");
        single("I-D4", Track.TOPIK_I, Skill.READING, "Chọn nội dung giống đoạn văn", 1, 2, "43-45",
                "※ [43~45] 다음의 내용과 같은 것을 고르십시오.");
        single("I-D5", Track.TOPIK_I, Skill.READING, "Chọn ý chính", 2, 2, "46-48",
                "※ [46~48] 다음을 읽고 중심 생각을 고르십시오.");
        grouped("I-D6", Track.TOPIK_I, Skill.READING, "Đoạn ngắn, 2 câu hỏi", 2, 2, "49-50|51-52|53-54|55-56",
                "※ [49~56] 다음을 읽고 물음에 답하십시오.");
        single("I-D7", Track.TOPIK_I, Skill.READING, "Sắp xếp thứ tự câu", 2, 2, "57-58",
                "※ [57~58] 다음을 순서대로 맞게 나열한 것을 고르십시오.");
        grouped("I-D8", Track.TOPIK_I, Skill.READING, "Chèn câu vào đoạn văn", 2, 2, "59-60",
                "※ [59~60] 다음을 읽고 물음에 답하십시오.");
        grouped("I-D9", Track.TOPIK_I, Skill.READING, "Mục đích viết + nội dung", 2, 2, "63-64",
                "※ [63~64] 다음을 읽고 물음에 답하십시오.");
        grouped("I-D10", Track.TOPIK_I, Skill.READING, "Đoạn dài, 2 câu hỏi", 2, 2, "61-62|65-66|67-68|69-70",
                "※ [61~70] 다음을 읽고 물음에 답하십시오.");

        // ---------- TOPIK II · Nghe (1–50) ----------
        def("II-N1", Track.TOPIK_II, Skill.LISTENING, "Chọn tranh, biểu đồ", 3, 3, "1|2|3",
                "※ [1~3] 다음을 듣고 알맞은 그림 또는 그래프를 고르십시오.", false, true);
        single("II-N2", Track.TOPIK_II, Skill.LISTENING, "Chọn câu nói tiếp theo", 3, 3, "4-8",
                "※ [4~8] 다음 대화를 잘 듣고 이어질 수 있는 말로 가장 알맞은 것을 고르십시오.");
        single("II-N3", Track.TOPIK_II, Skill.LISTENING, "Hành động tiếp theo", 3, 3, "9-12",
                "※ [9~12] 다음 대화를 잘 듣고 여자가 이어서 할 행동으로 가장 알맞은 것을 고르십시오.");
        single("II-N4", Track.TOPIK_II, Skill.LISTENING, "Chọn nội dung giống", 3, 3, "13-16",
                "※ [13~16] 다음을 듣고 들은 내용과 같은 것을 고르십시오.");
        single("II-N5", Track.TOPIK_II, Skill.LISTENING, "Ý chính của người nói", 3, 4, "17-20",
                "※ [17~20] 다음을 듣고 남자의 중심 생각으로 가장 알맞은 것을 고르십시오.");
        grouped("II-N6", Track.TOPIK_II, Skill.LISTENING, "Hội thoại đời sống, công việc (2 câu/bài)", 4, 4,
                "21-22|23-24|25-26|27-28|29-30", "※ [21~30] 다음을 듣고 물음에 답하십시오.");
        grouped("II-N7", Track.TOPIK_II, Skill.LISTENING, "Phỏng vấn, tranh luận, bài nói (2 câu/bài)", 4, 5,
                "31-32|33-34|35-36|37-38|39-40", "※ [31~40] 다음을 듣고 물음에 답하십시오.");
        grouped("II-N8", Track.TOPIK_II, Skill.LISTENING, "Bài giảng, tài liệu học thuật (2 câu/bài)", 5, 6,
                "41-42|43-44|45-46|47-48|49-50", "※ [41~50] 다음을 듣고 물음에 답하십시오.");

        // ---------- TOPIK II · Viết (51–54) ----------
        def("II-V1", Track.TOPIK_II, Skill.WRITING, "Điền câu vào văn bản thực dụng", 3, 3, "51",
                "※ [51~52] 다음 글의 ㉠과 ㉡에 들어갈 말을 각각 한 문장으로 쓰십시오.", false, false);
        def("II-V2", Track.TOPIK_II, Skill.WRITING, "Điền câu vào đoạn giải thích", 3, 4, "52",
                "※ [51~52] 다음 글의 ㉠과 ㉡에 들어갈 말을 각각 한 문장으로 쓰십시오.", false, false);
        def("II-V3", Track.TOPIK_II, Skill.WRITING, "Mô tả biểu đồ 200–300 chữ", 4, 4, "53",
                "※ [53] 다음을 참고하여 200~300자로 글을 쓰십시오. 단, 글의 제목은 쓰지 마십시오.", false, false);
        def("II-V4", Track.TOPIK_II, Skill.WRITING, "Nghị luận 600–700 chữ", 5, 6, "54",
                "※ [54] 다음을 주제로 하여 자신의 생각을 600~700자로 글을 쓰십시오. 단, 문제를 그대로 옮겨 쓰지 마십시오.", false, false);

        // ---------- TOPIK II · Đọc (1–50) ----------
        def("II-D1", Track.TOPIK_II, Skill.READING, "Điền ngữ pháp", 3, 3, "1|2",
                "※ [1~2] (    )에 들어갈 가장 알맞은 것을 고르십시오.", false, false);
        def("II-D2", Track.TOPIK_II, Skill.READING, "Chọn ngữ pháp gần nghĩa", 3, 3, "3|4",
                "※ [3~4] 밑줄 친 부분과 의미가 가장 비슷한 것을 고르십시오.", false, false);
        single("II-D3", Track.TOPIK_II, Skill.READING, "Quảng cáo, biển báo nói về gì", 3, 3, "5-8",
                "※ [5~8] 다음은 무엇에 대한 글인지 고르십시오.");
        single("II-D4", Track.TOPIK_II, Skill.READING, "Nội dung giống (thông báo, biểu đồ)", 3, 3, "9-12",
                "※ [9~12] 다음 글 또는 도표의 내용과 같은 것을 고르십시오.");
        single("II-D5", Track.TOPIK_II, Skill.READING, "Sắp xếp câu", 3, 4, "13-15",
                "※ [13~15] 다음을 순서대로 맞게 배열한 것을 고르십시오.");
        single("II-D6", Track.TOPIK_II, Skill.READING, "Điền cụm từ vào đoạn văn", 4, 5, "16-18,28-31",
                "※ 다음을 읽고 (    )에 들어갈 내용으로 가장 알맞은 것을 고르십시오.");
        grouped("II-D7", Track.TOPIK_II, Skill.READING, "Đoạn văn 2 câu (từ nối, thành ngữ, cảm xúc)", 4, 4,
                "19-20|21-22|23-24", "※ [19~24] 다음을 읽고 물음에 답하십시오.");
        single("II-D8", Track.TOPIK_II, Skill.READING, "Hiểu tiêu đề báo", 4, 4, "25-27",
                "※ [25~27] 다음은 신문 기사의 제목입니다. 가장 잘 설명한 것을 고르십시오.");
        single("II-D9", Track.TOPIK_II, Skill.READING, "Nội dung giống (đoạn học thuật)", 5, 5, "32-34",
                "※ [32~34] 다음을 읽고 내용이 같은 것을 고르십시오.");
        single("II-D10", Track.TOPIK_II, Skill.READING, "Chủ đề chính", 5, 5, "35-38",
                "※ [35~38] 다음 글의 주제로 가장 알맞은 것을 고르십시오.");
        single("II-D11", Track.TOPIK_II, Skill.READING, "Chèn câu", 5, 5, "39-41",
                "※ [39~41] 주어진 문장이 들어갈 곳으로 가장 알맞은 것을 고르십시오.");
        grouped("II-D12", Track.TOPIK_II, Skill.READING, "Văn học và bài dài", 5, 6, "42-43|44-45|46-47|48-50",
                "※ [42~50] 다음을 읽고 물음에 답하십시오.");
    }

    private QuestionTypeCatalog() {
    }

    private static void single(String code, Track track, Skill skill, String name, int zMin, int zMax,
                               String ranges, String instruction) {
        List<String> blockSpecs = new ArrayList<>();
        for (int n : expand(ranges)) blockSpecs.add(String.valueOf(n));
        def(code, track, skill, name, zMin, zMax, String.join("|", blockSpecs), instruction,
                skill == Skill.READING, false);
    }

    private static void grouped(String code, Track track, Skill skill, String name, int zMin, int zMax,
                                String blocks, String instruction) {
        def(code, track, skill, name, zMin, zMax, blocks, instruction, skill == Skill.READING, false);
    }

    private static void def(String code, Track track, Skill skill, String name, int zMin, int zMax,
                            String blockSpec, String instruction, boolean passageRequired, boolean imageRequired) {
        List<List<Integer>> blocks = new ArrayList<>();
        for (String b : blockSpec.split("\\|")) {
            blocks.add(expand(b));
        }
        QuestionTypeDef t = new QuestionTypeDef(code, track, skill, name, zMin, zMax, instruction,
                List.copyOf(blocks), passageRequired, imageRequired, TYPES.size() + 1);
        TYPES.add(t);
        BY_CODE.put(code, t);
    }

    private static List<Integer> expand(String spec) {
        List<Integer> out = new ArrayList<>();
        for (String part : spec.split(",")) {
            String[] ab = part.trim().split("-");
            int a = Integer.parseInt(ab[0].trim());
            int b = ab.length > 1 ? Integer.parseInt(ab[1].trim()) : a;
            for (int i = a; i <= b; i++) out.add(i);
        }
        return List.copyOf(out);
    }

    public static List<QuestionTypeDef> all() {
        return Collections.unmodifiableList(TYPES);
    }

    public static Optional<QuestionTypeDef> byCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(BY_CODE.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public static List<QuestionTypeDef> forTrack(Track track) {
        return TYPES.stream().filter(t -> t.track() == track).toList();
    }

    public static List<QuestionTypeDef> mcqTypes(Track track) {
        return TYPES.stream().filter(t -> t.track() == track && !t.isWriting()).toList();
    }

    public static List<QuestionTypeDef> forTrackAndSkill(Track track, Skill skill) {
        return TYPES.stream().filter(t -> t.track() == track && t.skill() == skill).toList();
    }

    /** Tra mã dạng từ (track, kỹ năng, số câu). Rỗng nếu số câu nằm ngoài phạm vi. */
    public static Optional<QuestionTypeDef> resolve(Track track, Skill skill, int no) {
        return TYPES.stream()
                .filter(t -> t.track() == track && t.skill() == skill && t.numbers().contains(no))
                .findFirst();
    }

    /** Thứ tự làm bài: TOPIK I = Nghe → Đọc; TOPIK II = Nghe → Viết → Đọc. */
    public static List<Section> sections(Track track) {
        if (track == Track.TOPIK_I) {
            return List.of(
                    new Section(Skill.LISTENING, 1, 30, 40),
                    new Section(Skill.READING, 31, 70, 60));
        }
        return List.of(
                new Section(Skill.LISTENING, 1, 50, 60),
                new Section(Skill.WRITING, 51, 54, 50),
                new Section(Skill.READING, 1, 50, 70));
    }

    public static Optional<Section> section(Track track, Skill skill) {
        return sections(track).stream().filter(s -> s.skill() == skill).findFirst();
    }

    public static int mcqCount(Track track) {
        return mcqTypes(track).stream().mapToInt(QuestionTypeDef::requiredCount).sum();
    }

    /** Tổng số câu của đề đầy đủ: TOPIK I = 70, TOPIK II = 104 (100 trắc nghiệm + 4 Viết). */
    public static int fullExamCount(Track track) {
        return track == Track.TOPIK_I ? 70 : 104;
    }

    public static int totalMinutes(Track track) {
        return sections(track).stream().mapToInt(Section::minutes).sum();
    }

    public static Map<Skill, List<QuestionTypeDef>> groupedBySkill(Track track) {
        return forTrack(track).stream().collect(Collectors.groupingBy(QuestionTypeDef::skill,
                () -> new EnumMap<>(Skill.class), Collectors.toList()));
    }
}
