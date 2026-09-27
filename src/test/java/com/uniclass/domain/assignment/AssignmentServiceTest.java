package com.uniclass.domain.assignment;

import com.uniclass.domain.assignment.dto.AssignmentResponseDto;
import com.uniclass.domain.assignment.dto.CreateAssignmentDto;
import com.uniclass.domain.assignment.dto.SubmitAssignmentDto;
import com.uniclass.domain.assignment.entity.Assignment;
import com.uniclass.domain.assignment.entity.Submission;
import com.uniclass.domain.assignment.entity.SubmissionStatus;
import com.uniclass.domain.assignment.repository.AssignmentRepository;
import com.uniclass.domain.assignment.repository.SubmissionRepository;
import com.uniclass.domain.assignment.service.AssignmentService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AssignmentServiceTest {

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private EntityManager entityManager;

    private User instructor;
    private User student;
    private User outsider;
    private ClassRoom classRoom;

    @BeforeEach
    void setUp() {
        instructor = userRepository.save(User.builder()
                .name("이교수")
                .email("prof_assign@univ.ac.kr")
                .password("password")
                .studentNo("PROF_A01")
                .role(Role.ROLE_INSTRUCTOR)
                .build());

        student = userRepository.save(User.builder()
                .name("김학생")
                .email("student_assign@univ.ac.kr")
                .password("password")
                .studentNo("20263333")
                .role(Role.ROLE_STUDENT)
                .build());

        outsider = userRepository.save(User.builder()
                .name("박외부")
                .email("outsider_assign@univ.ac.kr")
                .password("password")
                .studentNo("20264444")
                .role(Role.ROLE_STUDENT)
                .build());

        classRoom = classRoomRepository.save(ClassRoom.builder()
                .instructor(instructor)
                .name("알고리즘")
                .courseCode("CS202")
                .division("01")
                .semester("2026-1학기")
                .inviteCode("CS202A")
                .build());

        enrollmentRepository.save(Enrollment.builder()
                .classroom(classRoom)
                .student(student)
                .status(EnrollmentStatus.ENROLLED)
                .build());
    }

    @Test
    @DisplayName("교수는 마감일시와 배점을 지정하여 새 과제를 출제할 수 있다")
    void createAssignment_success() {
        CreateAssignmentDto dto = new CreateAssignmentDto();
        dto.setTitle("1차 과제 - 정렬 알고리즘");
        dto.setDescription("퀵 정렬과 병합 정렬을 구현하세요.");
        dto.setDueDate(LocalDateTime.now().plusDays(7).toString());
        dto.setMaxScore(100);
        dto.setAllowLate(true);
        dto.setWeekNumber(2);

        Long assignmentId = assignmentService.createAssignment(classRoom.getId(), instructor.getId(), dto);

        assertThat(assignmentId).isNotNull();
        Assignment saved = assignmentRepository.findById(assignmentId).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("1차 과제 - 정렬 알고리즘");
        assertThat(saved.getMaxScore()).isEqualTo(100);
        assertThat(saved.getWeekSection().getWeekNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("수강생은 마감 전에 과제 파일을 정상 제출할 수 있다")
    void submitAssignment_onTime_success() {
        CreateAssignmentDto createDto = new CreateAssignmentDto();
        createDto.setTitle("정상 제출 테스트 과제");
        createDto.setDueDate(LocalDateTime.now().plusDays(3).toString());
        createDto.setMaxScore(100);
        createDto.setAllowLate(true);

        Long assignmentId = assignmentService.createAssignment(classRoom.getId(), instructor.getId(), createDto);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "solution.pdf",
                "application/pdf",
                "Assignment Solution Content".getBytes(StandardCharsets.UTF_8)
        );

        SubmitAssignmentDto submitDto = new SubmitAssignmentDto();
        submitDto.setFile(file);
        submitDto.setNote("열심히 작성했습니다!");

        Long submissionId = assignmentService.submitAssignment(classRoom.getId(), assignmentId, student.getId(), submitDto);

        assertThat(submissionId).isNotNull();

        entityManager.flush();
        entityManager.clear();

        Submission saved = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(saved.getOriginalFilename()).isEqualTo("solution.pdf");
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(saved.getNote()).isEqualTo("열심히 작성했습니다!");
    }

    @Test
    @DisplayName("수강생은 과제 마감 전 새 파일로 재제출할 수 있다")
    void submitAssignment_resubmit_replacesExistingFile() {
        CreateAssignmentDto createDto = new CreateAssignmentDto();
        createDto.setTitle("재제출 테스트 과제");
        createDto.setDueDate(LocalDateTime.now().plusDays(3).toString());
        Long assignmentId = assignmentService.createAssignment(classRoom.getId(), instructor.getId(), createDto);

        // 1차 제출
        MockMultipartFile firstFile = new MockMultipartFile(
                "file", "draft.pdf", "application/pdf", "Draft Content".getBytes(StandardCharsets.UTF_8)
        );
        SubmitAssignmentDto firstDto = new SubmitAssignmentDto();
        firstDto.setFile(firstFile);
        Long firstSubId = assignmentService.submitAssignment(classRoom.getId(), assignmentId, student.getId(), firstDto);

        // 2차 재제출
        MockMultipartFile secondFile = new MockMultipartFile(
                "file", "final_v2.pdf", "application/pdf", "Final Content".getBytes(StandardCharsets.UTF_8)
        );
        SubmitAssignmentDto secondDto = new SubmitAssignmentDto();
        secondDto.setFile(secondFile);
        secondDto.setNote("수정본입니다.");
        Long secondSubId = assignmentService.submitAssignment(classRoom.getId(), assignmentId, student.getId(), secondDto);

        assertThat(secondSubId).isEqualTo(firstSubId);

        entityManager.flush();
        entityManager.clear();

        Submission updated = submissionRepository.findById(firstSubId).orElseThrow();
        assertThat(updated.getOriginalFilename()).isEqualTo("final_v2.pdf");
        assertThat(updated.getNote()).isEqualTo("수정본입니다.");
    }

    @Test
    @DisplayName("마감 기한이 지난 후 지각 제출이 허용된 경우 LATE_SUBMITTED 상태로 제출된다")
    void submitAssignment_lateSubmission_markedAsLate() {
        // 이미 마감된 과제 생성
        Assignment expiredAssignment = assignmentRepository.save(Assignment.builder()
                .classroom(classRoom)
                .title("지각 허용 과제")
                .dueDate(LocalDateTime.now().minusHours(1))
                .maxScore(100)
                .allowLate(true)
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "late_report.pdf", "application/pdf", "Late Content".getBytes(StandardCharsets.UTF_8)
        );
        SubmitAssignmentDto dto = new SubmitAssignmentDto();
        dto.setFile(file);

        Long submissionId = assignmentService.submitAssignment(classRoom.getId(), expiredAssignment.getId(), student.getId(), dto);

        entityManager.flush();
        entityManager.clear();

        Submission saved = submissionRepository.findById(submissionId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.LATE_SUBMITTED);
    }

    @Test
    @DisplayName("마감 기한이 지난 후 지각 제출이 허용되지 않은 과제는 제출이 거부된다")
    void submitAssignment_lateSubmission_rejectedWhenDisallowed() {
        Assignment expiredAssignment = assignmentRepository.save(Assignment.builder()
                .classroom(classRoom)
                .title("지각 불가 과제")
                .dueDate(LocalDateTime.now().minusHours(1))
                .maxScore(100)
                .allowLate(false)
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "Content".getBytes(StandardCharsets.UTF_8)
        );
        SubmitAssignmentDto dto = new SubmitAssignmentDto();
        dto.setFile(file);

        assertThatThrownBy(() -> assignmentService.submitAssignment(classRoom.getId(), expiredAssignment.getId(), student.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지각 제출이 허용되지 않습니다");
    }

    @Test
    @DisplayName("수강생이 아닌 외부 학생은 과제를 제출할 수 없다")
    void submitAssignment_byOutsider_denied() {
        CreateAssignmentDto createDto = new CreateAssignmentDto();
        createDto.setTitle("보안 테스트 과제");
        createDto.setDueDate(LocalDateTime.now().plusDays(2).toString());
        Long assignmentId = assignmentService.createAssignment(classRoom.getId(), instructor.getId(), createDto);

        MockMultipartFile file = new MockMultipartFile(
                "file", "hacker.pdf", "application/pdf", "Content".getBytes(StandardCharsets.UTF_8)
        );
        SubmitAssignmentDto dto = new SubmitAssignmentDto();
        dto.setFile(file);

        assertThatThrownBy(() -> assignmentService.submitAssignment(classRoom.getId(), assignmentId, outsider.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수강생만");
    }

    @Test
    @DisplayName("교수는 제출물을 채점하고 피드백을 남길 수 있다")
    void gradeSubmission_success() {
        CreateAssignmentDto createDto = new CreateAssignmentDto();
        createDto.setTitle("채점 대상 과제");
        createDto.setDueDate(LocalDateTime.now().plusDays(2).toString());
        createDto.setMaxScore(100);
        Long assignmentId = assignmentService.createAssignment(classRoom.getId(), instructor.getId(), createDto);

        MockMultipartFile file = new MockMultipartFile(
                "file", "solution.pdf", "application/pdf", "Content".getBytes(StandardCharsets.UTF_8)
        );
        SubmitAssignmentDto submitDto = new SubmitAssignmentDto();
        submitDto.setFile(file);
        Long subId = assignmentService.submitAssignment(classRoom.getId(), assignmentId, student.getId(), submitDto);

        // 채점
        assignmentService.gradeSubmission(classRoom.getId(), assignmentId, subId, instructor.getId(), 95, "훌륭한 풀이입니다!");

        entityManager.flush();
        entityManager.clear();

        Submission graded = submissionRepository.findById(subId).orElseThrow();
        assertThat(graded.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(graded.getScore()).isEqualTo(95);
        assertThat(graded.getFeedback()).isEqualTo("훌륭한 풀이입니다!");
    }
}
