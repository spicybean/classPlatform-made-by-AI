package com.uniclass.domain.material.repository;

import com.uniclass.domain.material.entity.WeekSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WeekSectionRepository extends JpaRepository<WeekSection, Long> {

    List<WeekSection> findByClassroomIdOrderByWeekNumberAsc(Long classroomId);

    Optional<WeekSection> findByClassroomIdAndWeekNumber(Long classroomId, int weekNumber);

    boolean existsByClassroomId(Long classroomId);

    @Query("SELECT DISTINCT ws FROM WeekSection ws " +
           "LEFT JOIN FETCH ws.materials m " +
           "WHERE ws.classroom.id = :classroomId " +
           "ORDER BY ws.weekNumber ASC, m.id ASC")
    List<WeekSection> findWithMaterialsByClassroomId(@Param("classroomId") Long classroomId);
}
