package com.uniclass.domain.attendance;

import com.uniclass.domain.attendance.dto.AttendanceRecordResponseDto;
import com.uniclass.domain.attendance.dto.AttendanceSessionResponseDto;
import com.uniclass.domain.attendance.dto.AttendanceStatusUpdateDto;
import com.uniclass.domain.attendance.dto.StartAttendanceDto;
import com.uniclass.domain.attendance.dto.StudentAttendanceSummaryDto;
import com.uniclass.domain.attendance.entity.AttendanceRecord;
import com.uniclass.domain.attendance.entity.AttendanceSession;
import com.uniclass.domain.attendance.entity.AttendanceStatus;
import com.uniclass.domain.attendance.repository.AttendanceRecordRepository;
import com.uniclass.domain.attendance.repository.AttendanceSessionRepository;
import com.uniclass.domain.attendance.service.AttendanceService;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AttendanceServiceTest {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceSessionRepository sessionRepository;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User instructor;
    private User student1;
    private User student2;
    private User outsider;
    private ClassRoom classRoom;

    @BeforeEach
    void setUp() {
        instructor = userRepository.save(User.builder()
                .email("prof_att@univ.ac.kr")
                .name("김교수")
                .studentNo("P_ATT_01")
                .password("password")
                .role(Role.ROLE_INSTRUCTOR)
                .build());

        student1 = userRepository.save(User.builder()
                .email("stu1_att@univ.ac.kr")
                .name("학생일")
                .studentNo("20263001")
                .password("password")
                .role(Role.ROLE_STUDENT)
                .build());

        student2 = userRepository.save(User.builder()
                .email("stu2_att@univ.ac.kr")
                .name("학생이")
                .studentNo("20263002")
                .password("password")
                .role(Role.ROLE_STUDENT)
                .build());

        outsider = userRepository.save(User.builder()
                .email("outsider_att@univ.ac.kr")
                .name("외부인")
                .studentNo("20269999")
                .password("password")
                .role(Role.ROLE_STUDENT)
                .build());

        classRoom = classRoomRepository.save(ClassRoom.builder()
                .instructor(instructor)
                .name("스마트 출석 테스트 수업")
                .courseCode("CS301")
                .division("01")
                .semester("2026-1학기")
                .inviteCode("ATT301")
                .build());

        // student1, student2 수강 등록
        enrollmentRepository.save(Enrollment.builder()
                .classroom(classRoom)
                .student(student1)
                .status(EnrollmentStatus.ENROLLED)
                .build());

        enrollmentRepository.save(Enrollment.builder()
                .classroom(classRoom)
                .student(student2)
                .status(EnrollmentStatus.ENROLLED)
                .build());

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("교수는 4자리 출석 세션을 시작할 수 있으며 4자리 난수와 3분 유효시간이 설정된다")
    void startSession_success() {
        StartAttendanceDto dto = new StartAttendanceDto();
        dto.setWeekNumber(1);
        dto.setRoundNumber(1);
        dto.setTimeoutMinutes(3);

        Long sessionId = attendanceService.startSession(classRoom.getId(), instructor.getId(), dto);

        assertThat(sessionId).isNotNull();
        AttendanceSession session = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getAuthCode()).matches("^[0-9]{4}$");
        assertThat(session.isActive()).isTrue();
        assertThat(session.getTimeoutMinutes()).isEqualTo(3);
        assertThat(session.getValidUntil()).isAfter(session.getStartedAt());
    }

    @Test
    @DisplayName("학생은 3분 내에 올바른 4자리 코드를 입력하면 PRESENT(출석) 처리된다")
    void submitAttendance_onTime_present() {
        StartAttendanceDto dto = new StartAttendanceDto();
        dto.setWeekNumber(1);
        dto.setTimeoutMinutes(3);
        Long sessionId = attendanceService.startSession(classRoom.getId(), instructor.getId(), dto);
        AttendanceSession session = sessionRepository.findById(sessionId).orElseThrow();

        AttendanceRecordResponseDto record = attendanceService.submitAttendance(
                classRoom.getId(), student1.getId(), session.getAuthCode(), "127.0.0.1"
        );

        assertThat(record.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(record.getStatusDescription()).isEqualTo("출석");
        assertThat(record.getStudentName()).isEqualTo("학생일");

        boolean exists = recordRepository.existsBySessionIdAndStudentId(sessionId, student1.getId());
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("틀린 4자리 코드를 입력하면 IllegalArgumentException이 발생한다")
    void submitAttendance_wrongCode_throwsException() {
        StartAttendanceDto dto = new StartAttendanceDto();
        Long sessionId = attendanceService.startSession(classRoom.getId(), instructor.getId(), dto);

        assertThatThrownBy(() -> attendanceService.submitAttendance(
                classRoom.getId(), student1.getId(), "0000", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("출석 인증번호(4자리)가 일치하지 않습니다");
    }

    @Test
    @DisplayName("수강생이 아닌 외부인이 출석 시도 시 예외가 발생한다")
    void submitAttendance_outsider_denied() {
        StartAttendanceDto dto = new StartAttendanceDto();
        Long sessionId = attendanceService.startSession(classRoom.getId(), instructor.getId(), dto);
        AttendanceSession session = sessionRepository.findById(sessionId).orElseThrow();

        assertThatThrownBy(() -> attendanceService.submitAttendance(
                classRoom.getId(), outsider.getId(), session.getAuthCode(), "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수강생만 출석할 수 있습니다");
    }

    @Test
    @DisplayName("이미 출석한 학생이 다시 코드를 입력하면 중복 출석 예외가 발생한다")
    void submitAttendance_duplicate_denied() {
        StartAttendanceDto dto = new StartAttendanceDto();
        Long sessionId = attendanceService.startSession(classRoom.getId(), instructor.getId(), dto);
        AttendanceSession session = sessionRepository.findById(sessionId).orElseThrow();

        // 1차 출석
        attendanceService.submitAttendance(classRoom.getId(), student1.getId(), session.getAuthCode(), "127.0.0.1");

        // 2차 중복 출석 시도
        assertThatThrownBy(() -> attendanceService.submitAttendance(
                classRoom.getId(), student1.getId(), session.getAuthCode(), "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 출석 처리가 완료되었습니다");
    }

    @Test
    @DisplayName("교수가 출석 세션을 마감하면 미출석 학생에게 ABSENT(결석) 레코드가 일괄 생성된다")
    void closeSession_marksUnattendedStudentsAbsent() {
        StartAttendanceDto dto = new StartAttendanceDto();
        Long sessionId = attendanceService.startSession(classRoom.getId(), instructor.getId(), dto);
        AttendanceSession session = sessionRepository.findById(sessionId).orElseThrow();

        // student1만 출석 완료, student2는 미출석
        attendanceService.submitAttendance(classRoom.getId(), student1.getId(), session.getAuthCode(), "127.0.0.1");

        // 교수 세션 마감
        attendanceService.closeSession(classRoom.getId(), sessionId, instructor.getId());

        entityManager.flush();
        entityManager.clear();

        // student1은 PRESENT
        AttendanceRecord rec1 = recordRepository.findBySessionIdAndStudentId(sessionId, student1.getId()).orElseThrow();
        assertThat(rec1.getStatus()).isEqualTo(AttendanceStatus.PRESENT);

        // student2는 ABSENT(결석) 자동 생성
        AttendanceRecord rec2 = recordRepository.findBySessionIdAndStudentId(sessionId, student2.getId()).orElseThrow();
        assertThat(rec2.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(rec2.getRemark()).contains("자동 결석 처리");
    }

    @Test
    @DisplayName("교수는 학생의 결석 기록을 EXCUSED(공결) 등으로 수동 정정할 수 있다")
    void updateRecordStatus_instructor_success() {
        StartAttendanceDto dto = new StartAttendanceDto();
        Long sessionId = attendanceService.startSession(classRoom.getId(), instructor.getId(), dto);
        AttendanceSession session = sessionRepository.findById(sessionId).orElseThrow();

        // student1 출석
        attendanceService.submitAttendance(classRoom.getId(), student1.getId(), session.getAuthCode(), "127.0.0.1");
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, student1.getId()).orElseThrow();

        AttendanceStatusUpdateDto updateDto = new AttendanceStatusUpdateDto();
        updateDto.setStatus(AttendanceStatus.EXCUSED);
        updateDto.setRemark("독감 진단서 제출로 공결 인정");

        attendanceService.updateRecordStatus(classRoom.getId(), sessionId, record.getId(), instructor.getId(), updateDto);

        entityManager.flush();
        entityManager.clear();

        AttendanceRecord updated = recordRepository.findById(record.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
        assertThat(updated.getRemark()).isEqualTo("독감 진단서 제출로 공결 인정");
    }

    @Test
    @DisplayName("학생 출석 요약 조회 시 총 출석 세션 수와 출석률이 올바르게 계산된다")
    void getStudentSummary_calculatesRate() {
        StartAttendanceDto dto = new StartAttendanceDto();
        Long sessionId = attendanceService.startSession(classRoom.getId(), instructor.getId(), dto);
        AttendanceSession session = sessionRepository.findById(sessionId).orElseThrow();

        attendanceService.submitAttendance(classRoom.getId(), student1.getId(), session.getAuthCode(), "127.0.0.1");

        StudentAttendanceSummaryDto summary = attendanceService.getStudentSummary(classRoom.getId(), student1.getId());

        assertThat(summary.getTotalSessions()).isEqualTo(1);
        assertThat(summary.getPresentCount()).isEqualTo(1);
        assertThat(summary.getAttendanceRate()).isEqualTo(100.0);
        assertThat(summary.isAlreadyAttendedActiveSession()).isTrue();
    }
}
