package com.mockinterview.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "interview_sessions")
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private int askedCount = 0;

    @Column(nullable = false)
    private int answeredCount = 0;

    @Column(nullable = false)
    private int totalScore = 0;

    @Column(length = 2000)
    private String currentQuestion;

    @Column(length = 4000)
    private String summary;

    @Column(nullable = false)
    private boolean finished = false;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant finishedAt;

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public int getAskedCount() { return askedCount; }
    public void setAskedCount(int askedCount) { this.askedCount = askedCount; }
    public int getAnsweredCount() { return answeredCount; }
    public void setAnsweredCount(int answeredCount) { this.answeredCount = answeredCount; }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public String getCurrentQuestion() { return currentQuestion; }
    public void setCurrentQuestion(String currentQuestion) { this.currentQuestion = currentQuestion; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
