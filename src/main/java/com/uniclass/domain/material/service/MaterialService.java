package com.uniclass.domain.material.service;

import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.material.dto.CreateMaterialDto;
import com.uniclass.domain.material.dto.MaterialResponseDto;
import com.uniclass.domain.material.dto.WeekSectionDto;
import com.uniclass.domain.material.entity.Material;
import com.uniclass.domain.material.entity.WeekSection;
import com.uniclass.domain.material.repository.MaterialRepository;
import com.uniclass.domain.material.repository.WeekSectionRepository;
import com.uniclass.global.storage.FileStorageService;
import com.uniclass.global.storage.FileStorageService.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MaterialService {

    private final ClassRoomRepository classRoomRepository;
    private final WeekSectionRepository weekSectionRepository;
    private final MaterialRepository materialRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final FileStorageService fileStorageService;

    public MaterialService(ClassRoomRepository classRoomRepository,
                           WeekSectionRepository weekSectionRepository,
                           MaterialRepository materialRepository,
                           EnrollmentRepository enrollmentRepository,
                           FileStorageService fileStorageService) {
        this.classRoomRepository = classRoomRepository;
        this.weekSectionRepository = weekSectionRepository;
        this.materialRepository = materialRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 1~16주차 기본 섹션 자동 초기화 (수업 개설 시 또는 첫 조회 시)
     */
    @Transactional
    public void initializeDefaultWeeks(ClassRoom classRoom) {
        if (weekSectionRepository.existsByClassroomId(classRoom.getId())) {
            return;
        }

        List<WeekSection> defaultWeeks = new ArrayList<>(16);
        for (int i = 1; i <= 16; i++) {
            defaultWeeks.add(WeekSection.builder()
                    .classroom(classRoom)
                    .weekNumber(i)
                    .title(i + "주차")
                    .description(null)
                    .build());
        }
        weekSectionRepository.saveAll(defaultWeeks);
    }

    /**
     * 수업의 주차별 자료 목록 조회 (학생/교수 권한별 필터링)
     */
    @Transactional
    public List<WeekSectionDto> getWeeksWithMaterials(Long classroomId, Long currentUserId, boolean isInstructor) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다: " + classroomId));

        // 주차 섹션이 없으면 기본 1~16주차 생성
        if (!weekSectionRepository.existsByClassroomId(classroomId)) {
            initializeDefaultWeeks(classRoom);
        }

        List<WeekSection> sections = weekSectionRepository.findWithMaterialsByClassroomId(classroomId);

        return sections.stream().map(section -> {
            List<MaterialResponseDto> materialDtos = section.getMaterials().stream()
                    // 학생인 경우 지금 시점에 공개된 자료만 노출
                    .filter(m -> isInstructor || m.isAvailableNow())
                    .map(MaterialResponseDto::from)
                    .collect(Collectors.toList());

            return WeekSectionDto.from(section, materialDtos);
        }).collect(Collectors.toList());
    }

    /**
     * 강의 자료 업로드 (교수 전용)
     */
    @Transactional
    public Long uploadMaterial(Long classroomId, Long instructorId, CreateMaterialDto dto) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다: " + classroomId));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 자료를 등록할 수 있습니다.");
        }

        if (classRoom.isArchived()) {
            throw new IllegalArgumentException("종료되어 보관된 수업에는 새로운 자료를 등록할 수 없습니다.");
        }

        if (dto.getFile() == null || dto.getFile().isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일을 첨부해 주세요.");
        }

        // 주차 섹션 확보 (없으면 생성)
        WeekSection weekSection = weekSectionRepository.findByClassroomIdAndWeekNumber(classroomId, dto.getWeekNumber())
                .orElseGet(() -> weekSectionRepository.save(WeekSection.builder()
                        .classroom(classRoom)
                        .weekNumber(dto.getWeekNumber())
                        .title(dto.getWeekNumber() + "주차")
                        .build()));

        // 파일 스토리지 저장
        StoredFile storedFile = fileStorageService.store(dto.getFile(), "materials/class_" + classroomId);

        // 예약 공개 일시 파싱
        LocalDateTime releaseAt = null;
        if (dto.getReleaseAt() != null && !dto.getReleaseAt().isBlank()) {
            try {
                releaseAt = LocalDateTime.parse(dto.getReleaseAt().trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("예약 공개 일시 형식이 올바르지 않습니다 (예: 2026-03-15T09:00).");
            }
        }

        Material material = Material.builder()
                .classroom(classRoom)
                .weekSection(weekSection)
                .title(dto.getTitle().trim())
                .description(dto.getDescription() != null ? dto.getDescription().trim() : null)
                .originalFilename(storedFile.originalFilename())
                .storedFilename(storedFile.storedFilename())
                .filePath(storedFile.relativePath())
                .fileSize(storedFile.fileSize())
                .contentType(storedFile.contentType())
                .isPublished(dto.isPublished())
                .releaseAt(releaseAt)
                .build();

        return materialRepository.save(material).getId();
    }

    /**
     * 강의 자료 다운로드 처리 (권한 검증 및 다운로드 카운트 증가)
     */
    @Transactional
    public DownloadResult downloadMaterial(Long materialId, Long currentUserId, boolean isInstructor) {
        Material material = materialRepository.findByIdWithClassroomAndWeek(materialId)
                .orElseThrow(() -> new IllegalArgumentException("해당 강의 자료를 찾을 수 없습니다: " + materialId));

        ClassRoom classRoom = material.getClassroom();

        // 권한 확인: 교수 본인이거나 해당 수업에 수강 중인 학생이어야 함
        if (classRoom.getInstructor().getId().equals(currentUserId)) {
            // 담당 교수 접근 허용
        } else {
            Optional<Enrollment> enrollment = enrollmentRepository.findByStudentIdAndClassroomId(currentUserId, classRoom.getId());
            if (enrollment.isEmpty() || enrollment.get().getStatus() != EnrollmentStatus.ENROLLED) {
                throw new IllegalArgumentException("해당 수업의 수강생만 강의 자료를 다운로드할 수 있습니다.");
            }

            // 학생인 경우 공개 시점 검증
            if (!material.isAvailableNow()) {
                throw new IllegalArgumentException("아직 공개되지 않은 강의 자료입니다.");
            }
        }

        material.increaseDownloadCount();

        Resource resource = fileStorageService.loadAsResource(material.getFilePath());
        return new DownloadResult(resource, material.getOriginalFilename(), material.getContentType());
    }

    /**
     * 강의 자료 삭제 (교수 전용)
     */
    @Transactional
    public void deleteMaterial(Long materialId, Long instructorId) {
        Material material = materialRepository.findByIdWithClassroomAndWeek(materialId)
                .orElseThrow(() -> new IllegalArgumentException("해당 강의 자료를 찾을 수 없습니다: " + materialId));

        if (!material.getClassroom().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 자료를 삭제할 수 있습니다.");
        }

        fileStorageService.delete(material.getFilePath());
        materialRepository.delete(material);
    }

    public record DownloadResult(
            Resource resource,
            String originalFilename,
            String contentType
    ) {}
}
