package com.uniclass.domain.library.entity;

import com.uniclass.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "library_items", indexes = {
        @Index(name = "IDX_library_instructor", columnList = "instructor_id"),
        @Index(name = "IDX_library_category", columnList = "instructor_id, category")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LibraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id", nullable = false)
    private User instructor;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LibraryCategory category = LibraryCategory.LECTURE_NOTE;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.category == null) {
            this.category = LibraryCategory.LECTURE_NOTE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public LibraryItem(User instructor, String title, String description, LibraryCategory category,
                       String originalFilename, String storedFilename, String filePath,
                       long fileSize, String contentType) {
        this.instructor = instructor;
        this.title = title;
        this.description = description;
        this.category = category != null ? category : LibraryCategory.LECTURE_NOTE;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.downloadCount = 0;
    }

    public void updateInfo(String title, String description, LibraryCategory category) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        this.description = description != null ? description.trim() : null;
        if (category != null) {
            this.category = category;
        }
    }

    public void increaseDownloadCount() {
        this.downloadCount++;
    }
}
