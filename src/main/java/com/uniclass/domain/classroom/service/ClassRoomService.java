package com.uniclass.domain.classroom.service;

import com.uniclass.domain.assignment.entity.Assignment;
import com.uniclass.domain.assignment.repository.AssignmentRepository;
import com.uniclass.domain.classroom.dto.ClassRoomCardDto;
import com.uniclass.domain.classroom.dto.CloneClassRoomDto;
import com.uniclass.domain.classroom.dto.CreateClassRoomDto;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.material.entity.Material;
import com.uniclass.domain.material.entity.WeekSection;
import com.uniclass.domain.material.repository.MaterialRepository;
import com.uniclass.domain.material.repository.WeekSectionRepository;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ClassRoomService {

    private static final String CODE_CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // 헷갈리기 쉬운 0, 1, I, O 제외
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final WeekSectionRepository weekSectionRepository;
    private final MaterialRepository materialRepository;
    private final AssignmentRepository assignmentRepository;

    public ClassRoomService(ClassRoomRepository classRoomRepository,
                            EnrollmentRepository enrollmentRepository,
                            UserRepository userRepository,
                            WeekSectionRepository weekSectionRepository,
                            MaterialRepository materialRepository,
                            AssignmentRepository assignmentRepository) {
        this.classRoomRepository = classRoomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.weekSectionRepository = weekSectionRepository;
        this.materialRepository = materialRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public Long createClassRoom(Long instructorId, CreateClassRoomDto dto) {
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("교수 정보를 찾을 수 없습니다."));

        if (instructor.getRole() != Role.ROLE_INSTRUCTOR) {
            throw new IllegalStateException("수업 개설은 교수 권한(ROLE_INSTRUCTOR)만 가능합니다.");
        }

        String inviteCode = generateUniqueInviteCode();

        ClassRoom classRoom = ClassRoom.builder()
                .name(dto.getName().trim())
                .courseCode(dto.getCourseCode().trim())
                .semester(dto.getSemester().trim())
                .division(dto.getDivision().trim())
                .description(dto.getDescription() != null ? dto.getDescription().trim() : "")
                .inviteCode(inviteCode)
                .instructor(instructor)
                .build();

        return classRoomRepository.save(classRoom).getId();
    }

    @Transactional
    public Long joinClassRoom(Long studentId, String inviteCode) {
        String normalizedCode = inviteCode.trim().toUpperCase();

        ClassRoom classRoom = classRoomRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("해당 초대 코드와 일치하는 수업을 찾을 수 없습니다."));

        if (classRoom.isArchived()) {
            throw new IllegalArgumentException("종료되어 보관(아카이빙)된 수업에는 참가할 수 없습니다.");
        }

        if (classRoom.getInstructor().getId().equals(studentId)) {
            throw new IllegalArgumentException("담당 교수는 자신의 수업에 수강생으로 참가할 수 없습니다.");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 정보를 찾을 수 없습니다."));

        Optional<Enrollment> existingOpt = enrollmentRepository.findByStudentIdAndClassroomId(studentId, classRoom.getId());
        if (existingOpt.isPresent()) {
            Enrollment existing = existingOpt.get();
            if (existing.getStatus() == EnrollmentStatus.ENROLLED) {
                throw new IllegalArgumentException("이미 수강 신청이 완료된 수업입니다.");
            } else {
                existing.reEnroll();
                return classRoom.getId();
            }
        }

        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .classroom(classRoom)
                .status(EnrollmentStatus.ENROLLED)
                .build();

        enrollmentRepository.save(enrollment);
        return classRoom.getId();
    }

    public List<ClassRoomCardDto> getInstructorClassrooms(Long instructorId) {
        List<ClassRoom> classes = classRoomRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId);
        if (classes.isEmpty()) {
            return List.of();
        }

        List<Long> classIds = classes.stream().map(ClassRoom::getId).toList();
        Map<Long, Long> countMap = enrollmentRepository.countByClassroomIdsAndStatus(classIds, EnrollmentStatus.ENROLLED)
                .stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));

        return classes.stream().map(c -> ClassRoomCardDto.builder()
                .id(c.getId())
                .name(c.getName())
                .courseCode(c.getCourseCode())
                .semester(c.getSemester())
                .division(c.getDivision())
                .inviteCode(c.getInviteCode())
                .instructorName(c.getInstructor().getName())
                .studentCount(countMap.getOrDefault(c.getId(), 0L))
                .isArchived(c.isArchived())
                .build()
        ).collect(Collectors.toList());
    }

    public List<ClassRoomCardDto> getStudentClassrooms(Long studentId) {
        List<Enrollment> enrollments = enrollmentRepository.findByStudentIdAndStatusWithClassroom(studentId, EnrollmentStatus.ENROLLED);
        if (enrollments.isEmpty()) {
            return List.of();
        }

        List<Long> classIds = enrollments.stream().map(e -> e.getClassroom().getId()).toList();
        Map<Long, Long> countMap = enrollmentRepository.countByClassroomIdsAndStatus(classIds, EnrollmentStatus.ENROLLED)
                .stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));

        return enrollments.stream().map(e -> {
            ClassRoom c = e.getClassroom();
            return ClassRoomCardDto.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .courseCode(c.getCourseCode())
                    .semester(c.getSemester())
                    .division(c.getDivision())
                    .inviteCode(c.getInviteCode())
                    .instructorName(c.getInstructor().getName())
                    .studentCount(countMap.getOrDefault(c.getId(), 0L))
                    .isArchived(c.isArchived())
                    .build();
        }).collect(Collectors.toList());
    }

    public ClassRoom getClassRoomDetail(Long classroomId) {
        return classRoomRepository.findByIdWithInstructor(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("수업 정보를 찾을 수 없습니다."));
    }

    @Transactional
    public void archiveClassRoom(Long classroomId, Long instructorId) {
        ClassRoom classRoom = classRoomRepository.findByIdWithInstructor(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("수업 정보를 찾을 수 없습니다."));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 보관(Archive) 처리할 수 있습니다.");
        }

        classRoom.archive();
    }

    @Transactional
    public void unarchiveClassRoom(Long classroomId, Long instructorId) {
        ClassRoom classRoom = classRoomRepository.findByIdWithInstructor(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("수업 정보를 찾을 수 없습니다."));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 보관 해제할 수 있습니다.");
        }

        classRoom.unarchive();
    }

    public List<User> getEnrolledStudents(Long classroomId) {
        return enrollmentRepository.findByClassroomIdAndStatusWithStudent(classroomId, EnrollmentStatus.ENROLLED)
                .stream()
                .map(Enrollment::getStudent)
                .collect(Collectors.toList());
    }

    public boolean isUserEnrolledOrInstructor(Long classroomId, Long userId) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("수업 정보를 찾을 수 없습니다."));
        if (classRoom.getInstructor().getId().equals(userId)) {
            return true;
        }
        return enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(userId, classroomId, EnrollmentStatus.ENROLLED);
    }

    public void assertInstructor(Long classroomId, Long instructorId) {
        ClassRoom classRoom = classRoomRepository.findByIdWithInstructor(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("수업 정보를 찾을 수 없습니다: " + classroomId));
        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("해당 수업의 담당 교수 권한이 필요합니다.");
        }
    }

    public void assertEnrolled(Long classroomId, Long studentId) {
        if (!enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(studentId, classroomId, EnrollmentStatus.ENROLLED)) {
            throw new IllegalArgumentException("해당 수업의 수강생 권한이 필요합니다.");
        }
    }

    /**
     * 수업 원클릭 복제 및 날짜 자동 시프트 (Course Clone & Date Shift)
     * - 대상: 주차 정보(날짜 시프트 포함), 강의자료(선택), 과제 템플릿(선택, 마감일 시프트 포함)
     * - 미포함(보안): 이전 학기 수강생, 학생 제출물, 출석 기록, 성적부
     */
    @Transactional
    public Long cloneClassRoom(Long instructorId, CloneClassRoomDto dto) {
        if (dto.getSourceClassroomId() == null) {
            throw new IllegalArgumentException("복제할 원본 수업 ID가 필요합니다.");
        }

        assertInstructor(dto.getSourceClassroomId(), instructorId);

        ClassRoom source = classRoomRepository.findByIdWithInstructor(dto.getSourceClassroomId())
                .orElseThrow(() -> new IllegalArgumentException("복제할 원본 수업을 찾을 수 없습니다."));

        User instructor = source.getInstructor();
        String newInviteCode = generateUniqueInviteCode();

        ClassRoom clonedClassRoom = ClassRoom.builder()
                .name(dto.getNewName().trim())
                .courseCode(source.getCourseCode())
                .semester(dto.getNewSemester().trim())
                .division(dto.getNewDivision().trim())
                .description(source.getDescription())
                .inviteCode(newInviteCode)
                .instructor(instructor)
                .build();

        ClassRoom savedClassRoom = classRoomRepository.save(clonedClassRoom);

        // 원본 수업의 주차 목록 조회 (오름차순)
        List<WeekSection> sourceSections = weekSectionRepository.findByClassroomIdOrderByWeekNumberAsc(source.getId());

        // 기준 개강일(가장 빠른 startDate) 탐색
        LocalDate baselineOldStart = sourceSections.stream()
                .map(WeekSection::getStartDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        boolean hasBaseline = (baselineOldStart != null);
        long deltaDays = hasBaseline ? ChronoUnit.DAYS.between(baselineOldStart, dto.getNewStartDate()) : 0L;

        // 주차 복제 및 매핑
        Map<Long, WeekSection> oldSectionIdToNewMap = new HashMap<>();
        for (WeekSection srcSec : sourceSections) {
            LocalDate newStart;
            LocalDate newEnd;

            if (hasBaseline && srcSec.getStartDate() != null) {
                newStart = srcSec.getStartDate().plusDays(deltaDays);
                newEnd = srcSec.getEndDate() != null ? srcSec.getEndDate().plusDays(deltaDays) : newStart.plusDays(6);
            } else {
                newStart = dto.getNewStartDate().plusWeeks(srcSec.getWeekNumber() - 1);
                newEnd = newStart.plusDays(6);
            }

            WeekSection newSec = WeekSection.builder()
                    .classroom(savedClassRoom)
                    .weekNumber(srcSec.getWeekNumber())
                    .title(srcSec.getTitle())
                    .description(srcSec.getDescription())
                    .sectionType(srcSec.getSectionType())
                    .startDate(newStart)
                    .endDate(newEnd)
                    .noticeText(srcSec.getNoticeText())
                    .isHidden(srcSec.isHidden())
                    .build();

            WeekSection savedSec = weekSectionRepository.save(newSec);
            oldSectionIdToNewMap.put(srcSec.getId(), savedSec);
        }

        // 강의 자료 복제
        if (dto.isCloneMaterials()) {
            List<Material> sourceMaterials = materialRepository.findByClassroomIdOrderByIdAsc(source.getId());
            for (Material srcMat : sourceMaterials) {
                WeekSection targetSec = oldSectionIdToNewMap.get(srcMat.getWeekSection().getId());
                if (targetSec != null) {
                    LocalDateTime newReleaseAt = (hasBaseline && srcMat.getReleaseAt() != null)
                            ? srcMat.getReleaseAt().plusDays(deltaDays)
                            : srcMat.getReleaseAt();

                    Material newMat = Material.builder()
                            .classroom(savedClassRoom)
                            .weekSection(targetSec)
                            .title(srcMat.getTitle())
                            .description(srcMat.getDescription())
                            .originalFilename(srcMat.getOriginalFilename())
                            .storedFilename(srcMat.getStoredFilename())
                            .filePath(srcMat.getFilePath())
                            .fileSize(srcMat.getFileSize())
                            .contentType(srcMat.getContentType())
                            .published(srcMat.isPublished())
                            .releaseAt(newReleaseAt)
                            .build();

                    materialRepository.save(newMat);
                }
            }
        }

        // 과제 템플릿 복제 (날짜 자동 시프트)
        if (dto.isCloneAssignments()) {
            List<Assignment> sourceAssignments = assignmentRepository.findByClassroomIdOrderByDueDateAsc(source.getId());
            for (Assignment srcAssign : sourceAssignments) {
                WeekSection targetSec = srcAssign.getWeekSection() != null
                        ? oldSectionIdToNewMap.get(srcAssign.getWeekSection().getId())
                        : null;

                LocalDateTime newDueDate;
                if (hasBaseline && srcAssign.getDueDate() != null) {
                    newDueDate = srcAssign.getDueDate().plusDays(deltaDays);
                } else if (targetSec != null && targetSec.getEndDate() != null) {
                    newDueDate = targetSec.getEndDate().atTime(23, 59, 59);
                } else {
                    newDueDate = dto.getNewStartDate().plusWeeks(1).atTime(23, 59, 59);
                }

                Assignment newAssign = Assignment.builder()
                        .classroom(savedClassRoom)
                        .weekSection(targetSec)
                        .title(srcAssign.getTitle())
                        .description(srcAssign.getDescription())
                        .dueDate(newDueDate)
                        .maxScore(srcAssign.getMaxScore())
                        .allowLate(srcAssign.isAllowLate())
                        .attachmentPath(srcAssign.getAttachmentPath())
                        .attachmentOriginalFilename(srcAssign.getAttachmentOriginalFilename())
                        .attachmentSize(srcAssign.getAttachmentSize())
                        .build();

                assignmentRepository.save(newAssign);
            }
        }

        return savedClassRoom.getId();
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_CHARACTERS.charAt(RANDOM.nextInt(CODE_CHARACTERS.length())));
            }
            String code = sb.toString();
            if (!classRoomRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드 생성에 실패했습니다. 다시 시도해 주세요.");
    }
}
