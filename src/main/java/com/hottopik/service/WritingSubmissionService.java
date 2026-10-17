package com.hottopik.service;

import com.hottopik.dto.WritingSubmissionDTO;
import com.hottopik.dto.WritingSubmissionDTO.ChartItem;
import com.hottopik.dto.WritingSubmissionDTO.CorrectionAnnotation;
import com.hottopik.dto.WritingSubmissionDTO.CriterionScore;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class WritingSubmissionService {

    private final Map<String, WritingSubmissionDTO> submissions = new ConcurrentHashMap<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @PostConstruct
    public void initSeeds() {
        // 1. Sub 101 - Câu 53 (waiting in queue)
        WritingSubmissionDTO s1 = new WritingSubmissionDTO();
        s1.setId("sub-101");
        s1.setTitle("Luyện tập · Viết câu 53");
        s1.setSource("Luyện tập");
        s1.setLearnerName("Phạm Minh Anh");
        s1.setLearnerEmail("learner@hottopik.com");
        s1.setQuestionNo(53);
        s1.setStatus("waiting");
        s1.setSubmittedAt(LocalDateTime.now().minusHours(12).format(FMT));
        s1.setDueDeadline("Còn 36 giờ");
        s1.setMaxScore(30);
        s1.setAiScore(23);
        s1.setPrompt("다음은 '20대의 여가 활동'에 대한 조사 결과이다. 이 내용을 200~300자의 글로 쓰시오. (단, 글의 제목을 쓰지 마시오.)");
        s1.setHasChart(true);
        s1.setChartItems(Arrays.asList(
                new ChartItem("운동", 35, "105px"),
                new ChartItem("여행", 26, "78px"),
                new ChartItem("게임", 24, "72px"),
                new ChartItem("독서", 9, "27px")
        ));
        s1.setStudentAnswer("20대가 여가 시간에 하는 활동을 조사한 결과, 2016년에는 여행이 30%로 가장 높았으나 2024년에는 운동이 35%로 가장 많았다. 게임은 18%에서 24%로 증가한 반면, 독서는 20%에서 9%로 크게 감소하였다. 이러한 결과는 20대의 건강에 대한 관심이 증가했기 때문이다.");
        s1.setWordCount(235);
        s1.setCriteriaScores(Arrays.asList(
                new CriterionScore("Nội dung & Nhiệm vụ", 0, 10),
                new CriterionScore("Cấu trúc & Liên kết", 0, 10),
                new CriterionScore("Từ vựng & Ngữ pháp", 0, 10)
        ));
        submissions.put(s1.getId(), s1);

        // 2. Sub 102 - Câu 54 (claimed by Cô Mai)
        WritingSubmissionDTO s2 = new WritingSubmissionDTO();
        s2.setId("sub-102");
        s2.setTitle("Luyện tập · Viết câu 54");
        s2.setSource("Luyện tập");
        s2.setLearnerName("Nguyễn Hoàng Nam");
        s2.setLearnerEmail("viplearner@hottopik.com");
        s2.setQuestionNo(54);
        s2.setStatus("claimed");
        s2.setMentorName("Cô Mai");
        s2.setSubmittedAt(LocalDateTime.now().minusHours(24).format(FMT));
        s2.setDueDeadline("Còn 24 giờ");
        s2.setMaxScore(50);
        s2.setAiScore(36);
        s2.setPrompt("최근 온라인 수업이 늘고 있다. 온라인 수업의 장점과 단점은 무엇인가? 효과적인 온라인 수업을 위해 필요한 것은 무엇인가? 이에 대한 자신의 생각을 쓰시오.");
        s2.setHasChart(false);
        s2.setStudentAnswer("현대 사회에서 정보 통신 기술의 발달과 함께 온라인 수업이 급격히 증가하고 있다. 이러한 변화는 교육 환경에 다양한 영향을 미치고 있다.\n\n먼저 온라인 수업의 큰 장점은 시간과 공간의 제약 없이 학습자가 원하는 환경에서 편리하게 공부할 수 있다는 점이다. 또한 반복 학습이 가능하여 이해가 부족한 부분을 다시 들을 수 있다. 그러나 학생들의 집중력이 저하되기 쉽고 교수자와 학습자 간의 상호작용이 부족하다는 단점이 있다.\n\n따라서 효과적인 온라인 수업을 위해서는 학생들의 자기주도 학습 역량을 기르는 것이 필요하며, 실시간 질의응답이나 토론 등 능동적인 참여를 유도하는 시스템이 마련되어야 한다.");
        s2.setWordCount(632);
        s2.setCriteriaScores(Arrays.asList(
                new CriterionScore("Nội dung & Hoàn thành nhiệm vụ", 0, 12),
                new CriterionScore("Cấu trúc & Phát triển ý", 0, 12),
                new CriterionScore("Ngữ pháp & Từ vựng nâng cao", 0, 12),
                new CriterionScore("Độ chính xác & Văn phong viết", 0, 14)
        ));
        submissions.put(s2.getId(), s2);

        // 3. Sub 103 - Câu 51 (graded with student question)
        WritingSubmissionDTO s3 = new WritingSubmissionDTO();
        s3.setId("sub-103");
        s3.setTitle("Luyện tập · Viết câu 51");
        s3.setSource("Luyện tập");
        s3.setLearnerName("Trần Văn Bình");
        s3.setLearnerEmail("binh.tran@gmail.com");
        s3.setQuestionNo(51);
        s3.setStatus("graded");
        s3.setMentorName("Cô Mai");
        s3.setSubmittedAt(LocalDateTime.now().minusDays(2).format(FMT));
        s3.setGradedAt(LocalDateTime.now().minusDays(1).format(FMT));
        s3.setDueDeadline("Đã hoàn tất");
        s3.setMaxScore(10);
        s3.setAiScore(8);
        s3.setTotalScore(9);
        s3.setPrompt("[안내] 동아리 모임 안내\n다음 주 금요일에 신입 회원 환영회가 있습니다. 참석하실 분은 목요일까지 (㉠).\n신청한 분만 (㉡). 많은 참여 바랍니다.");
        s3.setHasChart(false);
        s3.setStudentAnswer("㉠ 저에게 연락해 주십시오\n㉡ 참석하실 수 있습니다");
        s3.setWordCount(45);
        s3.setCriteriaScores(Arrays.asList(
                new CriterionScore("Điền chỗ trống (㉠)", 5, 5),
                new CriterionScore("Điền chỗ trống (㉡)", 4, 5)
        ));
        s3.setOverallFeedback("Em làm rất tốt, nắm chắc văn phong thông báo trang trọng. Chỗ ㉠ dùng -(으)십시오 rất chuẩn mực. Chỗ ㉡ có thể dùng thêm ngữ pháp nhấn mạnh sự cho phép.");
        s3.setModelAnswer("㉠ 신청해 주시기 바랍니다\n㉡ 참석할 수 있습니다");
        s3.setStudentQuestion("Cô cho em hỏi ở chỗ trống (㉠) nếu em dùng '연락해 주시기를 바랍니다' thì có được tính trọn vẹn điểm không ạ?");
        s3.setTeacherReply(""); // waiting teacher reply
        s3.setRating(5);
        submissions.put(s3.getId(), s3);

        // 4. Sub 104 - Câu 52 (waiting in queue)
        WritingSubmissionDTO s4 = new WritingSubmissionDTO();
        s4.setId("sub-104");
        s4.setTitle("Luyện tập · Viết câu 52");
        s4.setSource("Luyện tập");
        s4.setLearnerName("Lê Hoàng Yến");
        s4.setLearnerEmail("yen.le@gmail.com");
        s4.setQuestionNo(52);
        s4.setStatus("waiting");
        s4.setSubmittedAt(LocalDateTime.now().minusHours(8).format(FMT));
        s4.setDueDeadline("Còn 40 giờ");
        s4.setMaxScore(10);
        s4.setAiScore(7);
        s4.setPrompt("사람들은 보통 스트레스를 받으면 단 음식을 찾는다. 단 음식을 먹으면 (㉠).\n그러나 단 음식을 너무 많이 먹으면 오히려 건강에 (㉡).");
        s4.setHasChart(false);
        s4.setStudentAnswer("㉠ 기분이 좋아진다\n㉡ 안 좋은 영향을 줄 수 있다");
        s4.setWordCount(52);
        s4.setCriteriaScores(Arrays.asList(
                new CriterionScore("Điền chỗ trống (㉠)", 0, 5),
                new CriterionScore("Điền chỗ trống (㉡)", 0, 5)
        ));
        submissions.put(s4.getId(), s4);

        // 5. Sub 105 - Câu 53 (Graded for learner Phạm Minh Anh)
        WritingSubmissionDTO s5 = new WritingSubmissionDTO();
        s5.setId("sub-105");
        s5.setTitle("Luyện tập · Viết câu 53");
        s5.setSource("Luyện tập");
        s5.setLearnerName("Phạm Minh Anh");
        s5.setLearnerEmail("learner@hottopik.com");
        s5.setQuestionNo(53);
        s5.setStatus("graded");
        s5.setMentorName("Cô Mai");
        s5.setSubmittedAt(LocalDateTime.now().minusDays(3).format(FMT));
        s5.setGradedAt(LocalDateTime.now().minusDays(2).format(FMT));
        s5.setDueDeadline("Đã hoàn tất");
        s5.setMaxScore(30);
        s5.setAiScore(24);
        s5.setTotalScore(27);
        s5.setPrompt("다음을 참고하여 '성인의 독서 실태 조사'에 대한 글을 200~300자로 쓰십시오. (단, 글의 제목을 쓰지 마십시오.)");
        s5.setHasChart(false);
        s5.setStudentAnswer("문화체육관광부에서 성인의 독서 실태에 대해 설문 조사를 실시하였다. 조사 결과에 따르면 연간 독서량은 2013년 9.2권에서 2023년 4.5권으로 10년 사이에 절반 이상 감소한 것으로 나타났다. 이러한 감소 원인으로는 스마트폰 등 전자기기 이용 증가가 56%로 가장 높았으며, 시간 부족이 28%로 그 뒤를 이었다. 앞으로 독서 환경을 조성하는 노력이 필요할 것으로 보인다.");
        s5.setWordCount(238);
        s5.setCriteriaScores(Arrays.asList(
                new CriterionScore("Nội dung & Nhiệm vụ", 9, 10),
                new CriterionScore("Cấu trúc & Liên kết", 9, 10),
                new CriterionScore("Từ vựng & Ngữ pháp", 9, 10)
        ));
        s5.setOverallFeedback("Bài viết diễn đạt tự nhiên, số liệu trích xuất chính xác theo thứ tự logic. Đuôi câu -ㄴ/는다 và cấu trúc -에 그쳤다 / 뒤를 이었다 được sử dụng rất thành thạo.");
        s5.setModelAnswer("문화체육관광부에서 20~50대 성인 1,000명을 대상으로 '성인의 독서 실태'를 조사한 결과, 연간 독서량은 2013년 9.2권에서 2023년 4.5권으로 50% 이상 급감한 것으로 나타났다. 독서 감소 원인으로는 전자기기 이용 증가가 56%로 1위를 차지했고 시간 부족이 28%로 뒤를 이었다. 이에 따라 전자책 접근성 확대 및 독서 장려 정책이 요구된다.");
        s5.setAnnotations(Arrays.asList(
                new CorrectionAnnotation("감소 원인으로는", "vocab", "Từ vựng", "이러한 독서량 감소의 주된 원인으로는", "Nên thêm định từ để liên kết ý chặt chẽ hơn", "#2E7D67")
        ));
        s5.setRating(5);
        submissions.put(s5.getId(), s5);

        // 6. Sub 106 - Câu 54 (Appeal)
        WritingSubmissionDTO s6 = new WritingSubmissionDTO();
        s6.setId("sub-106");
        s6.setTitle("Luyện tập · Viết câu 54");
        s6.setSource("Luyện tập");
        s6.setLearnerName("Vũ Quốc Hưng");
        s6.setLearnerEmail("hung.vu@gmail.com");
        s6.setQuestionNo(54);
        s6.setStatus("appeal");
        s6.setMentorName("Thầy Khoa");
        s6.setSubmittedAt(LocalDateTime.now().minusDays(4).format(FMT));
        s6.setGradedAt(LocalDateTime.now().minusDays(3).format(FMT));
        s6.setDueDeadline("Khiếu nại");
        s6.setMaxScore(50);
        s6.setAiScore(32);
        s6.setTotalScore(31);
        s6.setPrompt("현대 사회에서 대중매체(Media)가 청소년에게 미치는 영향과 올바른 미디어 활용 방안에 대해 600~700자로 논하시오.");
        s6.setHasChart(false);
        s6.setStudentAnswer("현대 사회에서 스마트폰과 인터넷의 대중화로 인해 청소년들은 다양한 대중매체를 접하고 있다. 대중매체는 청소년에게 긍정적인 영향과 부정적인 영향을 동시에 미친다. 첫째, 유익한 정보를 빠르게 얻을 수 있다. 둘째, 부정확한 정보나 유해한 콘텐츠에 쉽게 노출된다. 따라서 올바른 미디어 리터러시 교육이 학교에서 강화되어야 한다.");
        s6.setWordCount(615);
        s6.setCriteriaScores(Arrays.asList(
                new CriterionScore("Nội dung & Nhiệm vụ", 8, 12),
                new CriterionScore("Cấu trúc & Liên kết", 7, 12),
                new CriterionScore("Ngữ pháp & Từ vựng", 8, 12),
                new CriterionScore("Văn phong viết", 8, 14)
        ));
        s6.setOverallFeedback("Bài viết đáp ứng các câu hỏi cơ bản nhưng phần thân bài phát triển ý còn hơi sơ sài, các luận cứ bổ trợ chưa đủ sâu.");
        s6.setAppealReason("Em thấy bài của em chia đủ 3 đoạn mở thân kết và dùng đúng ngữ pháp trung cao cấp, các luận điểm đều có ví dụ thực tế nhưng điểm phần cấu trúc hơi thấp ạ. Kính mong hội đồng giáo viên xem xét lại giúp em.");
        submissions.put(s6.getId(), s6);
    }

    public List<WritingSubmissionDTO> getAllSubmissions() {
        return new ArrayList<>(submissions.values());
    }

    public List<WritingSubmissionDTO> getByStatus(String status) {
        if (status == null || "all".equalsIgnoreCase(status)) {
            return getAllSubmissions();
        }
        return submissions.values().stream()
                .filter(s -> status.equalsIgnoreCase(s.getStatus()))
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .collect(Collectors.toList());
    }

    public List<WritingSubmissionDTO> getSubmissionsByLearner(String learnerEmail) {
        if (learnerEmail == null) return Collections.emptyList();
        return submissions.values().stream()
                .filter(s -> learnerEmail.equalsIgnoreCase(s.getLearnerEmail()))
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .collect(Collectors.toList());
    }

    public Optional<WritingSubmissionDTO> getById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(submissions.get(id));
    }

    public synchronized boolean claimSubmission(String id, String mentorName) {
        WritingSubmissionDTO sub = submissions.get(id);
        if (sub != null && ("waiting".equalsIgnoreCase(sub.getStatus()) || "appeal".equalsIgnoreCase(sub.getStatus()))) {
            sub.setStatus("claimed");
            sub.setMentorName(mentorName != null ? mentorName : "Cô Mai");
            sub.setDueDeadline("Còn 48 giờ");
            return true;
        }
        return false;
    }

    public synchronized boolean gradeSubmission(String id, String mentorName,
                                                Map<String, Integer> criteriaScoresMap,
                                                String overallFeedback,
                                                String modelAnswer) {
        WritingSubmissionDTO sub = submissions.get(id);
        if (sub == null) return false;

        sub.setStatus("graded");
        sub.setMentorName(mentorName != null ? mentorName : "Cô Mai");
        sub.setGradedAt(LocalDateTime.now().format(FMT));
        sub.setDueDeadline("Đã chấm");
        sub.setOverallFeedback(overallFeedback != null ? overallFeedback.trim() : "");
        // Model answer is optional as requested!
        sub.setModelAnswer(modelAnswer != null ? modelAnswer.trim() : "");

        int sum = 0;
        if (criteriaScoresMap != null && !criteriaScoresMap.isEmpty()) {
            for (CriterionScore cs : sub.getCriteriaScores()) {
                if (criteriaScoresMap.containsKey(cs.getName())) {
                    int sc = criteriaScoresMap.get(cs.getName());
                    cs.setScore(Math.min(sc, cs.getMax()));
                    sum += cs.getScore();
                } else {
                    sum += cs.getScore();
                }
            }
            sub.setTotalScore(sum);
        } else {
            // Default sum if not specified
            sub.setTotalScore(sub.getMaxScore() * 85 / 100);
        }

        return true;
    }

    public synchronized boolean replyQuestion(String id, String reply) {
        WritingSubmissionDTO sub = submissions.get(id);
        if (sub != null && reply != null && !reply.trim().isEmpty()) {
            sub.setTeacherReply(reply.trim());
            return true;
        }
        return false;
    }

    public synchronized boolean askQuestion(String id, String question) {
        WritingSubmissionDTO sub = submissions.get(id);
        if (sub != null && question != null && !question.trim().isEmpty()) {
            sub.setStudentQuestion(question.trim());
            return true;
        }
        return false;
    }

    public synchronized boolean rateSubmission(String id, int rating) {
        WritingSubmissionDTO sub = submissions.get(id);
        if (sub != null) {
            sub.setRating(Math.max(1, Math.min(5, rating)));
            return true;
        }
        return false;
    }

    public synchronized boolean appealSubmission(String id, String reason) {
        WritingSubmissionDTO sub = submissions.get(id);
        if (sub != null && reason != null && !reason.trim().isEmpty()) {
            sub.setStatus("appeal");
            sub.setAppealReason(reason.trim());
            return true;
        }
        return false;
    }

    public synchronized WritingSubmissionDTO submitNewEssay(String learnerName, String learnerEmail,
                                                           int questionNo, String prompt,
                                                           String answer, int wordCount,
                                                           boolean hasChart, List<ChartItem> chartItems) {
        String newId = "sub-" + (submissions.size() + 101);
        WritingSubmissionDTO s = new WritingSubmissionDTO();
        s.setId(newId);
        s.setTitle("Luyện tập · Viết câu " + questionNo);
        s.setSource("Luyện tập");
        s.setLearnerName(learnerName != null ? learnerName : "Phạm Minh Anh");
        s.setLearnerEmail(learnerEmail != null ? learnerEmail : "learner@hottopik.com");
        s.setQuestionNo(questionNo);
        s.setStatus("waiting");
        s.setSubmittedAt(LocalDateTime.now().format(FMT));
        s.setDueDeadline("Còn 48 giờ");
        s.setPrompt(prompt);
        s.setStudentAnswer(answer);
        s.setWordCount(wordCount);
        s.setHasChart(hasChart);
        if (chartItems != null) {
            s.setChartItems(chartItems);
        }

        if (questionNo == 51 || questionNo == 52) {
            s.setMaxScore(10);
            s.setAiScore(8);
            s.setCriteriaScores(Arrays.asList(
                    new CriterionScore("Điền chỗ trống (㉠)", 0, 5),
                    new CriterionScore("Điền chỗ trống (㉡)", 0, 5)
            ));
        } else if (questionNo == 53) {
            s.setMaxScore(30);
            s.setAiScore(24);
            s.setCriteriaScores(Arrays.asList(
                    new CriterionScore("Nội dung & Nhiệm vụ", 0, 10),
                    new CriterionScore("Cấu trúc & Liên kết", 0, 10),
                    new CriterionScore("Từ vựng & Ngữ pháp", 0, 10)
            ));
        } else {
            s.setMaxScore(50);
            s.setAiScore(35);
            s.setCriteriaScores(Arrays.asList(
                    new CriterionScore("Nội dung & Hoàn thành nhiệm vụ", 0, 12),
                    new CriterionScore("Cấu trúc & Phát triển ý", 0, 12),
                    new CriterionScore("Ngữ pháp & Từ vựng nâng cao", 0, 12),
                    new CriterionScore("Độ chính xác & Văn phong viết", 0, 14)
            ));
        }

        submissions.put(s.getId(), s);
        return s;
    }
}
