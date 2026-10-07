package com.uniclass.domain.attendance.repository;

import com.uniclass.domain.attendance.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    List<AttendanceSession> findByClassroomIdOrderByStartedAtDesc(Long classroomId);

    Optional<AttendanceSession> findFirstByClassroomIdAndActiveTrueOrderByStartedAtDesc(Long classroomId);

    @Query("SELECT s FROM AttendanceSession s JOIN FETCH s.classroom c JOIN FETCH c.instructor WHERE s.id = :sessionId")
    Optional<AttendanceSession> findByIdWithClassroomAndInstructor(@Param("sessionId") Long sessionId);

    boolean existsByClassroomIdAndWeekNumberAndRoundNumber(Long classroomId, int weekNumber, int roundNumber);
}
