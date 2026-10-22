package com.hottopik.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class WritingSubmissionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String source;
    private String learnerName;
    private String learnerEmail;
    private int questionNo;
    private String status; // "waiting", "claimed", "graded", "appeal"
    private String mentorName;
    private String prompt;
    private boolean hasChart;
    private List<ChartItem> chartItems = new ArrayList<>();
    private String studentAnswer;
    private int wordCount;
    private String submittedAt;
    private String dueDeadline;
    private String gradedAt;
    private Integer aiScore;
    private int maxScore;
    private Integer totalScore;
    private List<CriterionScore> criteriaScores = new ArrayList<>();
    private String overallFeedback;
    private String modelAnswer; // Optional
    private List<CorrectionAnnotation> annotations = new ArrayList<>();
    private String studentQuestion;
    private String teacherReply;
    private Integer rating;
    private String appealReason;

    public WritingSubmissionDTO() {
    }

    public static class ChartItem implements Serializable {
        private String label;
        private int value;
        private String width;

        public ChartItem() {}
        public ChartItem(String label, int value, String width) {
            this.label = label;
            this.value = value;
            this.width = width;
        }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public String getWidth() { return width; }
        public void setWidth(String width) { this.width = width; }
    }

    public static class CriterionScore implements Serializable {
        private String name;
        private int score;
        private int max;

        public CriterionScore() {}
        public CriterionScore(String name, int score, int max) {
            this.name = name;
            this.score = score;
            this.max = max;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        public int getMax() { return max; }
        public void setMax(int max) { this.max = max; }
    }

    public static class CorrectionAnnotation implements Serializable {
        private String phrase;
        private String kind; // "grammar", "vocab", "spelling", "style"
        private String kindLabel;
        private String fix;
        private String note;
        private String color;

        public CorrectionAnnotation() {}
        public CorrectionAnnotation(String phrase, String kind, String kindLabel, String fix, String note, String color) {
            this.phrase = phrase;
            this.kind = kind;
            this.kindLabel = kindLabel;
            this.fix = fix;
            this.note = note;
            this.color = color;
        }
        public String getPhrase() { return phrase; }
        public void setPhrase(String phrase) { this.phrase = phrase; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getKindLabel() { return kindLabel; }
        public void setKindLabel(String kindLabel) { this.kindLabel = kindLabel; }
        public String getFix() { return fix; }
        public void setFix(String fix) { this.fix = fix; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getLearnerName() { return learnerName; }
    public void setLearnerName(String learnerName) { this.learnerName = learnerName; }
    public String getLearnerEmail() { return learnerEmail; }
    public void setLearnerEmail(String learnerEmail) { this.learnerEmail = learnerEmail; }
    public int getQuestionNo() { return questionNo; }
    public void setQuestionNo(int questionNo) { this.questionNo = questionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMentorName() { return mentorName; }
    public void setMentorName(String mentorName) { this.mentorName = mentorName; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public boolean isHasChart() { return hasChart; }
    public void setHasChart(boolean hasChart) { this.hasChart = hasChart; }
    public List<ChartItem> getChartItems() { return chartItems; }
    public void setChartItems(List<ChartItem> chartItems) { this.chartItems = chartItems; }
    public String getStudentAnswer() { return studentAnswer; }
    public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }
    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }
    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
    public String getDueDeadline() { return dueDeadline; }
    public void setDueDeadline(String dueDeadline) { this.dueDeadline = dueDeadline; }
    public String getGradedAt() { return gradedAt; }
    public void setGradedAt(String gradedAt) { this.gradedAt = gradedAt; }
    public Integer getAiScore() { return aiScore; }
    public void setAiScore(Integer aiScore) { this.aiScore = aiScore; }
    public int getMaxScore() { return maxScore; }
    public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getFinalScore() { return totalScore != null ? totalScore : aiScore; }
    public List<CriterionScore> getCriteriaScores() { return criteriaScores; }
    public void setCriteriaScores(List<CriterionScore> criteriaScores) { this.criteriaScores = criteriaScores; }
    public String getOverallFeedback() { return overallFeedback; }
    public void setOverallFeedback(String overallFeedback) { this.overallFeedback = overallFeedback; }
    public String getModelAnswer() { return modelAnswer; }
    public void setModelAnswer(String modelAnswer) { this.modelAnswer = modelAnswer; }
    public List<CorrectionAnnotation> getAnnotations() { return annotations; }
    public void setAnnotations(List<CorrectionAnnotation> annotations) { this.annotations = annotations; }
    public String getStudentQuestion() { return studentQuestion; }
    public void setStudentQuestion(String studentQuestion) { this.studentQuestion = studentQuestion; }
    public String getTeacherReply() { return teacherReply; }
    public void setTeacherReply(String teacherReply) { this.teacherReply = teacherReply; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getAppealReason() { return appealReason; }
    public void setAppealReason(String appealReason) { this.appealReason = appealReason; }
}
