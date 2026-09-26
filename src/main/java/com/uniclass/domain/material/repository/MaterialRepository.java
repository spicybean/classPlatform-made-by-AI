package com.uniclass.domain.material.repository;

import com.uniclass.domain.material.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findByWeekSectionIdOrderByIdAsc(Long weekSectionId);

    List<Material> findByClassroomIdOrderByIdAsc(Long classroomId);

    @Query("SELECT m FROM Material m " +
           "JOIN FETCH m.classroom c " +
           "JOIN FETCH m.weekSection ws " +
           "WHERE m.id = :id")
    Optional<Material> findByIdWithClassroomAndWeek(@Param("id") Long id);
}
