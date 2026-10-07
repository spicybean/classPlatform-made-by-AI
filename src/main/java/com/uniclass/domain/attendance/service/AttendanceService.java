package com.uniclass.domain.attendance.service;

import com.uniclass.domain.attendance.dto.*;
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
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    /**
     * 실시간 4자리 출석 체크 세션 시작 (교수 전용)
     */
    @Transactional
    public Long startSession(Long classroomId, Long instructorId, StartAttendanceDto dto) {
        ClassRoom classRoom = classRoomRepository.findById(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("수업 정보를 찾을 수 없습니다: " + classroomId));

        if (!classRoom.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("수업 담당 교수만 출석 체크를 시작할 수 있습니다.");
        }

        classRoom.assertEditable();

        // 기존에 열려 있는 활성 세션이 있다면 자동 종료
        sessionRepository.findFirstByClassroomIdAndActiveTrueOrderByStartedAtDesc(classroomId)
                .ifPresent(this::closeSessionInternal);

        // 4자리 랜덤 숫자 생성 (1000 ~ 9999)
        String authCode = String.format("%04d", 1000 + RANDOM.nextInt(9000));
        LocalDateTime now = LocalDateTime.now();

        AttendanceSession session = AttendanceSession.builder()
                .classroom(classRoom)
                .weekNumber(dto.getWeekNumber())
                .roundNumber(dto.getRoundNumber())
                .title(dto.getWeekNumber() + "주차 " + dto.getRoundNumber() + "차시 출석")
                .authCode(authCode)
                .startedAt(now)
                .timeoutMinutes(dto.getTimeoutMinutes())
                .validUntil(now.plusMinutes(dto.getTimeoutMinutes()))
                .lateUntil(now.plusMinutes(dto.getTimeoutMinutes() + 7)) // 지각은 마감 후 7분간 추가 인정
                .build();

        return sessionRepository.save(session).getId();
    }

    /**
     * 출석 세션 수동 종료 및 미출석자 결석(ABSENT) 자동 일괄 처리 (교수 전용)
     */
    @Transactional
    public void closeSession(Long classroomId, Long sessionId, Long instructorId) {
        AttendanceSession session = sessionRepository.findByIdWithClassroomAndInstructor(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("출석 세션을 찾을 수 없습니다: " + sessionId));

        if (!session.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업의 출석 세션이 아닙니다.");
        }

        if (!session.getClassroom().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("담당 교수만 출석을 종료할 수 있습니다.");
        }

        closeSessionInternal(session);
    }

    private void closeSessionInternal(AttendanceSession session) {
        if (!session.isActive()) {
            return;
        }
        session.close();

        // 수강생 중 해당 세션 레코드가 없는 학생들을 찾아 결석(ABSENT) 일괄 등록
        List<Enrollment> enrollments = enrollmentRepository.findByClassroomIdAndStatusWithStudent(
                session.getClassroom().getId(), EnrollmentStatus.ENROLLED);

        List<AttendanceRecord> records = recordRepository.findBySessionIdWithStudent(session.getId());
        Set<Long> attendedStudentIds = records.stream()
                .map(r -> r.getStudent().getId())
                .collect(Collectors.toSet());

        List<AttendanceRecord> absentRecords = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Enrollment enrollment : enrollments) {
            User student = enrollment.getStudent();
            if (!attendedStudentIds.contains(student.getId())) {
                absentRecords.add(AttendanceRecord.builder()
                        .session(session)
                        .student(student)
                        .status(AttendanceStatus.ABSENT)
                        .attendedAt(now)
                        .remark("세션 종료 시 미입력으로 자동 결석 처리")
                        .build());
            }
        }

        if (!absentRecords.isEmpty()) {
            recordRepository.saveAll(absentRecords);
        }
    }

    /**
     * 학생 4자리 출석 인증번호 입력 처리 (학생 전용)
     */
    @Transactional
    public AttendanceRecordResponseDto submitAttendance(Long classroomId, Long studentId, String authCode, String clientIp) {
        boolean isEnrolled = enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(studentId, classroomId, EnrollmentStatus.ENROLLED);
        if (!isEnrolled) {
            throw new IllegalArgumentException("해당 수업의 수강생만 출석할 수 있습니다.");
        }

        AttendanceSession session = sessionRepository.findFirstByClassroomIdAndActiveTrueOrderByStartedAtDesc(classroomId)
                .orElseThrow(() -> new IllegalArgumentException("현재 진행 중인 출석 체크가 없습니다."));

        session.getClassroom().assertEditable();

        LocalDateTime now = LocalDateTime.now();
        if (session.isExpired(now)) {
            closeSessionInternal(session);
            throw new IllegalArgumentException("출석 가능 시간이 초과되어 마감되었습니다.");
        }

        String inputCode = authCode != null ? authCode.trim() : "";
        if (!session.getAuthCode().equals(inputCode)) {
            throw new IllegalArgumentException("출석 인증번호(4자리)가 일치하지 않습니다. 화면의 번호를 다시 확인해 주세요.");
        }

        if (recordRepository.existsBySessionIdAndStudentId(session.getId(), studentId)) {
            throw new IllegalArgumentException("이미 출석 처리가 완료되었습니다.");
        }

        AttendanceStatus status = session.evaluateStatus(now);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("학생 정보를 찾을 수 없습니다."));

        AttendanceRecord record = AttendanceRecord.builder()
                .session(session)
                .student(student)
                .status(status)
                .attendedAt(now)
                .clientIp(clientIp)
                .build();

        AttendanceRecord saved = recordRepository.save(record);
        return AttendanceRecordResponseDto.from(saved);
    }

    /**
     * 특정 출석 세션 상세 정보 및 학생별 출결 현황 조회 (교수/학생 공통)
     */
    public AttendanceSessionResponseDto getSessionDetail(Long classroomId, Long sessionId, Long currentUserId, boolean isInstructor) {
        AttendanceSession session = sessionRepository.findByIdWithClassroomAndInstructor(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("출석 세션을 찾을 수 없습니다: " + sessionId));

        if (!session.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업의 출석 세션이 아닙니다.");
        }

        // 인가 검증
        if (!isInstructor && !enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(currentUserId, classroomId, EnrollmentStatus.ENROLLED)) {
            throw new IllegalArgumentException("수업 접근 권한이 없습니다.");
        }

        long totalStudents = enrollmentRepository.countByClassroomIdAndStatus(classroomId, EnrollmentStatus.ENROLLED);
        List<AttendanceRecord> records = recordRepository.findBySessionIdWithStudent(sessionId);

        long presentCount = 0;
        long lateCount = 0;
        long absentCount = 0;
        long excusedCount = 0;

        for (AttendanceRecord r : records) {
            switch (r.getStatus()) {
                case PRESENT -> presentCount++;
                case LATE -> lateCount++;
                case ABSENT -> absentCount++;
                case EXCUSED -> excusedCount++;
            }
        }

        // 세션이 아직 진행 중이고 미출석 학생이 있다면, 결석 수에는 아직 제출 안 한 인원까지 포함하여 계산
        long submittedCount = presentCount + lateCount + absentCount + excusedCount;
        if (session.isActive()) {
            absentCount = Math.max(0, totalStudents - (presentCount + lateCount + excusedCount));
        }

        List<AttendanceRecordResponseDto> recordDtos = records.stream()
                .map(AttendanceRecordResponseDto::from)
                .collect(Collectors.toList());

        return AttendanceSessionResponseDto.from(
                session, totalStudents, presentCount, lateCount, absentCount, excusedCount, recordDtos, LocalDateTime.now()
        );
    }

    /**
     * 수업의 전체 출석 세션 목록 조회
     */
    public List<AttendanceSessionResponseDto> getSessionsForClassroom(Long classroomId, Long currentUserId, boolean isInstructor) {
        if (!isInstructor && !enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(currentUserId, classroomId, EnrollmentStatus.ENROLLED)) {
            throw new IllegalArgumentException("수업 접근 권한이 없습니다.");
        }

        List<AttendanceSession> sessions = sessionRepository.findByClassroomIdOrderByStartedAtDesc(classroomId);
        long totalStudents = enrollmentRepository.countByClassroomIdAndStatus(classroomId, EnrollmentStatus.ENROLLED);
        LocalDateTime now = LocalDateTime.now();

        return sessions.stream().map(s -> {
            long presentCount = recordRepository.countBySessionIdAndStatus(s.getId(), AttendanceStatus.PRESENT);
            long lateCount = recordRepository.countBySessionIdAndStatus(s.getId(), AttendanceStatus.LATE);
            long absentCount = recordRepository.countBySessionIdAndStatus(s.getId(), AttendanceStatus.ABSENT);
            long excusedCount = recordRepository.countBySessionIdAndStatus(s.getId(), AttendanceStatus.EXCUSED);

            return AttendanceSessionResponseDto.from(
                    s, totalStudents, presentCount, lateCount, absentCount, excusedCount, Collections.emptyList(), now
            );
        }).collect(Collectors.toList());
    }

    /**
     * 학생 개인 출석 요약 및 현재 활성 세션 상태 조회
     */
    public StudentAttendanceSummaryDto getStudentSummary(Long classroomId, Long studentId) {
        boolean isEnrolled = enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(studentId, classroomId, EnrollmentStatus.ENROLLED);
        if (!isEnrolled) {
            throw new IllegalArgumentException("해당 수업의 수강생이 아닙니다.");
        }

        List<AttendanceRecord> myRecords = recordRepository.findByClassroomIdAndStudentId(classroomId, studentId);
        List<AttendanceSession> allSessions = sessionRepository.findByClassroomIdOrderByStartedAtDesc(classroomId);

        long presentCount = 0;
        long lateCount = 0;
        long absentCount = 0;
        long excusedCount = 0;

        for (AttendanceRecord r : myRecords) {
            switch (r.getStatus()) {
                case PRESENT -> presentCount++;
                case LATE -> lateCount++;
                case ABSENT -> absentCount++;
                case EXCUSED -> excusedCount++;
            }
        }

        long totalSessions = allSessions.size();
        // 출석 점수 계산: 출석(1.0) + 지각(0.5) / 총 세션
        double rate = 100.0;
        if (totalSessions > 0) {
            double earned = presentCount + (lateCount * 0.5) + excusedCount;
            rate = Math.round((earned / totalSessions) * 1000.0) / 10.0;
        }

        // 현재 활성 세션 조회
        Optional<AttendanceSession> activeSessionOpt = sessionRepository.findFirstByClassroomIdAndActiveTrueOrderByStartedAtDesc(classroomId);
        AttendanceSessionResponseDto activeDto = null;
        boolean alreadyAttended = false;

        if (activeSessionOpt.isPresent()) {
            AttendanceSession activeSession = activeSessionOpt.get();
            if (activeSession.isExpired(LocalDateTime.now())) {
                closeSessionInternal(activeSession);
            } else {
                alreadyAttended = recordRepository.existsBySessionIdAndStudentId(activeSession.getId(), studentId);
                activeDto = AttendanceSessionResponseDto.from(
                        activeSession, 0, 0, 0, 0, 0, Collections.emptyList(), LocalDateTime.now()
                );
            }
        }

        List<AttendanceRecordResponseDto> recordDtos = myRecords.stream()
                .map(AttendanceRecordResponseDto::from)
                .collect(Collectors.toList());

        return StudentAttendanceSummaryDto.builder()
                .totalSessions(totalSessions)
                .presentCount(presentCount)
                .lateCount(lateCount)
                .absentCount(absentCount)
                .excusedCount(excusedCount)
                .attendanceRate(rate)
                .myRecords(recordDtos)
                .activeSession(activeDto)
                .alreadyAttendedActiveSession(alreadyAttended)
                .build();
    }

    /**
     * 교수의 학생 출결 상태 수동 정정 (공결 처리 등)
     */
    @Transactional
    public void updateRecordStatus(Long classroomId, Long sessionId, Long recordId, Long instructorId, AttendanceStatusUpdateDto dto) {
        AttendanceSession session = sessionRepository.findByIdWithClassroomAndInstructor(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("출석 세션을 찾을 수 없습니다: " + sessionId));

        if (!session.getClassroom().getId().equals(classroomId)) {
            throw new IllegalArgumentException("해당 수업의 출석 세션이 아닙니다.");
        }

        if (!session.getClassroom().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("담당 교수만 출결 상태를 정정할 수 있습니다.");
        }

        AttendanceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("출결 기록을 찾을 수 없습니다: " + recordId));

        if (!record.getSession().getId().equals(sessionId)) {
            throw new IllegalArgumentException("해당 세션의 출결 기록이 아닙니다.");
        }

        record.updateStatus(dto.getStatus(), dto.getRemark());
    }
}
