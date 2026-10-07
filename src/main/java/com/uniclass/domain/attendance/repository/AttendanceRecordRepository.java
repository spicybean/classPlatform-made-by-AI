package com.uniclass.domain.attendance.repository;

import com.uniclass.domain.attendance.entity.AttendanceRecord;
import com.uniclass.domain.attendance.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    @Query("SELECT r FROM AttendanceRecord r JOIN FETCH r.student WHERE r.session.id = :sessionId ORDER BY r.student.name ASC")
    List<AttendanceRecord> findBySessionIdWithStudent(@Param("sessionId") Long sessionId);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    long countBySessionIdAndStatus(Long sessionId, AttendanceStatus status);

    @Query("SELECT r FROM AttendanceRecord r JOIN FETCH r.session s " +
           "WHERE s.classroom.id = :classroomId AND r.student.id = :studentId " +
           "ORDER BY s.weekNumber ASC, s.roundNumber ASC")
    List<AttendanceRecord> findByClassroomIdAndStudentId(@Param("classroomId") Long classroomId,
                                                         @Param("studentId") Long studentId);

    @Query("SELECT r.status, COUNT(r) FROM AttendanceRecord r WHERE r.session.id = :sessionId GROUP BY r.status")
    List<Object[]> countGroupedByStatus(@Param("sessionId") Long sessionId);
}
