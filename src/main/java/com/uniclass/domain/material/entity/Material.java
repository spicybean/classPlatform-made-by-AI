package com.uniclass.domain.material.entity;

import com.uniclass.domain.classroom.entity.ClassRoom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "materials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "week_section_id", nullable = false)
    private WeekSection weekSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private ClassRoom classroom;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

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

    @Column(name = "is_published", nullable = false)
    private boolean isPublished = true;

    @Column(name = "release_at")
    private LocalDateTime releaseAt;

    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Material(WeekSection weekSection, ClassRoom classroom, String title, String description,
                    String originalFilename, String storedFilename, String filePath,
                    long fileSize, String contentType, boolean isPublished, LocalDateTime releaseAt) {
        this.weekSection = weekSection;
        this.classroom = classroom;
        this.title = title;
        this.description = description;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.isPublished = isPublished;
        this.releaseAt = releaseAt;
        this.downloadCount = 0;
    }

    public void increaseDownloadCount() {
        this.downloadCount++;
    }

    /**
     * 학생 기준 지금 시점에 열람/다운로드가 가능한지 확인
     */
    public boolean isAvailableNow() {
        if (!isPublished) {
            return false;
        }
        if (releaseAt != null && releaseAt.isAfter(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    /**
     * 파일 확장자 가져오기 (소문자)
     */
    public String getFileExtension() {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 사용자 친화적인 파일 크기 포맷 (KB, MB)
     */
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
}
