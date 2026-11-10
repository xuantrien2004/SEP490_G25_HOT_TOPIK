package com.hottopik.service.exam;

import com.hottopik.dto.ExamScoreDTO;
import com.hottopik.dto.WritingSubmissionDTO;
import com.hottopik.entity.exam.ExamAttempt;
import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;

import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ExamScoreCalculator {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ExamScoreCalculator() {
    }

    public static ExamScoreDTO calculate(LearnerExamService.Paper paper,
                                         Map<Long, WritingSubmissionDTO> reviews,
                                         Map<String, String> creditedAnswers) {
        return calculate(paper, reviews, creditedAnswers, null);
    }

    public static ExamScoreDTO calculate(LearnerExamService.Paper paper,
                                         Map<Long, WritingSubmissionDTO> reviews,
                                         Map<String, String> creditedAnswers,
                                         Map<Long, WritingSubmissionDTO> sentByPrompt) {
        ExamScoreDTO dto = new ExamScoreDTO();
        Track track = paper.track();

        int listenCorrect = 0;
        int listenTotal = 0;
        int readCorrect = 0;
        int readTotal = 0;

        for (LearnerExamService.QuestionView q : paper.questions()) {
            boolean correct = (creditedAnswers != null && creditedAnswers.containsKey(q.key()))
                    || (q.selected() != null && q.answer() != null && q.selected().trim().equals(String.valueOf(q.answer())));

            if (q.skill() == Skill.LISTENING) {
                listenTotal++;
                if (correct) listenCorrect++;
            } else if (q.skill() == Skill.READING) {
                readTotal++;
                if (correct) readCorrect++;
            }
        }

        double listenScore = 0.0;
        double listenMax = 0.0;
        double listenPerQ = track == Track.TOPIK_I ? 3.33 : 2.0;

        if (listenTotal > 0) {
            dto.setHasListening(true);
            dto.setListenCorrect(listenCorrect);
            dto.setListenTotal(listenTotal);
            dto.setListenPointPerQuestion(listenPerQ);
            if (track == Track.TOPIK_I && listenTotal == 30 && listenCorrect == 30) {
                listenScore = 100.0;
            } else {
                listenScore = Math.round(listenCorrect * listenPerQ * 100.0) / 100.0;
            }
            listenMax = (track == Track.TOPIK_I && listenTotal == 30) ? 100.0 : Math.round(listenTotal * listenPerQ * 100.0) / 100.0;
            dto.setListenScore(listenScore);
            dto.setListenMaxScore(listenMax);
        }

        double readScore = 0.0;
        double readMax = 0.0;
        double readPerQ = track == Track.TOPIK_I ? 2.50 : 2.0;

        if (readTotal > 0) {
            dto.setHasReading(true);
            dto.setReadCorrect(readCorrect);
            dto.setReadTotal(readTotal);
            dto.setReadPointPerQuestion(readPerQ);
            if (track == Track.TOPIK_I && readTotal == 40 && readCorrect == 40) {
                readScore = 100.0;
            } else {
                readScore = Math.round(readCorrect * readPerQ * 100.0) / 100.0;
            }
            readMax = (track == Track.TOPIK_I && readTotal == 40) ? 100.0 : Math.round(readTotal * readPerQ * 100.0) / 100.0;
            dto.setReadScore(readScore);
            dto.setReadMaxScore(readMax);
        }

        boolean hasWriting = paper.writing() != null && !paper.writing().isEmpty();
        dto.setHasWriting(hasWriting);

        if (hasWriting) {
            dto.setWritingMaxScore(100.0);
            double writingEarnedTotal = 0.0;
            boolean anyWritingGradedOrAuto = false;
            boolean anyWritingWaiting = false;

            for (LearnerExamService.WritingView w : paper.writing()) {
                // Chỉ dùng reviews của đúng lần làm này (attempt-scoped); không fallback sang sentByPrompt vì sentByPrompt chứa submission của các lần khác.
                WritingSubmissionDTO sub = (reviews != null) ? reviews.get(w.id()) : null;

                Double score = null;
                String status = "NOT_SUBMITTED";
                String feedback = null;

                if (sub != null) {
                    if ("graded".equalsIgnoreCase(sub.getStatus()) && sub.getTotalScore() != null) {
                        score = (double) sub.getTotalScore();
                        status = "GRADED";
                        feedback = sub.getOverallFeedback();
                        writingEarnedTotal += score;
                        anyWritingGradedOrAuto = true;
                    } else if ("waiting".equalsIgnoreCase(sub.getStatus())) {
                        score = null;
                        status = "WAITING";
                        feedback = "Đã gửi Giáo viên, đang chờ kết quả chấm.";
                        anyWritingWaiting = true;
                    } else if (sub.getRejectReason() != null && !sub.getRejectReason().isBlank()) {
                        score = 0.0;
                        status = "REJECTED";
                        feedback = "Giáo viên từ chối chấm: " + sub.getRejectReason();
                    } else {
                        score = null;
                        status = "WAITING";
                        anyWritingWaiting = true;
                    }
                } else {
                    if (w.no() == 51 || w.no() == 52) {
                        boolean hasAns = com.hottopik.service.WritingSubmissionService.hasAnswer(w.no(), w.selected());
                        if (hasAns) {
                            Writing5152Evaluator.EvaluationResult eval = Writing5152Evaluator.evaluate(
                                    w.no(), w.selected(), w.keys1(), w.keys2(), w.modelAnswer()
                            );
                            score = eval.totalScore();
                            status = "AUTO";
                            feedback = eval.summary();
                            writingEarnedTotal += score;
                            anyWritingGradedOrAuto = true;
                        } else {
                            score = 0.0;
                            status = "EMPTY";
                            feedback = "Chưa làm câu này";
                        }
                    } else {
                        boolean hasAns = w.selected() != null && !w.selected().isBlank();
                        score = null;
                        status = hasAns ? "NOT_SUBMITTED" : "EMPTY";
                        feedback = hasAns ? "Bài viết đã lưu, chưa gửi Giáo viên chấm" : "Chưa làm câu này";
                    }
                }

                if (w.no() == 51) {
                    dto.setWriting51Score(score);
                    dto.setWriting51Status(status);
                    dto.setWriting51Feedback(feedback);
                } else if (w.no() == 52) {
                    dto.setWriting52Score(score);
                    dto.setWriting52Status(status);
                    dto.setWriting52Feedback(feedback);
                } else if (w.no() == 53) {
                    dto.setWriting53Score(score);
                    dto.setWriting53Status(status);
                    dto.setWriting53Feedback(feedback);
                } else if (w.no() == 54) {
                    dto.setWriting54Score(score);
                    dto.setWriting54Status(status);
                    dto.setWriting54Feedback(feedback);
                }
            }

            if (anyWritingGradedOrAuto) {
                dto.setWritingTotalScore(writingEarnedTotal);
                dto.setWritingStatus(anyWritingWaiting ? "PARTIAL" : "GRADED");
            } else if (anyWritingWaiting) {
                dto.setWritingTotalScore(null);
                dto.setWritingStatus("WAITING");
            } else {
                dto.setWritingTotalScore(0.0);
                dto.setWritingStatus("NOT_SUBMITTED");
            }
        }

        dto.setTotalCorrect(listenCorrect + readCorrect);
        dto.setTotalQuestions(listenTotal + readTotal);

        double totalEarned = 0.0;
        double totalMax = 0.0;

        if (dto.isHasListening()) {
            totalEarned += dto.getListenScore();
            totalMax += dto.getListenMaxScore();
        }
        if (dto.isHasReading()) {
            totalEarned += dto.getReadScore();
            totalMax += dto.getReadMaxScore();
        }
        if (hasWriting) {
            totalMax += 100.0;
            if (dto.getWritingTotalScore() != null) {
                totalEarned += dto.getWritingTotalScore();
            }
        }

        dto.setTotalEarnedScore(Math.round(totalEarned * 100.0) / 100.0);
        dto.setTotalMaxScore(totalMax);

        double ratio = totalMax > 0 ? (totalEarned / totalMax) : 0.0;
        if (ratio >= 0.80) {
            dto.setGradeBadge("Xuất sắc");
            dto.setGradeCss("success");
        } else if (ratio >= 0.50) {
            dto.setGradeBadge("Đạt");
            dto.setGradeCss("warning");
        } else {
            dto.setGradeBadge("Cần cố gắng");
            dto.setGradeCss("danger");
        }

        return dto;
    }

    public static List<ExamScoreDTO.HistoryItem> calculateHistory(LearnerExamService.Paper paper) {
        return calculateHistory(paper, null, null);
    }

    public static List<ExamScoreDTO.HistoryItem> calculateHistory(LearnerExamService.Paper paper, ExamScoreDTO currentScore) {
        return calculateHistory(paper, currentScore, null);
    }

    public static List<ExamScoreDTO.HistoryItem> calculateHistory(LearnerExamService.Paper paper,
                                                                  ExamScoreDTO currentScore,
                                                                  Map<Long, Map<Long, WritingSubmissionDTO>> allReviews) {
        List<ExamScoreDTO.HistoryItem> list = new ArrayList<>();
        if (paper.history() == null) return list;

        Track track = paper.track();
        Long currentAttemptId = paper.attempt().getId();

        for (ExamAttempt a : paper.history()) {
            ExamScoreDTO.HistoryItem item = new ExamScoreDTO.HistoryItem();
            item.setId(a.getId());
            boolean isCur = a.getId().equals(currentAttemptId);
            item.setCurrent(isCur);
            item.setCompletedAt(a.getCompletedAt() != null ? a.getCompletedAt().format(TIME_FORMAT) : "—");
            item.setCorrect(a.getCorrect());
            item.setTotal(a.getTotal());

            if (isCur && currentScore != null) {
                item.setScore(currentScore.getTotalEarnedScore());
                item.setMaxScore(currentScore.getTotalMaxScore());
                item.setFormattedScore(currentScore.getFormattedTotalScore() + " / " + currentScore.getFormattedMaxScore());
                item.setGradeBadge(currentScore.getGradeBadge());
                item.setGradeCss(currentScore.getGradeCss());
                item.setWaiting(currentScore.hasAnyWritingWaiting());
            } else {
                Map<String, String> answers = CheckedAnswers.read(a.getAnswersJson());
                Map<String, String> credited = CheckedAnswers.read(a.getCreditedAnswersJson());

                double mcEarned = 0.0;
                double mcMax = 0.0;

                if (paper.questions() != null && !paper.questions().isEmpty()) {
                    if (track == Track.TOPIK_I) {
                        int listenCorrect = 0, listenTotal = 0;
                        int readCorrect = 0, readTotal = 0;
                        for (LearnerExamService.QuestionView q : paper.questions()) {
                            boolean ok = (answers.get(q.key()) != null && q.answer() != null && answers.get(q.key()).trim().equals(String.valueOf(q.answer())))
                                    || credited.containsKey(q.key());
                            if (q.skill() == Skill.LISTENING) {
                                listenTotal++;
                                if (ok) listenCorrect++;
                            } else if (q.skill() == Skill.READING) {
                                readTotal++;
                                if (ok) readCorrect++;
                            }
                        }

                        if (listenTotal > 0 && readTotal > 0) {
                            double lScore = (listenTotal == 30 && listenCorrect == 30) ? 100.0 : Math.round(listenCorrect * 3.33 * 100.0) / 100.0;
                            double rScore = (readTotal == 40 && readCorrect == 40) ? 100.0 : Math.round(readCorrect * 2.50 * 100.0) / 100.0;
                            mcEarned = lScore + rScore;
                            mcMax = ((listenTotal == 30) ? 100.0 : Math.round(listenTotal * 3.33 * 100.0) / 100.0)
                                    + ((readTotal == 40) ? 100.0 : Math.round(readTotal * 2.50 * 100.0) / 100.0);
                        } else if (listenTotal > 0) {
                            mcEarned = (listenTotal == 30 && listenCorrect == 30) ? 100.0 : Math.round(listenCorrect * 3.33 * 100.0) / 100.0;
                            mcMax = (listenTotal == 30) ? 100.0 : Math.round(listenTotal * 3.33 * 100.0) / 100.0;
                        } else if (readTotal > 0) {
                            mcEarned = (readTotal == 40 && readCorrect == 40) ? 100.0 : Math.round(readCorrect * 2.50 * 100.0) / 100.0;
                            mcMax = (readTotal == 40) ? 100.0 : Math.round(readTotal * 2.50 * 100.0) / 100.0;
                        } else {
                            if (paper.kind() == com.hottopik.entity.exam.ExamKind.LISTENING) {
                                mcEarned = (a.getTotal() == 30 && a.getCorrect() == 30) ? 100.0 : Math.round(a.getCorrect() * 3.33 * 100.0) / 100.0;
                                mcMax = a.getTotal() == 30 ? 100.0 : Math.round(a.getTotal() * 3.33 * 100.0) / 100.0;
                            } else if (paper.kind() == com.hottopik.entity.exam.ExamKind.READING) {
                                mcEarned = Math.round(a.getCorrect() * 2.50 * 100.0) / 100.0;
                                mcMax = Math.round(a.getTotal() * 2.50 * 100.0) / 100.0;
                            } else {
                                double ratio = a.getTotal() > 0 ? ((double) a.getCorrect() / a.getTotal()) : 0.0;
                                mcEarned = Math.round(ratio * 200.0 * 10.0) / 10.0;
                                mcMax = 200.0;
                            }
                        }
                    } else {
                        mcEarned = Math.round(a.getCorrect() * 2.0 * 100.0) / 100.0;
                        mcMax = Math.round(a.getTotal() * 2.0 * 100.0) / 100.0;
                    }
                } else if (a.getTotal() > 0) {
                    if (track == Track.TOPIK_I) {
                        if (paper.kind() == com.hottopik.entity.exam.ExamKind.LISTENING) {
                            mcEarned = (a.getTotal() == 30 && a.getCorrect() == 30) ? 100.0 : Math.round(a.getCorrect() * 3.33 * 100.0) / 100.0;
                            mcMax = a.getTotal() == 30 ? 100.0 : Math.round(a.getTotal() * 3.33 * 100.0) / 100.0;
                        } else if (paper.kind() == com.hottopik.entity.exam.ExamKind.READING) {
                            mcEarned = Math.round(a.getCorrect() * 2.50 * 100.0) / 100.0;
                            mcMax = Math.round(a.getTotal() * 2.50 * 100.0) / 100.0;
                        } else {
                            double ratio = ((double) a.getCorrect() / a.getTotal());
                            mcEarned = Math.round(ratio * 200.0 * 10.0) / 10.0;
                            mcMax = 200.0;
                        }
                    } else {
                        mcEarned = Math.round(a.getCorrect() * 2.0 * 100.0) / 100.0;
                        mcMax = Math.round(a.getTotal() * 2.0 * 100.0) / 100.0;
                    }
                }

                double totalEarned = mcEarned;
                double totalMax = mcMax;
                boolean anyWaiting = false;

                if (paper.writing() != null && !paper.writing().isEmpty()) {
                    totalMax += 100.0;
                    Map<Long, WritingSubmissionDTO> attemptReviews = (allReviews != null) ? allReviews.get(a.getId()) : null;
                    for (LearnerExamService.WritingView w : paper.writing()) {
                        WritingSubmissionDTO sub = (attemptReviews != null) ? attemptReviews.get(w.id()) : null;
                        if (sub != null) {
                            if ("graded".equalsIgnoreCase(sub.getStatus()) && sub.getTotalScore() != null) {
                                totalEarned += sub.getTotalScore();
                            } else if ("waiting".equalsIgnoreCase(sub.getStatus()) || ("submitted".equalsIgnoreCase(sub.getStatus()) && (sub.getRejectReason() == null || sub.getRejectReason().isBlank()))) {
                                anyWaiting = true;
                            }
                        } else {
                            if (w.no() == 51 || w.no() == 52) {
                                String ans = answers.get("w" + w.id());
                                if (com.hottopik.service.WritingSubmissionService.hasAnswer(w.no(), ans)) {
                                    Writing5152Evaluator.EvaluationResult eval = Writing5152Evaluator.evaluate(
                                            w.no(), ans, w.keys1(), w.keys2(), w.modelAnswer()
                                    );
                                    totalEarned += eval.totalScore();
                                }
                            }
                        }
                    }
                }

                totalEarned = Math.round(totalEarned * 100.0) / 100.0;
                item.setScore(totalEarned);
                item.setMaxScore(totalMax);
                item.setFormattedScore(ExamScoreDTO.formatScore(totalEarned) + " / " + ExamScoreDTO.formatScore(totalMax));
                item.setWaiting(anyWaiting);

                double ratio = totalMax > 0 ? (totalEarned / totalMax) : 0.0;
                if (anyWaiting && totalEarned == 0 && a.getTotal() == 0) {
                    item.setGradeBadge("Chờ chấm");
                    item.setGradeCss("warning");
                } else if (ratio >= 0.80) {
                    item.setGradeBadge("Xuất sắc");
                    item.setGradeCss("success");
                } else if (ratio >= 0.50) {
                    item.setGradeBadge("Đạt");
                    item.setGradeCss("warning");
                } else {
                    item.setGradeBadge("Cần cố gắng");
                    item.setGradeCss("danger");
                }
            }

            list.add(item);
        }

        return list;
    }
}
