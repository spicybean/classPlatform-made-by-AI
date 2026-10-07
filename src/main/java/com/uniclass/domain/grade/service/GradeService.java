package com.uniclass.domain.grade.service;

import com.uniclass.domain.assignment.entity.Assignment;
import com.uniclass.domain.assignment.entity.Submission;
import com.uniclass.domain.assignment.entity.SubmissionStatus;
import com.uniclass.domain.assignment.repository.AssignmentRepository;
import com.uniclass.domain.assignment.repository.SubmissionRepository;
import com.uniclass.domain.attendance.entity.AttendanceRecord;
import com.uniclass.domain.attendance.entity.AttendanceSession;
import com.uniclass.domain.attendance.entity.AttendanceStatus;
import com.uniclass.domain.attendance.repository.AttendanceRecordRepository;
import com.uniclass.domain.attendance.repository.AttendanceSessionRepository;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.grade.dto.GradeBookDto;
import com.uniclass.domain.grade.dto.GradeBookDto.GradeItemHeaderDto;
import com.uniclass.domain.grade.dto.GradeBookDto.StudentGradeRowDto;
import com.uniclass.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradeService {

    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;

    /**
     * 수업의 종합 성적부 조회 (교수는 전체 수강생, 학생은 본인 1인 열람)
     */
    public GradeBookDto getGradeBook(Long classroomId, Long currentUserId, boolean isInstructor) {
        if (classroomId == null || currentUserId == null) {
            throw new IllegalArgumentException("유효하지 않은 요청 파라미터입니다.");
        }

        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("수업을 찾을 수 없습니다: " + classroomId));

        // 호출자가 넘긴 isInstructor 플래그를 맹신하지 않고, 실제 해당 수업의 담당 교수인지 직접 검증
        boolean isActualInstructor = isInstructor
                && classRoom.getInstructor() != null
                && Objects.equals(classRoom.getInstructor().getId(), currentUserId);

        if (!isActualInstructor && !enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(currentUserId, classroomId, EnrollmentStatus.ENROLLED)) {
            throw new IllegalArgumentException("수업 접근 권한이 없습니다.");
        }

        // 1. 과제 헤더 목록
        List<Assignment> assignments = assignmentRepository.findByClassroomIdOrderByDueDateAsc(classroomId);
        List<GradeItemHeaderDto> headers = assignments.stream().map(a -> GradeItemHeaderDto.builder()
                .assignmentId(a.getId())
                .title(a.getTitle())
                .maxScore(a.getMaxScore())
                .build()
        ).collect(Collectors.toList());

        int totalPossibleMaxScore = assignments.stream().mapToInt(Assignment::getMaxScore).sum();

        // 2. 수강생 목록 (교수/학생 모두 student가 null인 비정상 레코드 사전 배제)
        List<Enrollment> enrollments = enrollmentRepository.findByClassroomIdAndStatusWithStudent(classroomId, EnrollmentStatus.ENROLLED)
                .stream()
                .filter(e -> e.getStudent() != null)
                .collect(Collectors.toList());

        if (!isActualInstructor) {
            enrollments = enrollments.stream()
                    .filter(e -> Objects.equals(e.getStudent().getId(), currentUserId))
                    .collect(Collectors.toList());
        }

        // 3. 수업 내 전체 출석 세션 수
        List<AttendanceSession> sessions = sessionRepository.findByClassroomIdOrderByStartedAtDesc(classroomId);
        long totalSessions = sessions.size();

        // 4. 학생별 성적 행 빌드
        List<StudentGradeRowDto> rows = new ArrayList<>();
        double sumTotalScores = 0;

        for (Enrollment enrollment : enrollments) {
            User student = enrollment.getStudent();
            Long studentId = student.getId();

            Map<Long, Integer> scores = new HashMap<>();
            Map<Long, String> statuses = new HashMap<>();
            int totalEarned = 0;

            for (Assignment a : assignments) {
                Optional<Submission> subOpt = submissionRepository.findByAssignmentIdAndStudentId(a.getId(), studentId);
                if (subOpt.isPresent()) {
                    Submission sub = subOpt.get();
                    if (sub.getStatus() == SubmissionStatus.GRADED && sub.getScore() != null) {
                        scores.put(a.getId(), sub.getScore());
                        statuses.put(a.getId(), "GRADED");
                        totalEarned += sub.getScore();
                    } else {
                        scores.put(a.getId(), null);
                        statuses.put(a.getId(), "SUBMITTED");
                    }
                } else {
                    scores.put(a.getId(), null);
                    statuses.put(a.getId(), "NOT_SUBMITTED");
                }
            }

            // 출석률 계산
            List<AttendanceRecord> attendanceRecords = recordRepository.findByClassroomIdAndStudentId(classroomId, studentId);
            long presentCount = attendanceRecords.stream().filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
            long lateCount = attendanceRecords.stream().filter(r -> r.getStatus() == AttendanceStatus.LATE).count();
            long excusedCount = attendanceRecords.stream().filter(r -> r.getStatus() == AttendanceStatus.EXCUSED).count();

            // 출석 점수: 출석 1.0, 지각 0.5, 공결 1.0
            double attendanceScore = presentCount + (lateCount * 0.5) + excusedCount;
            double attendanceRate = totalSessions > 0 ? Math.min(100.0, (attendanceScore / totalSessions) * 100.0) : 100.0;

            // 과제 성취율 (%)
            double assignmentPct = totalPossibleMaxScore > 0 ? ((double) totalEarned / totalPossibleMaxScore) * 100.0 : 100.0;

            // 종합 점수: 과제 80% + 출석 20%
            double composite = (assignmentPct * 0.8) + (attendanceRate * 0.2);
            composite = Math.round(composite * 10.0) / 10.0;
            sumTotalScores += composite;

            rows.add(StudentGradeRowDto.builder()
                    .studentId(studentId)
                    .studentName(student.getName())
                    .studentNo(student.getStudentNo())
                    .email(student.getEmail())
                    .scores(scores)
                    .submissionStatuses(statuses)
                    .totalEarnedScore(totalEarned)
                    .totalMaxScore(totalPossibleMaxScore)
                    .assignmentPercentage(Math.round(assignmentPct * 10.0) / 10.0)
                    .attendanceRate(Math.round(attendanceRate * 10.0) / 10.0)
                    .totalCompositeScore(composite)
                    .letterGrade(calculateLetterGrade(composite))
                    .build());
        }

        Double classAvg = null;
        if (isActualInstructor) {
            classAvg = rows.isEmpty() ? 0.0 : Math.round((sumTotalScores / rows.size()) * 10.0) / 10.0;
        }

        return GradeBookDto.builder()
                .classroomId(classRoom.getId())
                .classroomName(classRoom.getName())
                .assignmentHeaders(headers)
                .studentRows(rows)
                .totalStudents(isActualInstructor ? rows.size() : 1)
                .classAverageScore(classAvg)
                .build();
    }

    private String calculateLetterGrade(double score) {
        if (score >= 95) return "A+";
        if (score >= 90) return "A0";
        if (score >= 85) return "B+";
        if (score >= 80) return "B0";
        if (score >= 75) return "C+";
        if (score >= 70) return "C0";
        if (score >= 60) return "D0";
        return "F";
    }
}
