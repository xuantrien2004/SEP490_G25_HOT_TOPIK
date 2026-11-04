package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.QuestionCorrectionService;
import com.hottopik.service.exam.ExamImportService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.io.IOException;
import java.util.stream.Stream;

@Controller
@RequiredArgsConstructor
public class QuestionCorrectionController {
    private final QuestionCorrectionService corrections;
    private final ExamImportService imports;
    @GetMapping("/cau-hoi-da-sua/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        UserDTO user=user(session);
        var r=corrections.owned(id,user.getId(),user.isContentManager()||user.isAdmin());
        model.addAttribute("revision",r);
        model.addAttribute("beforeQuestion",corrections.read(r.getBeforeJson()));
        model.addAttribute("afterQuestion",corrections.read(r.getAfterJson()));
        model.addAttribute("adjustments",corrections.adjustments(id,user.getId()));
        model.addAttribute("currentUser",user);model.addAttribute("showAnswers",true);
        model.addAttribute("pageTitle","Câu hỏi đã được sửa · HOT TOPIK");
        return "report/correction";
    }
    @GetMapping("/cau-hoi-da-sua/{id}/media/{filename:.+}")
    public ResponseEntity<Resource> media(@PathVariable Long id,@PathVariable String filename,HttpSession session) throws IOException {
        UserDTO user=user(session);
        var r=corrections.owned(id,user.getId(),user.isContentManager()||user.isAdmin());
        String suffix="/"+org.springframework.web.util.UriUtils.encodePathSegment(filename,java.nio.charset.StandardCharsets.UTF_8);
        for(var q:java.util.List.of(corrections.read(r.getBeforeJson()),corrections.read(r.getAfterJson()))) {
            boolean allowed=Stream.concat(Stream.of(q.image(),q.audio()),q.options().stream().map(com.hottopik.service.exam.LearnerExamService.Choice::image)).anyMatch(url->url!=null&&url.endsWith(suffix));
            if(!allowed||q.sourceId()==null)continue;
            var asset=imports.findMedia(q.sourceId(),filename);if(asset.isEmpty())continue;
            Path path=imports.mediaPath(asset.get());if(!Files.exists(path))continue;
            String contentType=Files.probeContentType(path);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType==null?"application/octet-stream":contentType)).body(new FileSystemResource(path));
        }
        return ResponseEntity.notFound().build();
    }
    private UserDTO user(HttpSession session){UserDTO u=AuthHelper.getLoggedInUser(session);if(u==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return u;}
}
