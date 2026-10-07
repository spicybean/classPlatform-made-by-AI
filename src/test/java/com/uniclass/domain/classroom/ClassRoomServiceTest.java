package com.uniclass.domain.classroom;

import com.uniclass.domain.assignment.entity.Assignment;
import com.uniclass.domain.assignment.repository.AssignmentRepository;
import com.uniclass.domain.classroom.dto.CloneClassRoomDto;
import com.uniclass.domain.classroom.dto.CreateClassRoomDto;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.classroom.service.ClassRoomService;
import com.uniclass.domain.material.entity.Material;
import com.uniclass.domain.material.entity.SectionType;
import com.uniclass.domain.material.entity.WeekSection;
import com.uniclass.domain.material.repository.MaterialRepository;
import com.uniclass.domain.material.repository.WeekSectionRepository;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ClassRoomServiceTest {

    @Autowired
    private ClassRoomService classRoomService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private WeekSectionRepository weekSectionRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    private User instructor;
    private User otherInstructor;
    private User student;
    private ClassRoom sourceClassRoom;
    private LocalDate originalStart;

    @BeforeEach
    void setUp() {
        instructor = userRepository.save(User.builder()
                .studentNo("P2026001")
                .name("홍교수")
                .email("hong@uniclass.ac.kr")
                .password("encoded_pwd")
                .role(Role.ROLE_INSTRUCTOR)
                .build());

        otherInstructor = userRepository.save(User.builder()
                .studentNo("P2026002")
                .name("김교수")
                .email("kim@uniclass.ac.kr")
                .password("encoded_pwd")
                .role(Role.ROLE_INSTRUCTOR)
                .build());

        student = userRepository.save(User.builder()
                .studentNo("20261001")
                .name("이학생")
                .email("lee@uniclass.ac.kr")
                .password("encoded_pwd")
                .role(Role.ROLE_STUDENT)
                .build());

        originalStart = LocalDate.of(2026, 3, 2);

        sourceClassRoom = classRoomRepository.save(ClassRoom.builder()
                .name("자바 프로그래밍")
                .courseCode("CS101")
                .semester("2026-1학기")
                .division("01분반")
                .description("자바 기초 강의입니다.")
                .inviteCode("ORIG01")
                .instructor(instructor)
                .build());

        // 1~3주차 생성 (날짜 및 특수 섹션 지정)
        WeekSection sec1 = weekSectionRepository.save(WeekSection.builder()
                .classroom(sourceClassRoom)
                .weekNumber(1)
                .title("1주차: 자바 오리엔테이션")
                .description("강의 계획 및 개발환경 구성")
                .sectionType(SectionType.LECTURE)
                .startDate(originalStart)
                .endDate(originalStart.plusDays(6))
                .build());

        WeekSection sec2 = weekSectionRepository.save(WeekSection.builder()
                .classroom(sourceClassRoom)
                .weekNumber(2)
                .title("2주차: 기본 문법")
                .description("변수와 제어문")
                .sectionType(SectionType.LECTURE)
                .startDate(originalStart.plusWeeks(1))
                .endDate(originalStart.plusWeeks(1).plusDays(6))
                .build());

        WeekSection sec3 = weekSectionRepository.save(WeekSection.builder()
                .classroom(sourceClassRoom)
                .weekNumber(3)
                .title("3주차: 공휴일 휴강")
                .description("개교기념일")
                .sectionType(SectionType.HOLIDAY)
                .noticeText("개교기념일로 공식 휴강합니다.")
                .startDate(originalStart.plusWeeks(2))
                .endDate(originalStart.plusWeeks(2).plusDays(6))
                .build());

        // 자료 1개 등록
        materialRepository.save(Material.builder()
                .classroom(sourceClassRoom)
                .weekSection(sec1)
                .title("1주차 강의 슬라이드")
                .description("OT 슬라이드 PDF")
                .originalFilename("intro.pdf")
                .storedFilename("uuid_intro.pdf")
                .filePath("/uploads/materials/uuid_intro.pdf")
                .fileSize(1024L)
                .contentType("application/pdf")
                .published(true)
                .build());

        // 과제 1개 등록 (2주차 일요일 23:59:59 마감)
        LocalDateTime originalDueDate = originalStart.plusWeeks(1).plusDays(6).atTime(23, 59, 59);
        assignmentRepository.save(Assignment.builder()
                .classroom(sourceClassRoom)
                .weekSection(sec2)
                .title("2주차 제어문 실습 과제")
                .description("별찍기 과제를 제출하세요.")
                .dueDate(originalDueDate)
                .maxScore(100)
                .allowLate(true)
                .build());

        // 이전 학기 학생 수강 등록
        enrollmentRepository.save(Enrollment.builder()
                .classroom(sourceClassRoom)
                .student(student)
                .status(EnrollmentStatus.ENROLLED)
                .build());
    }

    @Test
    @DisplayName("성공: 새 학기 수업 원클릭 복제 시 날짜 자동 시프트 및 주차/자료/과제 복제 검증")
    void cloneClassRoom_Success_WithDateShift() {
        // Given: 2학기 개강일로 6개월 뒤(2026-09-01) 설정
        LocalDate newStart = LocalDate.of(2026, 9, 1);
        long deltaDays = ChronoUnit.DAYS.between(originalStart, newStart);

        CloneClassRoomDto dto = new CloneClassRoomDto(
                sourceClassRoom.getId(),
                "자바 프로그래밍 (2학기)",
                "2026-2학기",
                "01분반",
                newStart,
                true,
                true
        );

        // When
        Long newClassroomId = classRoomService.cloneClassRoom(instructor.getId(), dto);

        // Then: 신규 수업 생성 확인
        ClassRoom cloned = classRoomRepository.findById(newClassroomId).orElseThrow();
        assertThat(cloned.getName()).isEqualTo("자바 프로그래밍 (2학기)");
        assertThat(cloned.getSemester()).isEqualTo("2026-2학기");
        assertThat(cloned.getCourseCode()).isEqualTo("CS101");
        assertThat(cloned.getInviteCode()).isNotEqualTo(sourceClassRoom.getInviteCode());
        assertThat(cloned.isArchived()).isFalse();

        // Then: 주차(WeekSection) 날짜 시프트 및 메타데이터 복제 확인
        List<WeekSection> clonedSections = weekSectionRepository.findByClassroomIdOrderByWeekNumberAsc(newClassroomId);
        assertThat(clonedSections).hasSize(3);

        WeekSection clonedSec1 = clonedSections.get(0);
        assertThat(clonedSec1.getWeekNumber()).isEqualTo(1);
        assertThat(clonedSec1.getStartDate()).isEqualTo(newStart); // 정확한 날짜 시프트 검증
        assertThat(clonedSec1.getEndDate()).isEqualTo(newStart.plusDays(6));
        assertThat(clonedSec1.getSectionType()).isEqualTo(SectionType.LECTURE);

        WeekSection clonedSec3 = clonedSections.get(2);
        assertThat(clonedSec3.getSectionType()).isEqualTo(SectionType.HOLIDAY); // 특수 섹션 유지 검증
        assertThat(clonedSec3.getNoticeText()).isEqualTo("개교기념일로 공식 휴강합니다.");
        assertThat(clonedSec3.getStartDate()).isEqualTo(originalStart.plusWeeks(2).plusDays(deltaDays));

        // Then: 강의자료 복제 확인
        List<Material> clonedMaterials = materialRepository.findByClassroomIdOrderByIdAsc(newClassroomId);
        assertThat(clonedMaterials).hasSize(1);
        assertThat(clonedMaterials.get(0).getTitle()).isEqualTo("1주차 강의 슬라이드");
        assertThat(clonedMaterials.get(0).getWeekSection().getId()).isEqualTo(clonedSec1.getId());

        // Then: 과제 마감일 시프트 복제 확인
        List<Assignment> clonedAssignments = assignmentRepository.findByClassroomIdOrderByDueDateAsc(newClassroomId);
        assertThat(clonedAssignments).hasSize(1);
        Assignment clonedAssign = clonedAssignments.get(0);
        assertThat(clonedAssign.getTitle()).isEqualTo("2주차 제어문 실습 과제");
        assertThat(clonedAssign.getDueDate()).isEqualTo(originalStart.plusWeeks(1).plusDays(6).atTime(23, 59, 59).plusDays(deltaDays));

        // Then [보안 & 데이터 독립성]: 이전 학기 학생 수강생은 복제되지 않아야 함
        boolean studentEnrolled = enrollmentRepository.existsByStudentIdAndClassroomIdAndStatus(
                student.getId(), newClassroomId, EnrollmentStatus.ENROLLED);
        assertThat(studentEnrolled).isFalse();
    }

    @Test
    @DisplayName("성공: 자료 및 과제 복제 옵션을 끄면 주차 뼈대만 복제된다")
    void cloneClassRoom_WithoutMaterialsAndAssignments() {
        // Given
        LocalDate newStart = LocalDate.of(2026, 9, 1);
        CloneClassRoomDto dto = new CloneClassRoomDto(
                sourceClassRoom.getId(),
                "자바 프로그래밍 (뼈대만)",
                "2026-2학기",
                "02분반",
                newStart,
                false, // 자료 제외
                false  // 과제 제외
        );

        // When
        Long newClassroomId = classRoomService.cloneClassRoom(instructor.getId(), dto);

        // Then
        List<WeekSection> clonedSections = weekSectionRepository.findByClassroomIdOrderByWeekNumberAsc(newClassroomId);
        assertThat(clonedSections).hasSize(3);

        List<Material> clonedMaterials = materialRepository.findByClassroomIdOrderByIdAsc(newClassroomId);
        assertThat(clonedMaterials).isEmpty();

        List<Assignment> clonedAssignments = assignmentRepository.findByClassroomIdOrderByDueDateAsc(newClassroomId);
        assertThat(clonedAssignments).isEmpty();
    }

    @Test
    @DisplayName("실패(Negative): 담당 교수가 아닌 다른 사용자가 수업 복제를 시도하면 예외 발생")
    void cloneClassRoom_Unauthorized_ThrowsException() {
        // Given: 다른 교수
        LocalDate newStart = LocalDate.of(2026, 9, 1);
        CloneClassRoomDto dto = new CloneClassRoomDto(
                sourceClassRoom.getId(),
                "불법 복제 시도",
                "2026-2학기",
                "01분반",
                newStart,
                true,
                true
        );

        // When & Then
        assertThatThrownBy(() -> classRoomService.cloneClassRoom(otherInstructor.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("담당 교수 권한이 필요합니다");
    }

    @Test
    @DisplayName("실패(Negative): 원본 수업 ID가 누락되면 예외 발생")
    void cloneClassRoom_MissingSourceId_ThrowsException() {
        CloneClassRoomDto dto = new CloneClassRoomDto(
                null,
                "수업명",
                "2026-2학기",
                "01분반",
                LocalDate.now(),
                true,
                true
        );

        assertThatThrownBy(() -> classRoomService.cloneClassRoom(instructor.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("복제할 원본 수업 ID가 필요합니다");
    }
}
