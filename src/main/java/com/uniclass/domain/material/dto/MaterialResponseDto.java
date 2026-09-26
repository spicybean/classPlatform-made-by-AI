package com.uniclass.domain.material.dto;

import com.uniclass.domain.material.entity.Material;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MaterialResponseDto {

    private Long id;
    private Long weekSectionId;
    private int weekNumber;
    private String title;
    private String description;
    private String originalFilename;
    private String formattedFileSize;
    private String fileExtension;
    private int downloadCount;
    private boolean isPublished;
    private LocalDateTime releaseAt;
    private boolean isAvailableNow;
    private LocalDateTime createdAt;

    public static MaterialResponseDto from(Material material) {
        return MaterialResponseDto.builder()
                .id(material.getId())
                .weekSectionId(material.getWeekSection().getId())
                .weekNumber(material.getWeekSection().getWeekNumber())
                .title(material.getTitle())
                .description(material.getDescription())
                .originalFilename(material.getOriginalFilename())
                .formattedFileSize(material.getFormattedFileSize())
                .fileExtension(material.getFileExtension())
                .downloadCount(material.getDownloadCount())
                .isPublished(material.isPublished())
                .releaseAt(material.getReleaseAt())
                .isAvailableNow(material.isAvailableNow())
                .createdAt(material.getCreatedAt())
                .build();
    }
}
