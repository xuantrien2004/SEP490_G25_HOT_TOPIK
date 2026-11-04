package com.hottopik.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.entity.*;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.*;
import com.hottopik.repository.exam.*;
import com.hottopik.service.exam.CheckedAnswers;
import com.hottopik.service.exam.LearnerExamService.Choice;
import com.hottopik.service.QuestionReportService.ReportQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Cùng transaction với lần sửa: lưu hai phiên bản, điều chỉnh đúng lượt đã nộp, gửi mỗi người một thông báo. */
@Service
@RequiredArgsConstructor
@Transactional
public class QuestionCorrectionService {
    private final QuestionRevisionRepository revisions;
    private final QuestionRevisionRecipientRepository recipients;
    private final UserNotificationRepository notifications;
    private final QuestionReportRepository reports;
    private final PracticeAttemptRepository practices;
    private final ExamAttemptRepository exams;
    private final WritingSubmissionRepository writing;
    private final ObjectMapper json;
    public record Adjustment(String kind, Long attemptId, int before, int after, int total, String link) {}

    public ReportQuestion snapshot(Question q) {
        var g=q.getGroup();var source=q.getSource()!=null?q.getSource():g==null?null:g.getSource();
        return new ReportQuestion(q.getPromptKo(),g==null?null:g.getPassageKo(),g==null?null:g.getScriptKo(),
            g==null?null:media(g.getAudioFile()),g==null?null:media(g.getImageFile()),
            q.options().stream().map(o->isImage(o)?new Choice(null,media(o)):new Choice(o,null)).toList(),q.getAnswerIndex(),q.getExplanationVi(),null,null,null,source==null?null:source.getId());
    }
    public ReportQuestion snapshot(WritingPrompt p) {
        com.fasterxml.jackson.databind.JsonNode chart=null;
        try {if(p.getChartJson()!=null && !p.getChartJson().isBlank())chart=json.readTree(p.getChartJson());}catch(Exception ex){throw new IllegalArgumentException("Biểu đồ không hợp lệ.");}
        String model=p.getNo()<=52 ? "㉠ "+Objects.toString(p.getKeys1(),"")+"\n㉡ "+Objects.toString(p.getKeys2(),"")+"\n"+Objects.toString(p.getModelAnswer(),"") : p.getModelAnswer();
        return new ReportQuestion(p.getPromptKo(),null,null,null,media(p.getChartImage()),List.of(),null,null,model,p.getRubricNote(),chart,p.getSource()==null?null:p.getSource().getId());
    }
    public QuestionRevision record(Question q,ReportQuestion before,Long reportId,String type,String policy,String note) {
        return record(q.getId(),"MC",before,snapshot(q),reportId,type,policy,note);
    }
    public QuestionRevision recordWriting(WritingPrompt p,ReportQuestion before,Long reportId,String type,String note) {
        return record(p.getId(),"WRITING",before,snapshot(p),reportId,type,"NO_SCORE_CHANGE",note);
    }
    private QuestionRevision record(Long qid,String kind,ReportQuestion before,ReportQuestion after,Long reportId,String type,String policy,String note) {
        if (Objects.equals(before,after)) return null;
        if(!Set.of("CONTENT","ANSWER","IMAGE","AUDIO","SPELLING").contains(type)) throw new IllegalArgumentException("Loại sửa lỗi không hợp lệ.");
        if(!Set.of("RECALCULATE","CREDIT_ANSWERED","NO_SCORE_CHANGE").contains(policy)) throw new IllegalArgumentException("Cách xử lý điểm không hợp lệ.");
        boolean keyChanged=!Objects.equals(before.answer(),after.answer());
        if(keyChanged)policy="RECALCULATE";
        QuestionRevision r=new QuestionRevision();r.setQuestionId(qid);r.setKind(kind);r.setReportId(reportId);
        r.setCorrectionType(type);r.setScorePolicy(policy);r.setNote(note==null?"Đã cập nhật câu hỏi.":note);
        revisions.saveAndFlush(r);
        r.setBeforeJson(serialize(before).replace("/cau-hoi-da-sua/REV/","/cau-hoi-da-sua/"+r.getId()+"/"));
        r.setAfterJson(serialize(after).replace("/cau-hoi-da-sua/REV/","/cau-hoi-da-sua/"+r.getId()+"/"));
        Map<Long,List<Adjustment>> audience=new LinkedHashMap<>();
        QuestionReport report=reportId==null?null:reports.findById(reportId).orElseThrow();
        boolean broadcast=!"SPELLING".equals(type) || keyChanged || "CREDIT_ANSWERED".equals(policy);
        if("MC".equals(kind)) {
            for(PracticeAttempt a:practices.findCompletedWithQuestion(qid.toString())) {
                Map<String,String> answers=CheckedAnswers.read(a.getAnswersJson());String selected=answers.get(qid.toString());
                if(selected==null || !selected.matches("[1-4]"))continue;
                Map<String,String> credited=CheckedAnswers.read(a.getCreditedAnswersJson());
                boolean oldCorrect=credited.containsKey(qid.toString()) || selected.equals(String.valueOf(before.answer()));
                if("CREDIT_ANSWERED".equals(policy))credited.put(qid.toString(),"1");
                boolean newCorrect=credited.containsKey(qid.toString()) || selected.equals(String.valueOf(after.answer()));
                int old=a.getScore(), updated=old;
                if(!"NO_SCORE_CHANGE".equals(policy))updated=Math.max(0,Math.min(a.getTotal(),old+(newCorrect?1:0)-(oldCorrect?1:0)));
                a.setScore(updated);a.setCreditedAnswersJson(CheckedAnswers.write(credited));
                if(broadcast || report!=null && Objects.equals(report.getUserId(),a.getUserId())) audience.computeIfAbsent(a.getUserId(),id->new ArrayList<>())
                    .add(new Adjustment("Luyện tập",a.getId(),old,updated,a.getTotal(),"/practice/result/"+a.getId()+"#question-"+qid));
            }
            for(ExamAttempt a:exams.findCompletedWithQuestion(qid)) {
                Map<String,String> answers=CheckedAnswers.read(a.getAnswersJson()), credited=CheckedAnswers.read(a.getCreditedAnswersJson());
                int old=a.getCorrect(), delta=0;boolean answered=false;
                for(ExamItem i:a.getExam().getItems()) {
                    if(!qid.equals(i.getQuestion().getId()))continue;
                    String key="q"+i.getId(), selected=answers.get(key);
                    if(selected==null || !selected.matches("[1-4]"))continue;answered=true;
                    boolean was=credited.containsKey(key)||selected.equals(String.valueOf(before.answer()));
                    if("CREDIT_ANSWERED".equals(policy))credited.put(key,"1");
                    boolean now=credited.containsKey(key)||selected.equals(String.valueOf(after.answer()));delta+=(now?1:0)-(was?1:0);
                }
                if(!answered)continue;
                int updated="NO_SCORE_CHANGE".equals(policy)?old:Math.max(0,Math.min(a.getTotal(),old+delta));
                a.setCorrect(updated);a.setCreditedAnswersJson(CheckedAnswers.write(credited));
                if(broadcast || report!=null && Objects.equals(report.getUserId(),a.getUserId()))audience.computeIfAbsent(a.getUserId(),id->new ArrayList<>())
                    .add(new Adjustment("Đề thi",a.getId(),old,updated,a.getTotal(),"/ket-qua/"+a.getId()+"#question-"+qid));
            }
        } else {
            for(WritingSubmission w:writing.findByPayloadKey("%\"promptId\":"+qid+"%")) {
                try {if(json.readTree(w.getPayload()).path("promptId").asLong()!=qid)continue;}catch(Exception ex){throw new IllegalStateException(ex);}
                if(broadcast)audience.computeIfAbsent(w.getLearnerId(),id->new ArrayList<>());
            }
            for(ExamAttempt a:exams.findCompletedWriting("%\"w"+qid+"\":%"))
                if(broadcast && !Objects.toString(CheckedAnswers.read(a.getAnswersJson()).get("w"+qid),"").isBlank())audience.computeIfAbsent(a.getUserId(),id->new ArrayList<>());
        }
        // Người báo lỗi nhận kết quả xử lý kể cả báo từ màn xem đề; không gửi cho người chưa làm khác.
        if(report!=null)audience.computeIfAbsent(report.getUserId(),id->new ArrayList<>());
        for(var entry:audience.entrySet()) {
            QuestionRevisionRecipient recipient=new QuestionRevisionRecipient();recipient.setRevisionId(r.getId());recipient.setUserId(entry.getKey());recipient.setAdjustmentsJson(serialize(entry.getValue()));recipients.save(recipient);
            // Người báo lỗi nhận thông báo REPORT_RESOLVED riêng có link bản so sánh, tránh hai thông báo cùng lần sửa.
            if(report!=null && Objects.equals(report.getUserId(),entry.getKey()))continue;
            UserNotification n=new UserNotification();n.setUserId(entry.getKey());n.setType("QUESTION_CORRECTED");
            n.setTitle("Câu hỏi bạn đã làm được cập nhật");
            String message="Đã sửa câu hỏi. "+entry.getValue().stream().filter(a->a.before()!=a.after()).map(a->a.kind()+" #"+a.attemptId()+": "+a.before()+" → "+a.after()+"/"+a.total()).collect(java.util.stream.Collectors.joining("; "));
            if("WRITING".equals(kind))message="Đề Viết bạn đã làm được sửa. Điểm Giáo viên được giữ nguyên, cần Giáo viên đánh giá nếu nội dung sửa ảnh hưởng bài chấm.";
            n.setMessage(message.substring(0,Math.min(1000,message.length())));n.setLink("/cau-hoi-da-sua/"+r.getId());notifications.save(n);
        }
        return r;
    }
    @Transactional(readOnly=true)
    public Optional<QuestionRevision> byReport(Long id){return revisions.findTopByReportIdOrderByIdDesc(id);}
    @Transactional(readOnly=true)
    public QuestionRevision owned(Long id,Long userId,boolean editor){
        QuestionRevision r=revisions.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!editor && recipients.findByRevisionIdAndUserId(id,userId).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return r;
    }
    @Transactional(readOnly=true)
    public List<Adjustment> adjustments(Long id,Long userId){
        return recipients.findByRevisionIdAndUserId(id,userId).map(r->{try{return json.readValue(r.getAdjustmentsJson(),new com.fasterxml.jackson.core.type.TypeReference<List<Adjustment>>(){});}catch(Exception ex){throw new IllegalStateException(ex);}}).orElse(List.of());
    }
    public ReportQuestion read(String raw){try{return json.readValue(raw,ReportQuestion.class);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private String serialize(Object v){try{return json.writeValueAsString(v);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static boolean isImage(String value){return value!=null && value.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp)$");}
    private static String media(String filename){return filename==null || filename.isBlank()?null:"/cau-hoi-da-sua/REV/media/"+org.springframework.web.util.UriUtils.encodePathSegment(filename,java.nio.charset.StandardCharsets.UTF_8);}
}
