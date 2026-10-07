package com.uniclass.domain.library.dto;

import com.uniclass.domain.library.entity.LibraryCategory;
import com.uniclass.domain.library.entity.LibraryItem;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class LibraryItemResponseDto {

    private Long id;
    private String title;
    private String description;
    private LibraryCategory category;
    private String categoryDisplayName;
    private String categoryIconClass;
    private String categoryBadgeClass;
    private String originalFilename;
    private String fileExtension;
    private long fileSize;
    private String formattedFileSize;
    private int downloadCount;
    private String formattedCreatedAt;

    public static LibraryItemResponseDto from(LibraryItem item) {
        String ext = "";
        if (item.getOriginalFilename() != null && item.getOriginalFilename().contains(".")) {
            ext = item.getOriginalFilename().substring(item.getOriginalFilename().lastIndexOf(".") + 1).toLowerCase();
        }

        return LibraryItemResponseDto.builder()
                .id(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .category(item.getCategory())
                .categoryDisplayName(item.getCategory().getDisplayName())
                .categoryIconClass(item.getCategory().getIconClass())
                .categoryBadgeClass(item.getCategory().getBadgeClass())
                .originalFilename(item.getOriginalFilename())
                .fileExtension(ext)
                .fileSize(item.getFileSize())
                .formattedFileSize(formatBytes(item.getFileSize()))
                .downloadCount(item.getDownloadCount())
                .formattedCreatedAt(item.getCreatedAt() != null ?
                        item.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "")
                .build();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
