package com.uniclass.domain.classroom.repository;

import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByStudentIdAndClassroomId(Long studentId, Long classroomId);

    boolean existsByStudentIdAndClassroomId(Long studentId, Long classroomId);

    @Query("SELECT e FROM Enrollment e JOIN FETCH e.classroom c JOIN FETCH c.instructor " +
           "WHERE e.student.id = :studentId AND e.status = :status ORDER BY e.enrolledAt DESC")
    List<Enrollment> findByStudentIdAndStatusWithClassroom(@Param("studentId") Long studentId,
                                                          @Param("status") EnrollmentStatus status);

    long countByClassroomIdAndStatus(Long classroomId, EnrollmentStatus status);
}
