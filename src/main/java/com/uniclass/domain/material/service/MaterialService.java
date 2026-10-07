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
    private final com.uniclass.domain.assignment.repository.AssignmentRepository assignmentRepository;

    public MaterialService(ClassRoomRepository classRoomRepository,
                           WeekSectionRepository weekSectionRepository,
                           MaterialRepository materialRepository,
                           EnrollmentRepository enrollmentRepository,
                           FileStorageService fileStorageService,
                           com.uniclass.domain.assignment.repository.AssignmentRepository assignmentRepository) {
        this.classRoomRepository = classRoomRepository;
        this.weekSectionRepository = weekSectionRepository;
        this.materialRepository = materialRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.fileStorageService = fileStorageService;
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * 1~16주차 기본 섹션 자동 초기화 (외부 호출용)
     */
    @Transactional
    public void initializeDefaultWeeks(ClassRoom classRoom) {
        createDefaultWeeks(classRoom);
    }

    private void createDefaultWeeks(ClassRoom classRoom) {
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

        // 인가 검증: 수강생 또는 담당 교수만 접근 가능
        boolean isEnrolled = enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(currentUserId, classroomId, EnrollmentStatus.ENROLLED);
        boolean isClassInstructor = classRoom.getInstructor().getId().equals(currentUserId);
        if (!isEnrolled && !isClassInstructor) {
            throw new IllegalArgumentException("해당 수업에 접근 권한이 없습니다.");
        }

        // 주차 섹션이 없으면 기본 1~16주차 생성
        if (!weekSectionRepository.existsByClassroomId(classroomId)) {
            createDefaultWeeks(classRoom);
        }

        List<WeekSection> sections = weekSectionRepository.findWithMaterialsByClassroomId(classroomId);

        return sections.stream()
                // 학생 시점에서는 숨김(isHidden) 주차는 제외
                .filter(section -> isInstructor || !section.isHidden())
                .map(section -> {
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

        classRoom.assertEditable();

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
                fileStorageService.delete(storedFile.relativePath());
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
                .published(dto.isPublished())
                .releaseAt(releaseAt)
                .build();

        try {
            return materialRepository.save(material).getId();
        } catch (Exception e) {
            // DB 저장 실패 시 디스크에 저장된 물리 파일 삭제 (고아 파일 방어)
            fileStorageService.delete(storedFile.relativePath());
            throw e;
        }
    }

    /**
     * 강의 자료 다운로드 처리 (수업 ID 및 권한 검증, 다운로드 카운트 증가)
     */
    @Transactional
    public DownloadResult downloadMaterial(Long classroomId, Long materialId, Long currentUserId, boolean isInstructor) {
        Material material = materialRepository.findByIdWithClassroomAndWeek(materialId)
                .orElseThrow(() -> new IllegalArgumentException("해당 강의 자료를 찾을 수 없습니다: " + materialId));

        ClassRoom classRoom = material.getClassroom();

        // 요청된 URL의 수업과 자료가 속한 수업 일치 여부 검증
        if (!classRoom.getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업에 속한 강의 자료가 아닙니다.");
        }

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
    public void deleteMaterial(Long classroomId, Long materialId, Long instructorId) {
        Material material = materialRepository.findByIdWithClassroomAndWeek(materialId)
                .orElseThrow(() -> new IllegalArgumentException("해당 강의 자료를 찾을 수 없습니다: " + materialId));

        if (!material.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업에 속한 강의 자료가 아닙니다.");
        }

        if (!material.getClassroom().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 자료를 삭제할 수 있습니다.");
        }

        material.getClassroom().assertEditable();

        fileStorageService.delete(material.getFilePath());
        materialRepository.delete(material);
    }

    /**
     * 주차 정보 및 유형/날짜/휴강안내 수정 (교수 전용)
     */
    @Transactional
    public void updateWeekSection(Long classroomId, Long sectionId, com.uniclass.domain.material.dto.UpdateWeekSectionDto dto, Long instructorId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다: " + classroomId));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 주차 설정을 변경할 수 있습니다.");
        }

        classRoom.assertEditable();

        WeekSection section = weekSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 섹션을 찾을 수 없습니다: " + sectionId));

        if (!section.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업의 주차 섹션이 아닙니다.");
        }

        if (dto.getStartDate() != null && dto.getEndDate() != null && dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이거나 같아야 합니다.");
        }

        section.updateSectionInfo(
                dto.getTitle(),
                dto.getDescription(),
                dto.getSectionType(),
                dto.getStartDate(),
                dto.getEndDate(),
                dto.getNoticeText(),
                dto.isHidden()
        );
    }

    /**
     * 신규 주차 / 보강 주차 추가 (교수 전용)
     */
    @Transactional
    public Long addCustomWeekSection(Long classroomId, String title, com.uniclass.domain.material.entity.SectionType sectionType, Long instructorId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다: " + classroomId));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 새 주차를 추가할 수 있습니다.");
        }

        classRoom.assertEditable();

        int nextWeekNumber = weekSectionRepository.findMaxWeekNumberByClassroomId(classroomId) + 1;
        String sectionTitle = (title != null && !title.isBlank()) ? title.trim() : (nextWeekNumber + "주차 (보강)");

        WeekSection newSection = WeekSection.builder()
                .classroom(classRoom)
                .weekNumber(nextWeekNumber)
                .title(sectionTitle)
                .sectionType(sectionType != null ? sectionType : com.uniclass.domain.material.entity.SectionType.MAKEUP)
                .build();

        return weekSectionRepository.save(newSection).getId();
    }

    /**
     * 주차 섹션 삭제 (교수 전용, 자료/과제 미존재 시에만 허용)
     */
    @Transactional
    public void deleteWeekSection(Long classroomId, Long sectionId, Long instructorId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다: " + classroomId));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 주차를 삭제할 수 있습니다.");
        }

        classRoom.assertEditable();

        WeekSection section = weekSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 섹션을 찾을 수 없습니다: " + sectionId));

        if (!section.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업의 주차 섹션이 아닙니다.");
        }

        if (!section.getMaterials().isEmpty()) {
            throw new IllegalStateException("해당 주차에 등록된 강의 자료(" + section.getMaterials().size() + "건)가 있어 삭제할 수 없습니다. 자료를 먼저 삭제하거나 이동해 주세요.");
        }

        if (assignmentRepository.existsByWeekSectionId(sectionId)) {
            throw new IllegalStateException("해당 주차에 연결된 과제가 존재하여 삭제할 수 없습니다. 과제를 먼저 삭제하거나 수정해 주세요.");
        }

        weekSectionRepository.delete(section);
    }

    /**
     * 원클릭 휴강 전환 / 복구 토글 (교수 전용)
     */
    @Transactional
    public void toggleHoliday(Long classroomId, Long sectionId, String reason, Long instructorId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다: " + classroomId));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 휴강 상태를 변경할 수 있습니다.");
        }

        classRoom.assertEditable();

        WeekSection section = weekSectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 섹션을 찾을 수 없습니다: " + sectionId));

        if (!section.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업의 주차 섹션이 아닙니다.");
        }

        if (section.getSectionType() == com.uniclass.domain.material.entity.SectionType.HOLIDAY) {
            section.markAsLecture();
        } else {
            section.markAsHoliday(reason);
        }
    }

    public record DownloadResult(
            Resource resource,
            String originalFilename,
            String contentType
    ) {}
}
