package com.uniclass.domain.assignment.service;

import com.uniclass.domain.assignment.dto.AssignmentResponseDto;
import com.uniclass.domain.assignment.dto.CreateAssignmentDto;
import com.uniclass.domain.assignment.dto.SubmissionItemDto;
import com.uniclass.domain.assignment.dto.SubmitAssignmentDto;
import com.uniclass.domain.assignment.entity.Assignment;
import com.uniclass.domain.assignment.entity.Submission;
import com.uniclass.domain.assignment.entity.SubmissionStatus;
import com.uniclass.domain.assignment.repository.AssignmentRepository;
import com.uniclass.domain.assignment.repository.SubmissionRepository;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.material.entity.WeekSection;
import com.uniclass.domain.material.repository.WeekSectionRepository;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import com.uniclass.global.storage.FileStorageService;
import com.uniclass.global.storage.FileStorageService.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WeekSectionRepository weekSectionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             SubmissionRepository submissionRepository,
                             ClassRoomRepository classRoomRepository,
                             EnrollmentRepository enrollmentRepository,
                             WeekSectionRepository weekSectionRepository,
                             UserRepository userRepository,
                             FileStorageService fileStorageService) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.classRoomRepository = classRoomRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.weekSectionRepository = weekSectionRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 과제 출제 (교수 전용)
     */
    @Transactional
    public Long createAssignment(Long classroomId, Long instructorId, CreateAssignmentDto dto) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다: " + classroomId));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 과제를 출제할 수 있습니다.");
        }

        if (classRoom.isArchived()) {
            throw new IllegalArgumentException("종료되어 보관된 수업에는 과제를 출제할 수 없습니다.");
        }

        LocalDateTime dueDate;
        try {
            dueDate = LocalDateTime.parse(dto.getDueDate().trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("마감 일시 형식이 올바르지 않습니다 (예: 2026-04-10T23:59).");
        }

        // 주차 연동 (선택 사항)
        WeekSection weekSection = null;
        if (dto.getWeekNumber() != null && dto.getWeekNumber() > 0) {
            weekSection = weekSectionRepository.findByClassroomIdAndWeekNumber(classroomId, dto.getWeekNumber())
                    .orElseGet(() -> weekSectionRepository.save(WeekSection.builder()
                            .classroom(classRoom)
                            .weekNumber(dto.getWeekNumber())
                            .title(dto.getWeekNumber() + "주차")
                            .build()));
        }

        // 첨부파일 저장 (선택 사항)
        StoredFile storedFile = null;
        if (dto.getAttachment() != null && !dto.getAttachment().isEmpty()) {
            storedFile = fileStorageService.store(dto.getAttachment(), "assignments/class_" + classroomId);
        }

        Assignment assignment = Assignment.builder()
                .classroom(classRoom)
                .weekSection(weekSection)
                .title(dto.getTitle().trim())
                .description(dto.getDescription() != null ? dto.getDescription().trim() : null)
                .dueDate(dueDate)
                .maxScore(dto.getMaxScore())
                .allowLate(dto.isAllowLate())
                .attachmentPath(storedFile != null ? storedFile.relativePath() : null)
                .attachmentOriginalFilename(storedFile != null ? storedFile.originalFilename() : null)
                .attachmentSize(storedFile != null ? storedFile.fileSize() : 0L)
                .build();

        try {
            return assignmentRepository.save(assignment).getId();
        } catch (Exception e) {
            if (storedFile != null) {
                fileStorageService.delete(storedFile.relativePath());
            }
            throw e;
        }
    }

    /**
     * 수업 내 과제 목록 조회 (수업 참여자 전용)
     */
    public List<AssignmentResponseDto> getAssignmentsForClassroom(Long classroomId, Long currentUserId, boolean isInstructor) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 수업을 찾을 수 없습니다: " + classroomId));

        boolean isEnrolled = enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(currentUserId, classroomId, EnrollmentStatus.ENROLLED);
        if (!classRoom.getInstructor().getId().equals(currentUserId) && !isEnrolled) {
            throw new IllegalArgumentException("해당 수업의 참여자만 과제 목록을 조회할 수 있습니다.");
        }

        List<Assignment> assignments = assignmentRepository.findByClassroomIdOrderByDueDateAsc(classroomId);
        long studentCount = enrollmentRepository.countByClassroomIdAndStatus(classroomId, EnrollmentStatus.ENROLLED);

        return assignments.stream().map(a -> {
            long totalSubs = submissionRepository.countByAssignmentId(a.getId());
            Submission mySub = null;
            if (!isInstructor) {
                mySub = submissionRepository.findByAssignmentIdAndStudentId(a.getId(), currentUserId).orElse(null);
            }
            return AssignmentResponseDto.from(a, totalSubs, studentCount, mySub);
        }).collect(Collectors.toList());
    }

    /**
     * 과제 상세 정보 조회
     */
    public AssignmentResponseDto getAssignmentDetail(Long classroomId, Long assignmentId, Long currentUserId, boolean isInstructor) {
        Assignment assignment = assignmentRepository.findByIdWithClassroomAndInstructor(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + assignmentId));

        if (!assignment.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업에 속한 과제가 아닙니다.");
        }

        long totalSubs = submissionRepository.countByAssignmentId(assignmentId);
        long studentCount = enrollmentRepository.countByClassroomIdAndStatus(classroomId, EnrollmentStatus.ENROLLED);

        Submission mySub = null;
        if (!isInstructor) {
            mySub = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, currentUserId).orElse(null);
        }

        return AssignmentResponseDto.from(assignment, totalSubs, studentCount, mySub);
    }

    /**
     * 과제 전체 제출 현황 조회 (교수 전용)
     */
    public List<SubmissionItemDto> getSubmissionsForAssignment(Long classroomId, Long assignmentId, Long instructorId) {
        Assignment assignment = assignmentRepository.findByIdWithClassroomAndInstructor(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + assignmentId));

        if (!assignment.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업에 속한 과제가 아닙니다.");
        }

        if (!assignment.getClassroom().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 제출 현황을 확인할 수 있습니다.");
        }

        List<Submission> submissions = submissionRepository.findByAssignmentIdWithStudent(assignmentId);
        return submissions.stream().map(SubmissionItemDto::from).collect(Collectors.toList());
    }

    /**
     * 학생 과제 제출 및 재제출 (신규/수정 통합)
     */
    @Transactional
    public Long submitAssignment(Long classroomId, Long assignmentId, Long studentId, SubmitAssignmentDto dto) {
        Assignment assignment = assignmentRepository.findByIdWithClassroomAndInstructor(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + assignmentId));

        if (!assignment.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업에 속한 과제가 아닙니다.");
        }

        // 수강생 여부 검증
        boolean isEnrolled = enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(studentId, classroomId, EnrollmentStatus.ENROLLED);
        if (!isEnrolled) {
            throw new IllegalArgumentException("해당 수업의 수강생만 과제를 제출할 수 있습니다.");
        }

        // 마감 기한 및 지각 제출 허용 검증
        boolean isExpired = assignment.isExpired();
        if (isExpired && !assignment.isAllowLate()) {
            throw new IllegalArgumentException("과제 제출 마감 일시가 지났으며, 지각 제출이 허용되지 않습니다.");
        }

        if (dto.getFile() == null || dto.getFile().isEmpty()) {
            throw new IllegalArgumentException("제출할 과제 파일을 첨부해 주세요.");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 정보를 찾을 수 없습니다: " + studentId));

        SubmissionStatus status = isExpired ? SubmissionStatus.LATE_SUBMITTED : SubmissionStatus.SUBMITTED;

        // 파일 저장
        StoredFile storedFile = fileStorageService.store(dto.getFile(),
                "submissions/class_" + classroomId + "/assign_" + assignmentId);

        // 기존 제출 확인 (재제출인 경우 파일 교체)
        Optional<Submission> existingOpt = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);

        if (existingOpt.isPresent()) {
            Submission existing = existingOpt.get();
            String oldFilePath = existing.getFilePath();

            try {
                existing.updateSubmission(
                        storedFile.relativePath(),
                        storedFile.originalFilename(),
                        storedFile.fileSize(),
                        dto.getNote() != null ? dto.getNote().trim() : null,
                        status
                );
                // 새 파일 저장 성공 후 이전 파일 정리
                fileStorageService.delete(oldFilePath);
                return existing.getId();
            } catch (Exception e) {
                fileStorageService.delete(storedFile.relativePath());
                throw e;
            }
        } else {
            Submission newSubmission = Submission.builder()
                    .assignment(assignment)
                    .student(student)
                    .filePath(storedFile.relativePath())
                    .originalFilename(storedFile.originalFilename())
                    .fileSize(storedFile.fileSize())
                    .note(dto.getNote() != null ? dto.getNote().trim() : null)
                    .status(status)
                    .build();

            try {
                return submissionRepository.save(newSubmission).getId();
            } catch (Exception e) {
                fileStorageService.delete(storedFile.relativePath());
                throw e;
            }
        }
    }

    /**
     * 학생 제출 파일 다운로드 (제출 학생 본인 또는 담당 교수)
     */
    public DownloadResult downloadSubmission(Long classroomId, Long assignmentId, Long submissionId, Long currentUserId, boolean isInstructor) {
        Submission submission = submissionRepository.findByIdWithAssignmentAndClassroom(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다: " + submissionId));

        if (!submission.getAssignment().getId().equals(assignmentId) ||
            !submission.getAssignment().getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 과제에 속한 제출 파일이 아닙니다.");
        }

        boolean isSubmitter = submission.getStudent().getId().equals(currentUserId);
        boolean isClassInstructor = submission.getAssignment().getClassroom().getInstructor().getId().equals(currentUserId);

        if (!isSubmitter && !isClassInstructor) {
            throw new IllegalArgumentException("제출자 본인 또는 수업 담당 교수만 다운로드할 수 있습니다.");
        }

        Resource resource = fileStorageService.loadAsResource(submission.getFilePath());
        return new DownloadResult(resource, submission.getOriginalFilename(), "application/octet-stream");
    }

    /**
     * 과제 첨부파일(교수 양식) 다운로드
     */
    public DownloadResult downloadAssignmentAttachment(Long classroomId, Long assignmentId, Long currentUserId) {
        Assignment assignment = assignmentRepository.findByIdWithClassroomAndInstructor(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과제를 찾을 수 없습니다: " + assignmentId));

        if (!assignment.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업에 속한 과제가 아닙니다.");
        }

        if (assignment.getAttachmentPath() == null || assignment.getAttachmentPath().isBlank()) {
            throw new IllegalArgumentException("이 과제에는 첨부된 양식 파일이 없습니다.");
        }

        // 수업 참여자 확인 (교수이거나 수강생)
        boolean isInstructor = assignment.getClassroom().getInstructor().getId().equals(currentUserId);
        boolean isEnrolled = enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(currentUserId, classroomId, EnrollmentStatus.ENROLLED);
        if (!isInstructor && !isEnrolled) {
            throw new IllegalArgumentException("수업 참여자만 첨부파일을 다운로드할 수 있습니다.");
        }

        Resource resource = fileStorageService.loadAsResource(assignment.getAttachmentPath());
        return new DownloadResult(resource, assignment.getAttachmentOriginalFilename(), "application/octet-stream");
    }

    /**
     * 과제 채점 및 피드백 등록 (교수 전용)
     */
    @Transactional
    public void gradeSubmission(Long classroomId, Long assignmentId, Long submissionId, Long instructorId, int score, String feedback) {
        Submission submission = submissionRepository.findByIdWithAssignmentAndClassroom(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("해당 제출물을 찾을 수 없습니다: " + submissionId));

        if (!submission.getAssignment().getId().equals(assignmentId) ||
            !submission.getAssignment().getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업의 과제 제출물이 아닙니다.");
        }

        if (!submission.getAssignment().getClassroom().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 채점할 수 있습니다.");
        }

        int maxScore = submission.getAssignment().getMaxScore();
        if (score < 0 || score > maxScore) {
            throw new IllegalArgumentException("점수는 0점 이상 " + maxScore + "점 이하여야 합니다.");
        }

        submission.grade(score, feedback != null ? feedback.trim() : null);
    }

    public record DownloadResult(
            Resource resource,
            String originalFilename,
            String contentType
    ) {}
}
