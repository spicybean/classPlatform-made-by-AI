package com.uniclass.domain.library.repository;

import com.uniclass.domain.library.entity.LibraryCategory;
import com.uniclass.domain.library.entity.LibraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, Long> {

    List<LibraryItem> findByInstructorIdOrderByCreatedAtDesc(Long instructorId);

    List<LibraryItem> findByInstructorIdAndCategoryOrderByCreatedAtDesc(Long instructorId, LibraryCategory category);

    List<LibraryItem> findByInstructorIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(Long instructorId, String title);

    List<LibraryItem> findByInstructorIdAndCategoryAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(Long instructorId, LibraryCategory category, String title);

    long countByInstructorId(Long instructorId);

    long countByInstructorIdAndCategory(Long instructorId, LibraryCategory category);

    @Query("SELECT COALESCE(SUM(l.fileSize), 0) FROM LibraryItem l WHERE l.instructor.id = :instructorId")
    long sumFileSizeByInstructorId(@Param("instructorId") Long instructorId);

    @Query("SELECT l FROM LibraryItem l JOIN FETCH l.instructor WHERE l.id = :id")
    Optional<LibraryItem> findByIdWithInstructor(@Param("id") Long id);
}
