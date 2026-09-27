package com.uniclass.domain.assignment.repository;

import com.uniclass.domain.assignment.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Optional<Submission> findByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    @Query("SELECT s FROM Submission s JOIN FETCH s.student WHERE s.assignment.id = :assignmentId ORDER BY s.submittedAt ASC")
    List<Submission> findByAssignmentIdWithStudent(@Param("assignmentId") Long assignmentId);

    @Query("SELECT s FROM Submission s JOIN FETCH s.assignment a JOIN FETCH a.classroom WHERE s.id = :id")
    Optional<Submission> findByIdWithAssignmentAndClassroom(@Param("id") Long id);

    long countByAssignmentId(Long assignmentId);
}
