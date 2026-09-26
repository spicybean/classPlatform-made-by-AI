package com.uniclass.domain.classroom.repository;

import com.uniclass.domain.classroom.entity.ClassRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassRoomRepository extends JpaRepository<ClassRoom, Long> {

    Optional<ClassRoom> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    List<ClassRoom> findByInstructorIdOrderByCreatedAtDesc(Long instructorId);

    @Query("SELECT c FROM ClassRoom c JOIN FETCH c.instructor WHERE c.id = :id")
    Optional<ClassRoom> findByIdWithInstructor(@Param("id") Long id);
}
