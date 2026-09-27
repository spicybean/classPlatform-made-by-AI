package com.uniclass.domain.assignment.repository;

import com.uniclass.domain.assignment.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByClassroomIdOrderByDueDateAsc(Long classroomId);

    List<Assignment> findByClassroomIdAndWeekSectionIdOrderByDueDateAsc(Long classroomId, Long weekSectionId);

    @Query("SELECT a FROM Assignment a JOIN FETCH a.classroom c JOIN FETCH c.instructor LEFT JOIN FETCH a.weekSection WHERE a.id = :id")
    Optional<Assignment> findByIdWithClassroomAndInstructor(@Param("id") Long id);

    long countByClassroomId(Long classroomId);
}
