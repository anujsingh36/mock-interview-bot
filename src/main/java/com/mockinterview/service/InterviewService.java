package com.mockinterview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockinterview.model.AppUser;
import com.mockinterview.model.InterviewSession;
import com.mockinterview.model.QuestionAnswer;
import com.mockinterview.repo.InterviewSessionRepository;
import com.mockinterview.repo.QuestionAnswerRepository;
import com.mockinterview.web.Dtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InterviewService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.systemDefault());

    private final InterviewSessionRepository sessions;
    private final QuestionAnswerRepository qas;
    private final GeminiService gemini;
    private final int questionsPerSession;

    public InterviewService(InterviewSessionRepository sessions,
                           QuestionAnswerRepository qas,
                           GeminiService gemini,
                           @Value("${interview.questions-per-session}") int questionsPerSession) {
        this.sessions = sessions;
        this.qas = qas;
        this.gemini = gemini;
        this.questionsPerSession = questionsPerSession;
    }

    // ------------------------------------------------------------------
    // Start an interview
    // ------------------------------------------------------------------
    @Transactional
    public Dtos.StartResponse start(AppUser user, Dtos.StartRequest req) {
        InterviewSession session = new InterviewSession();
        session.setUser(user);
        session.setTopic(req.topic().trim());
        session.setLevel(req.level().trim());
        sessions.save(session);

        String question = generateQuestion(session, List.of());
        saveQuestion(session, question);

        return new Dtos.StartResponse(session.getId(), question, 1, questionsPerSession);
    }

    // ------------------------------------------------------------------
    // Submit an answer -> Gemini scores it -> next question
    // ------------------------------------------------------------------
    @Transactional
    public Dtos.AnswerResponse answer(AppUser user, Long sessionId, Dtos.AnswerRequest req) {
        InterviewSession session = loadOwned(user, sessionId);
        if (session.isFinished()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This interview is already finished.");
        }

        List<QuestionAnswer> transcript = qas.findBySessionOrderByIdAsc(session);
        QuestionAnswer pending = transcript.isEmpty() ? null : transcript.get(transcript.size() - 1);
        if (pending == null || pending.getScore() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no question waiting for an answer.");
        }

        JsonNode judged = gemini.askForJson(evaluationPrompt(session, pending.getQuestion(), req.answer()));
        int score = clamp(judged.path("score").asInt(0));
        String feedback = textOr(judged, "feedback", "No feedback returned.");
        String modelAnswer = textOr(judged, "model_answer", "");

        pending.setAnswer(req.answer());
        pending.setScore(score);
        pending.setFeedback(feedback);
        qas.save(pending);

        session.setAnsweredCount(session.getAnsweredCount() + 1);
        session.setTotalScore(session.getTotalScore() + score);

        boolean finished = session.getAnsweredCount() >= questionsPerSession;
        String nextQuestion = null;
        String summary = null;

        if (finished) {
            session.setFinished(true);
            session.setFinishedAt(Instant.now());
            summary = generateSummary(session);
            session.setSummary(summary);
            session.setCurrentQuestion(null);
        } else {
            nextQuestion = generateQuestion(session, qas.findBySessionOrderByIdAsc(session));
            saveQuestion(session, nextQuestion);
        }
        sessions.save(session);

        return new Dtos.AnswerResponse(
                score,
                feedback,
                modelAnswer,
                nextQuestion,
                Math.min(session.getAnsweredCount() + 1, questionsPerSession),
                questionsPerSession,
                finished,
                average(session),
                summary);
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<Dtos.HistoryItem> history(AppUser user) {
        return sessions.findByUserOrderByStartedAtDesc(user).stream()
                .map(this::toHistoryItem)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Dtos.SessionDetail detail(AppUser user, Long sessionId) {
        InterviewSession session = loadOwned(user, sessionId);
        List<Dtos.TranscriptItem> items = qas.findBySessionOrderByIdAsc(session).stream()
                .map(q -> new Dtos.TranscriptItem(q.getQuestion(), q.getAnswer(), q.getScore(), q.getFeedback()))
                .collect(Collectors.toList());
        return new Dtos.SessionDetail(toHistoryItem(session), items);
    }

    // ------------------------------------------------------------------
    // Prompts — this is the whole "prompt engineering" part
    // ------------------------------------------------------------------
    private String generateQuestion(InterviewSession session, List<QuestionAnswer> asked) {
        String previous = asked.stream()
                .map(q -> "- " + q.getQuestion())
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are a friendly but sharp technical interviewer for a %s-level role.
                Topic: %s.

                Ask exactly ONE interview question. Rules:
                - Keep it under 40 words, conversational, like a real person speaking.
                - Do not repeat or rephrase any of these already-asked questions:
                %s
                - No greetings, no numbering, no preamble.

                Reply with JSON only: {"question": "..."}
                """.formatted(session.getLevel(), session.getTopic(),
                        previous.isBlank() ? "(none yet)" : previous);

        JsonNode node = gemini.askForJson(prompt);
        String question = textOr(node, "question", "").trim();
        if (question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not generate a question. Try again.");
        }
        return question;
    }

    private String evaluationPrompt(InterviewSession session, String question, String answer) {
        return """
                You are a technical interviewer for a %s-level %s role.

                Question: %s
                Candidate's answer: %s

                Score the answer out of 10. Be fair: reward correct ideas even if the
                wording is simple, and subtract for factual mistakes or missing key points.
                If the answer is empty or unrelated, score it 0.

                Write feedback in 2-3 short sentences, speaking directly to the candidate
                ("you"). Warm and specific, never generic praise.
                Then give a concise ideal answer in 3-4 sentences.

                Reply with JSON only:
                {"score": 7, "feedback": "...", "model_answer": "..."}
                """.formatted(session.getLevel(), session.getTopic(), question, answer);
    }

    private String generateSummary(InterviewSession session) {
        String transcript = qas.findBySessionOrderByIdAsc(session).stream()
                .map(q -> "Q: " + q.getQuestion() + "\nA: " + q.getAnswer() + "\nScore: " + q.getScore())
                .collect(Collectors.joining("\n\n"));

        String prompt = """
                You are a mentor reviewing a mock interview on %s (%s level).

                Transcript:
                %s

                Write a short closing review for the candidate: what they clearly know,
                the two weakest areas, and one concrete thing to study next.
                Max 90 words, speak directly to them.

                Reply with JSON only: {"summary": "..."}
                """.formatted(session.getTopic(), session.getLevel(), transcript);

        try {
            return textOr(gemini.askForJson(prompt), "summary", "");
        } catch (RuntimeException e) {
            return "Interview complete. Review the feedback on each answer above.";
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private void saveQuestion(InterviewSession session, String question) {
        QuestionAnswer qa = new QuestionAnswer();
        qa.setSession(session);
        qa.setQuestion(question);
        qas.save(qa);

        session.setCurrentQuestion(question);
        session.setAskedCount(session.getAskedCount() + 1);
        sessions.save(session);
    }

    private InterviewSession loadOwned(AppUser user, Long sessionId) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found."));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That interview belongs to someone else.");
        }
        return session;
    }

    private Dtos.HistoryItem toHistoryItem(InterviewSession s) {
        return new Dtos.HistoryItem(
                s.getId(), s.getTopic(), s.getLevel(), s.getAnsweredCount(),
                average(s), s.isFinished(), DATE_FMT.format(s.getStartedAt()), s.getSummary());
    }

    private int average(InterviewSession s) {
        return s.getAnsweredCount() == 0 ? 0 : Math.round((float) s.getTotalScore() / s.getAnsweredCount());
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(10, score));
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText();
    }

    public List<String> suggestedTopics() {
        return new ArrayList<>(List.of("Core Java", "OOP Concepts", "Spring Boot", "DBMS & SQL",
                "Data Structures", "Operating Systems", "HR / Behavioural"));
    }
}
