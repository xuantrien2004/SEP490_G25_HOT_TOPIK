package com.hottopik.dto;

import java.io.Serializable;
import java.util.Locale;

/**
 * DTO chứa điểm số chuẩn của Đề thi (Full Mock & Mini Test)
 * - Nghe TOPIK I: 30 câu / 100đ -> 3.33 điểm/câu
 * - Đọc TOPIK I: 40 câu / 100đ -> 2.50 điểm/câu
 * - TOPIK II: Đọc & Nghe đều 50 câu / 100đ -> 2.0 điểm/câu
 * - Viết TOPIK II: Câu 51 (10đ), 52 (10đ), 53 (30đ), 54 (50đ) -> 100đ
 */
public class ExamScoreDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private double totalEarnedScore;
    private double totalMaxScore;
    private int totalCorrect;
    private int totalQuestions;

    // Nghe
    private boolean hasListening;
    private int listenCorrect;
    private int listenTotal;
    private double listenScore;
    private double listenMaxScore;
    private double listenPointPerQuestion;

    // Đọc
    private boolean hasReading;
    private int readCorrect;
    private int readTotal;
    private double readScore;
    private double readMaxScore;
    private double readPointPerQuestion;

    // Viết
    private boolean hasWriting;
    private Double writingTotalScore; // null nếu chưa có điểm nào
    private double writingMaxScore;
    private Double writing51Score;
    private Double writing52Score;
    private Double writing53Score;
    private Double writing54Score;
    private String writingStatus; // "GRADED", "PARTIAL", "WAITING", "NOT_SUBMITTED"
    private String writing51Status; // "GRADED", "AUTO", "WAITING", "NOT_SUBMITTED", "EMPTY"
    private String writing52Status;
    private String writing53Status;
    private String writing54Status;
    private String writing51Feedback;
    private String writing52Feedback;
    private String writing53Feedback;
    private String writing54Feedback;

    // Đánh giá
    private String gradeBadge; // "Xuất sắc", "Rất tốt", "Đạt", "Cần cố gắng"
    private String gradeCss;   // "success", "info", "warning", "danger"

    public ExamScoreDTO() {
    }

    public static String formatScore(double val) {
        if (Math.abs(val - Math.round(val)) < 0.001) {
            return String.valueOf((long) Math.round(val));
        }
        return String.format(Locale.US, "%.2f", val).replaceAll("\\.?0+$", "");
    }

    public String getFormattedTotalScore() {
        return formatScore(totalEarnedScore);
    }

    public String getFormattedMaxScore() {
        return formatScore(totalMaxScore);
    }

    public String getFormattedListenScore() {
        return formatScore(listenScore);
    }

    public String getFormattedListenMaxScore() {
        return formatScore(listenMaxScore);
    }

    public String getFormattedReadScore() {
        return formatScore(readScore);
    }

    public String getFormattedReadMaxScore() {
        return formatScore(readMaxScore);
    }

    public String getFormattedWritingScore() {
        return writingTotalScore != null ? formatScore(writingTotalScore) : "0";
    }

    public String getFormattedWriting51Score() {
        if (writing51Score != null) return formatScore(writing51Score);
        if ("WAITING".equals(writing51Status)) return "Chờ chấm";
        return "—";
    }

    public String getFormattedWriting52Score() {
        if (writing52Score != null) return formatScore(writing52Score);
        if ("WAITING".equals(writing52Status)) return "Chờ chấm";
        return "—";
    }

    public String getFormattedWriting53Score() {
        if (writing53Score != null) return formatScore(writing53Score);
        if ("WAITING".equals(writing53Status)) return "Chờ chấm";
        return "—";
    }

    public String getFormattedWriting54Score() {
        if (writing54Score != null) return formatScore(writing54Score);
        if ("WAITING".equals(writing54Status)) return "Chờ chấm";
        return "—";
    }

    public boolean isWriting51Waiting() { return "WAITING".equals(writing51Status); }
    public boolean isWriting52Waiting() { return "WAITING".equals(writing52Status); }
    public boolean isWriting53Waiting() { return "WAITING".equals(writing53Status); }
    public boolean isWriting54Waiting() { return "WAITING".equals(writing54Status); }

    public boolean isWriting51Graded() { return "GRADED".equals(writing51Status); }
    public boolean isWriting52Graded() { return "GRADED".equals(writing52Status); }
    public boolean isWriting53Graded() { return "GRADED".equals(writing53Status); }
    public boolean isWriting54Graded() { return "GRADED".equals(writing54Status); }

    public boolean isWriting51Auto() { return "AUTO".equals(writing51Status); }
    public boolean isWriting52Auto() { return "AUTO".equals(writing52Status); }

    public boolean hasAnyWritingWaiting() {
        return isWriting51Waiting() || isWriting52Waiting() || isWriting53Waiting() || isWriting54Waiting();
    }

    public boolean hasAnyWritingScore() {
        return writing51Score != null || writing52Score != null || writing53Score != null || writing54Score != null;
    }

    // Getters and Setters
    public double getTotalEarnedScore() { return totalEarnedScore; }
    public void setTotalEarnedScore(double totalEarnedScore) { this.totalEarnedScore = totalEarnedScore; }

    public double getTotalMaxScore() { return totalMaxScore; }
    public void setTotalMaxScore(double totalMaxScore) { this.totalMaxScore = totalMaxScore; }

    public int getTotalCorrect() { return totalCorrect; }
    public void setTotalCorrect(int totalCorrect) { this.totalCorrect = totalCorrect; }

    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }

    public boolean isHasListening() { return hasListening; }
    public void setHasListening(boolean hasListening) { this.hasListening = hasListening; }

    public int getListenCorrect() { return listenCorrect; }
    public void setListenCorrect(int listenCorrect) { this.listenCorrect = listenCorrect; }

    public int getListenTotal() { return listenTotal; }
    public void setListenTotal(int listenTotal) { this.listenTotal = listenTotal; }

    public double getListenScore() { return listenScore; }
    public void setListenScore(double listenScore) { this.listenScore = listenScore; }

    public double getListenMaxScore() { return listenMaxScore; }
    public void setListenMaxScore(double listenMaxScore) { this.listenMaxScore = listenMaxScore; }

    public double getListenPointPerQuestion() { return listenPointPerQuestion; }
    public void setListenPointPerQuestion(double listenPointPerQuestion) { this.listenPointPerQuestion = listenPointPerQuestion; }

    public boolean isHasReading() { return hasReading; }
    public void setHasReading(boolean hasReading) { this.hasReading = hasReading; }

    public int getReadCorrect() { return readCorrect; }
    public void setReadCorrect(int readCorrect) { this.readCorrect = readCorrect; }

    public int getReadTotal() { return readTotal; }
    public void setReadTotal(int readTotal) { this.readTotal = readTotal; }

    public double getReadScore() { return readScore; }
    public void setReadScore(double readScore) { this.readScore = readScore; }

    public double getReadMaxScore() { return readMaxScore; }
    public void setReadMaxScore(double readMaxScore) { this.readMaxScore = readMaxScore; }

    public double getReadPointPerQuestion() { return readPointPerQuestion; }
    public void setReadPointPerQuestion(double readPointPerQuestion) { this.readPointPerQuestion = readPointPerQuestion; }

    public boolean isHasWriting() { return hasWriting; }
    public void setHasWriting(boolean hasWriting) { this.hasWriting = hasWriting; }

    public Double getWritingTotalScore() { return writingTotalScore; }
    public void setWritingTotalScore(Double writingTotalScore) { this.writingTotalScore = writingTotalScore; }

    public double getWritingMaxScore() { return writingMaxScore; }
    public void setWritingMaxScore(double writingMaxScore) { this.writingMaxScore = writingMaxScore; }

    public Double getWriting51Score() { return writing51Score; }
    public void setWriting51Score(Double writing51Score) { this.writing51Score = writing51Score; }

    public Double getWriting52Score() { return writing52Score; }
    public void setWriting52Score(Double writing52Score) { this.writing52Score = writing52Score; }

    public Double getWriting53Score() { return writing53Score; }
    public void setWriting53Score(Double writing53Score) { this.writing53Score = writing53Score; }

    public Double getWriting54Score() { return writing54Score; }
    public void setWriting54Score(Double writing54Score) { this.writing54Score = writing54Score; }

    public String getWritingStatus() { return writingStatus; }
    public void setWritingStatus(String writingStatus) { this.writingStatus = writingStatus; }

    public String getWriting51Status() { return writing51Status; }
    public void setWriting51Status(String writing51Status) { this.writing51Status = writing51Status; }

    public String getWriting52Status() { return writing52Status; }
    public void setWriting52Status(String writing52Status) { this.writing52Status = writing52Status; }

    public String getWriting53Status() { return writing53Status; }
    public void setWriting53Status(String writing53Status) { this.writing53Status = writing53Status; }

    public String getWriting54Status() { return writing54Status; }
    public void setWriting54Status(String writing54Status) { this.writing54Status = writing54Status; }

    public String getWriting51Feedback() { return writing51Feedback; }
    public void setWriting51Feedback(String writing51Feedback) { this.writing51Feedback = writing51Feedback; }

    public String getWriting52Feedback() { return writing52Feedback; }
    public void setWriting52Feedback(String writing52Feedback) { this.writing52Feedback = writing52Feedback; }

    public String getWriting53Feedback() { return writing53Feedback; }
    public void setWriting53Feedback(String writing53Feedback) { this.writing53Feedback = writing53Feedback; }

    public String getWriting54Feedback() { return writing54Feedback; }
    public void setWriting54Feedback(String writing54Feedback) { this.writing54Feedback = writing54Feedback; }

    public String getGradeBadge() { return gradeBadge; }
    public void setGradeBadge(String gradeBadge) { this.gradeBadge = gradeBadge; }

    public String getGradeCss() { return gradeCss; }
    public void setGradeCss(String gradeCss) { this.gradeCss = gradeCss; }

    /** DTO đại diện cho một lần làm đề trong bảng lịch sử */
    public static class HistoryItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String completedAt;
        private int correct;
        private int total;
        private double score;
        private double maxScore;
        private String formattedScore;
        private String gradeBadge;
        private String gradeCss;
        private boolean current;
        private boolean waiting;

        public HistoryItem() {
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getCompletedAt() { return completedAt; }
        public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }

        public int getCorrect() { return correct; }
        public void setCorrect(int correct) { this.correct = correct; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }

        public double getMaxScore() { return maxScore; }
        public void setMaxScore(double maxScore) { this.maxScore = maxScore; }

        public String getFormattedScore() { return formattedScore; }
        public void setFormattedScore(String formattedScore) { this.formattedScore = formattedScore; }

        public String getGradeBadge() { return gradeBadge; }
        public void setGradeBadge(String gradeBadge) { this.gradeBadge = gradeBadge; }

        public String getGradeCss() { return gradeCss; }
        public void setGradeCss(String gradeCss) { this.gradeCss = gradeCss; }

        public boolean isCurrent() { return current; }
        public void setCurrent(boolean current) { this.current = current; }

        public boolean isWaiting() { return waiting; }
        public void setWaiting(boolean waiting) { this.waiting = waiting; }
    }
}
