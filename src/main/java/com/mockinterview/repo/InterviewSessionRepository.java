package com.mockinterview.repo;

import com.mockinterview.model.AppUser;
import com.mockinterview.model.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    List<InterviewSession> findByUserOrderByStartedAtDesc(AppUser user);
}
