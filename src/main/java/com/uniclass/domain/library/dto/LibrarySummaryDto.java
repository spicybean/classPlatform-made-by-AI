package com.uniclass.domain.library.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class LibrarySummaryDto {
    private long totalItems;
    private long totalBytes;
    private String formattedTotalSize;
    private Map<String, Long> countByCategory;

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
