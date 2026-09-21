package com.mockinterview.web;

import com.mockinterview.model.AppUser;
import com.mockinterview.service.AuthService;
import com.mockinterview.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private final InterviewService interviews;
    private final AuthService auth;

    public InterviewController(InterviewService interviews, AuthService auth) {
        this.interviews = interviews;
        this.auth = auth;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal String email) {
        AppUser user = auth.requireUser(email);
        return Map.of("name", user.getName(), "email", user.getEmail(), "topics", interviews.suggestedTopics());
    }

    @PostMapping("/start")
    public Dtos.StartResponse start(@AuthenticationPrincipal String email,
                                    @Valid @RequestBody Dtos.StartRequest req) {
        return interviews.start(auth.requireUser(email), req);
    }

    @PostMapping("/{sessionId}/answer")
    public Dtos.AnswerResponse answer(@AuthenticationPrincipal String email,
                                      @PathVariable Long sessionId,
                                      @Valid @RequestBody Dtos.AnswerRequest req) {
        return interviews.answer(auth.requireUser(email), sessionId, req);
    }

    @GetMapping("/history")
    public List<Dtos.HistoryItem> history(@AuthenticationPrincipal String email) {
        return interviews.history(auth.requireUser(email));
    }

    @GetMapping("/{sessionId}")
    public Dtos.SessionDetail detail(@AuthenticationPrincipal String email, @PathVariable Long sessionId) {
        return interviews.detail(auth.requireUser(email), sessionId);
    }
}
