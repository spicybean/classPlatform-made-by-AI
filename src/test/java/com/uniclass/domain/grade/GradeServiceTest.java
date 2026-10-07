package com.uniclass.domain.grade;

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
import com.uniclass.domain.grade.service.GradeService;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock
    private ClassRoomRepository classRoomRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AttendanceSessionRepository sessionRepository;

    @Mock
    private AttendanceRecordRepository recordRepository;

    @InjectMocks
    private GradeService gradeService;

    private User instructor;
    private User student1;
    private ClassRoom classRoom;
    private Assignment assignment1;

    @BeforeEach
    void setUp() {
        instructor = User.builder()
                .name("김교수")
                .email("prof@univ.ac.kr")
                .role(Role.ROLE_INSTRUCTOR)
                .studentNo("P001")
                .password("pass")
                .build();

        student1 = User.builder()
                .name("이학생")
                .email("student1@univ.ac.kr")
                .role(Role.ROLE_STUDENT)
                .studentNo("20261111")
                .password("pass")
                .build();

        classRoom = ClassRoom.builder()
                .name("소프트웨어공학")
                .courseCode("SE101")
                .semester("2026-1학기")
                .division("01")
                .inviteCode("SE101A")
                .instructor(instructor)
                .build();

        assignment1 = Assignment.builder()
                .classroom(classRoom)
                .title("과제1")
                .maxScore(100)
                .dueDate(LocalDateTime.now().plusDays(3))
                .allowLate(true)
                .build();

        ReflectionTestUtils.setField(instructor, "id", 1L);
        ReflectionTestUtils.setField(student1, "id", 101L);
        ReflectionTestUtils.setField(assignment1, "id", 201L);
    }

    @Test
    @DisplayName("교수는 수업의 전체 성적부를 정상적으로 조회할 수 있다")
    void getGradeBook_instructor_success() {
        // given
        given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
        given(assignmentRepository.findByClassroomIdOrderByDueDateAsc(1L)).willReturn(List.of(assignment1));

        Enrollment enrollment = Enrollment.builder()
                .classroom(classRoom)
                .student(student1)
                .status(EnrollmentStatus.ENROLLED)
                .build();
        given(enrollmentRepository.findByClassroomIdAndStatusWithStudent(1L, EnrollmentStatus.ENROLLED))
                .willReturn(List.of(enrollment));

        AttendanceSession session = AttendanceSession.builder()
                .classroom(classRoom)
                .weekNumber(1)
                .roundNumber(1)
                .authCode("1234")
                .timeoutMinutes(3)
                .startedAt(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusMinutes(3))
                .lateUntil(LocalDateTime.now().plusMinutes(5))
                .build();
        given(sessionRepository.findByClassroomIdOrderByStartedAtDesc(1L)).willReturn(List.of(session));

        Submission submission = Submission.builder()
                .assignment(assignment1)
                .student(student1)
                .filePath("sub.pdf")
                .originalFilename("sub.pdf")
                .fileSize(100L)
                .status(SubmissionStatus.GRADED)
                .build();
        submission.grade(90, "우수함");
        given(submissionRepository.findByAssignmentIdAndStudentId(assignment1.getId(), student1.getId()))
                .willReturn(Optional.of(submission));

        AttendanceRecord record = AttendanceRecord.builder()
                .session(session)
                .student(student1)
                .status(AttendanceStatus.PRESENT)
                .attendedAt(LocalDateTime.now())
                .build();
        given(recordRepository.findByClassroomIdAndStudentId(1L, student1.getId()))
                .willReturn(List.of(record));

        // when
        GradeBookDto gradeBook = gradeService.getGradeBook(1L, instructor.getId(), true);

        // then
        assertThat(gradeBook).isNotNull();
        assertThat(gradeBook.getClassroomName()).isEqualTo("소프트웨어공학");
        assertThat(gradeBook.getStudentRows()).hasSize(1);

        GradeBookDto.StudentGradeRowDto row = gradeBook.getStudentRows().get(0);
        assertThat(row.getStudentName()).isEqualTo("이학생");
        assertThat(row.getTotalEarnedScore()).isEqualTo(90);
        assertThat(row.getAttendanceRate()).isEqualTo(100.0);
        assertThat(row.getTotalCompositeScore()).isEqualTo(92.0); // 90 * 0.8 + 100 * 0.2 = 72 + 20 = 92
        assertThat(row.getLetterGrade()).isEqualTo("A0");
        assertThat(gradeBook.getClassAverageScore()).isEqualTo(92.0);
    }

    @Test
    @DisplayName("학생이 성적부를 조회할 때 본인 성적만 반환되고 학급 평균 성적은 은닉된다(null)")
    void getGradeBook_student_onlyOwnGrade_and_noClassAverage() {
        // given
        User student2 = User.builder()
                .name("박학생")
                .email("student2@univ.ac.kr")
                .role(Role.ROLE_STUDENT)
                .studentNo("20262222")
                .password("pass")
                .build();
        ReflectionTestUtils.setField(student2, "id", 102L);

        given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
        given(enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(student1.getId(), 1L, EnrollmentStatus.ENROLLED))
                .willReturn(true);
        given(assignmentRepository.findByClassroomIdOrderByDueDateAsc(1L)).willReturn(List.of(assignment1));

        Enrollment enrollment1 = Enrollment.builder().classroom(classRoom).student(student1).status(EnrollmentStatus.ENROLLED).build();
        Enrollment enrollment2 = Enrollment.builder().classroom(classRoom).student(student2).status(EnrollmentStatus.ENROLLED).build();
        given(enrollmentRepository.findByClassroomIdAndStatusWithStudent(1L, EnrollmentStatus.ENROLLED))
                .willReturn(List.of(enrollment1, enrollment2));

        given(sessionRepository.findByClassroomIdOrderByStartedAtDesc(1L)).willReturn(List.of());
        given(submissionRepository.findByAssignmentIdAndStudentId(assignment1.getId(), student1.getId()))
                .willReturn(Optional.empty());
        given(recordRepository.findByClassroomIdAndStudentId(1L, student1.getId()))
                .willReturn(List.of());

        // when
        GradeBookDto gradeBook = gradeService.getGradeBook(1L, student1.getId(), false);

        // then
        assertThat(gradeBook).isNotNull();
        // 학생에게는 학급 평균 성적이 제공되지 않음 (null 검증)
        assertThat(gradeBook.getClassAverageScore()).isNull();
        // 타 학생(student2)의 성적은 일체 포함되지 않고 오직 본인 1명만 조회됨
        assertThat(gradeBook.getStudentRows()).hasSize(1);
        assertThat(gradeBook.getStudentRows().get(0).getStudentName()).isEqualTo("이학생");
        assertThat(gradeBook.getStudentRows().get(0).getStudentId()).isEqualTo(student1.getId());
    }

    @Test
    @DisplayName("비수강생이 성적부를 조회하려 하면 예외가 발생한다")
    void getGradeBook_outsider_denied() {
        // given
        given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
        given(enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(999L, 1L, EnrollmentStatus.ENROLLED))
                .willReturn(false);

        // when & then
        assertThrows(IllegalArgumentException.class, () ->
                gradeService.getGradeBook(1L, 999L, false));
    }

    @Test
    @DisplayName("학생이 isInstructor=true로 위장 시도해도 실제 담당 교수가 아니면 학급 평균이 은닉된다")
    void getGradeBook_impersonateInstructor_deniedToStudentLevel() {
        // given
        given(classRoomRepository.findById(1L)).willReturn(Optional.of(classRoom));
        given(enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(student1.getId(), 1L, EnrollmentStatus.ENROLLED))
                .willReturn(true);
        given(assignmentRepository.findByClassroomIdOrderByDueDateAsc(1L)).willReturn(List.of(assignment1));

        Enrollment enrollment1 = Enrollment.builder().classroom(classRoom).student(student1).status(EnrollmentStatus.ENROLLED).build();
        given(enrollmentRepository.findByClassroomIdAndStatusWithStudent(1L, EnrollmentStatus.ENROLLED))
                .willReturn(List.of(enrollment1));
        given(sessionRepository.findByClassroomIdOrderByStartedAtDesc(1L)).willReturn(List.of());
        given(submissionRepository.findByAssignmentIdAndStudentId(assignment1.getId(), student1.getId()))
                .willReturn(Optional.empty());
        given(recordRepository.findByClassroomIdAndStudentId(1L, student1.getId()))
                .willReturn(List.of());

        // when - 학생 ID로 isInstructor=true 위장 전달
        GradeBookDto gradeBook = gradeService.getGradeBook(1L, student1.getId(), true);

        // then - 서비스 내부에서 실제 담당 교수가 아님을 간파하고 학급 평균 은닉(null)
        assertThat(gradeBook).isNotNull();
        assertThat(gradeBook.getClassAverageScore()).isNull();
        assertThat(gradeBook.getTotalStudents()).isEqualTo(1);
    }

    @Test
    @DisplayName("currentUserId가 null이면 예외가 발생한다")
    void getGradeBook_nullUserId_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                gradeService.getGradeBook(1L, null, false));
    }
}
