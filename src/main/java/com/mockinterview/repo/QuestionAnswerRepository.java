package com.mockinterview.repo;

import com.mockinterview.model.InterviewSession;
import com.mockinterview.model.QuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionAnswerRepository extends JpaRepository<QuestionAnswer, Long> {
    List<QuestionAnswer> findBySessionOrderByIdAsc(InterviewSession session);
}
