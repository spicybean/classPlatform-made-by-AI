package com.uniclass.domain.library.service;

import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.library.dto.CreateLibraryItemDto;
import com.uniclass.domain.library.dto.ImportFromLibraryDto;
import com.uniclass.domain.library.dto.LibraryItemResponseDto;
import com.uniclass.domain.library.dto.LibrarySummaryDto;
import com.uniclass.domain.library.entity.LibraryCategory;
import com.uniclass.domain.library.entity.LibraryItem;
import com.uniclass.domain.library.repository.LibraryItemRepository;
import com.uniclass.domain.material.entity.Material;
import com.uniclass.domain.material.entity.WeekSection;
import com.uniclass.domain.material.repository.MaterialRepository;
import com.uniclass.domain.material.repository.WeekSectionRepository;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import com.uniclass.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LibraryService {

    private final LibraryItemRepository libraryItemRepository;
    private final UserRepository userRepository;
    private final ClassRoomRepository classRoomRepository;
    private final WeekSectionRepository weekSectionRepository;
    private final MaterialRepository materialRepository;
    private final FileStorageService fileStorageService;

    public record DownloadResult(Resource resource, String filename, String contentType) {}

    /**
     * 개인 보관함에 새 파일 업로드 (교수 전용)
     */
    @Transactional
    public Long uploadItem(Long instructorId, CreateLibraryItemDto dto) {
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("교수 사용자를 찾을 수 없습니다: " + instructorId));

        if (dto.getFile() == null || dto.getFile().isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일을 첨부해 주세요.");
        }

        // 파일 디스크 저장 (보관함 격리 폴더)
        FileStorageService.StoredFile storedFile = fileStorageService.store(
                dto.getFile(),
                "library/instructor_" + instructorId
        );

        LibraryItem item = LibraryItem.builder()
                .instructor(instructor)
                .title(dto.getTitle().trim())
                .description(dto.getDescription() != null ? dto.getDescription().trim() : null)
                .category(dto.getCategory() != null ? dto.getCategory() : LibraryCategory.LECTURE_NOTE)
                .originalFilename(storedFile.originalFilename())
                .storedFilename(storedFile.storedFilename())
                .filePath(storedFile.relativePath())
                .fileSize(storedFile.fileSize())
                .contentType(storedFile.contentType())
                .build();

        LibraryItem saved = libraryItemRepository.save(item);
        log.info("[MyLibrary] 새 자료 보관: id={}, title={}, instructor={}", saved.getId(), saved.getTitle(), instructor.getName());
        return saved.getId();
    }

    /**
     * 교수 본인의 보관함 자료 목록 조회 (카테고리/검색어 필터)
     */
    public List<LibraryItemResponseDto> getMyLibraryItems(Long instructorId, LibraryCategory category, String keyword) {
        List<LibraryItem> items;

        boolean hasCategory = category != null;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasCategory && hasKeyword) {
            items = libraryItemRepository.findByInstructorIdAndCategoryAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                    instructorId, category, keyword.trim());
        } else if (hasCategory) {
            items = libraryItemRepository.findByInstructorIdAndCategoryOrderByCreatedAtDesc(instructorId, category);
        } else if (hasKeyword) {
            items = libraryItemRepository.findByInstructorIdAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                    instructorId, keyword.trim());
        } else {
            items = libraryItemRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId);
        }

        return items.stream()
                .map(LibraryItemResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 보관함 요약 통계 조회 (전체 건수, 사용 용량, 카테고리별 건수)
     */
    public LibrarySummaryDto getSummary(Long instructorId) {
        long totalItems = libraryItemRepository.countByInstructorId(instructorId);
        long totalBytes = libraryItemRepository.sumFileSizeByInstructorId(instructorId);

        Map<String, Long> countMap = new HashMap<>();
        for (LibraryCategory cat : LibraryCategory.values()) {
            countMap.put(cat.name(), libraryItemRepository.countByInstructorIdAndCategory(instructorId, cat));
        }

        return LibrarySummaryDto.builder()
                .totalItems(totalItems)
                .totalBytes(totalBytes)
                .formattedTotalSize(LibrarySummaryDto.formatBytes(totalBytes))
                .countByCategory(countMap)
                .build();
    }

    /**
     * 보관함 자료 다운로드 (교수 본인 소유권 검증)
     */
    @Transactional
    public DownloadResult downloadItem(Long libraryItemId, Long instructorId) {
        LibraryItem item = libraryItemRepository.findByIdWithInstructor(libraryItemId)
                .orElseThrow(() -> new IllegalArgumentException("해당 보관함 자료를 찾을 수 없습니다: " + libraryItemId));

        if (!item.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("본인이 보관한 자료만 다운로드할 수 있습니다.");
        }

        item.increaseDownloadCount();
        Resource resource = fileStorageService.loadAsResource(item.getFilePath());
        return new DownloadResult(resource, item.getOriginalFilename(), item.getContentType());
    }

    /**
     * 보관함 자료 삭제 (교수 본인 소유권 검증 및 파일 삭제)
     */
    @Transactional
    public void deleteItem(Long libraryItemId, Long instructorId) {
        LibraryItem item = libraryItemRepository.findByIdWithInstructor(libraryItemId)
                .orElseThrow(() -> new IllegalArgumentException("해당 보관함 자료를 찾을 수 없습니다: " + libraryItemId));

        if (!item.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("본인이 보관한 자료만 삭제할 수 있습니다.");
        }

        fileStorageService.delete(item.getFilePath());
        libraryItemRepository.delete(item);
        log.info("[MyLibrary] 자료 삭제 완료: id={}, title={}", libraryItemId, item.getTitle());
    }

    /**
     * 🌟 내 보관함의 자료를 특정 수업 주차에 원클릭 가져오기 (Import)
     * - 수업 소유권 및 보관함 자료 소유권 동시 검증 (BOLA 방어)
     * - 원본 파일 보존을 위해 수업 디렉터리로 복제(Duplicate) 후 Material 엔티티 생성
     */
    @Transactional
    public Long importToClassroom(Long classroomId, Long instructorId, ImportFromLibraryDto dto) {
        // 1. 수업 검증
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다: " + classroomId));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 자료를 가져올 수 있습니다.");
        }

        classRoom.assertEditable();

        // 2. 보관함 자료 검증
        LibraryItem libraryItem = libraryItemRepository.findByIdWithInstructor(dto.getLibraryItemId())
                .orElseThrow(() -> new IllegalArgumentException("해당 보관함 자료를 찾을 수 없습니다: " + dto.getLibraryItemId()));

        if (!libraryItem.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("본인의 보관함에 저장된 자료만 수업으로 가져올 수 있습니다.");
        }

        // 3. 파일 복제 (수업용 독립 파일로 생성)
        FileStorageService.StoredFile copiedFile = fileStorageService.copy(
                libraryItem.getFilePath(),
                "materials/class_" + classroomId
        );

        // 4. 주차 섹션 확보 (없으면 생성)
        WeekSection weekSection = weekSectionRepository.findByClassroomIdAndWeekNumber(classroomId, dto.getWeekNumber())
                .orElseGet(() -> weekSectionRepository.save(WeekSection.builder()
                        .classroom(classRoom)
                        .weekNumber(dto.getWeekNumber())
                        .title(dto.getWeekNumber() + "주차")
                        .build()));

        // 5. 강의 자료 엔티티 생성
        String title = (dto.getCustomTitle() != null && !dto.getCustomTitle().isBlank())
                ? dto.getCustomTitle().trim() : libraryItem.getTitle();
        String description = (dto.getCustomDescription() != null && !dto.getCustomDescription().isBlank())
                ? dto.getCustomDescription().trim() : libraryItem.getDescription();

        Material material = Material.builder()
                .classroom(classRoom)
                .weekSection(weekSection)
                .title(title)
                .description(description)
                .originalFilename(copiedFile.originalFilename())
                .storedFilename(copiedFile.storedFilename())
                .filePath(copiedFile.relativePath())
                .fileSize(copiedFile.fileSize())
                .contentType(copiedFile.contentType())
                .published(true)
                .build();

        Material savedMaterial = materialRepository.save(material);
        log.info("[MyLibrary] 수업 가져오기 성공: classId={}, week={}, materialId={}, sourceLibraryId={}",
                classroomId, dto.getWeekNumber(), savedMaterial.getId(), libraryItem.getId());

        return savedMaterial.getId();
    }
}
