package com.uniclass.domain.material;

import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.material.dto.CreateMaterialDto;
import com.uniclass.domain.material.dto.WeekSectionDto;
import com.uniclass.domain.material.entity.Material;
import com.uniclass.domain.material.repository.MaterialRepository;
import com.uniclass.domain.material.service.MaterialService;
import com.uniclass.domain.material.service.MaterialService.DownloadResult;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MaterialServiceTest {

    @Autowired
    private MaterialService materialService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private com.uniclass.domain.material.repository.WeekSectionRepository weekSectionRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private User instructor;
    private User student;
    private User outsider;
    private ClassRoom classRoom;

    @BeforeEach
    void setUp() {
        instructor = userRepository.save(User.builder()
                .name("김교수")
                .email("prof@univ.ac.kr")
                .password("password")
                .studentNo("PROF001")
                .role(Role.ROLE_INSTRUCTOR)
                .build());

        student = userRepository.save(User.builder()
                .name("이학생")
                .email("student@univ.ac.kr")
                .password("password")
                .studentNo("20261111")
                .role(Role.ROLE_STUDENT)
                .build());

        outsider = userRepository.save(User.builder()
                .name("박타과")
                .email("outsider@univ.ac.kr")
                .password("password")
                .studentNo("20262222")
                .role(Role.ROLE_STUDENT)
                .build());

        classRoom = classRoomRepository.save(ClassRoom.builder()
                .instructor(instructor)
                .name("자료구조")
                .courseCode("CS101")
                .division("01")
                .semester("2026-1학기")
                .inviteCode("CS101A")
                .build());

        enrollmentRepository.save(Enrollment.builder()
                .classroom(classRoom)
                .student(student)
                .status(EnrollmentStatus.ENROLLED)
                .build());
    }

    @Test
    @DisplayName("수업 개설 후 주차별 섹션 조회 시 1~16주차가 자동 생성된다")
    void getWeeksWithMaterials_autoInitializes16Weeks() {
        List<WeekSectionDto> weeks = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);

        assertThat(weeks).hasSize(16);
        assertThat(weeks.get(0).getWeekNumber()).isEqualTo(1);
        assertThat(weeks.get(0).getTitle()).isEqualTo("1주차");
        assertThat(weeks.get(15).getWeekNumber()).isEqualTo(16);
    }

    @Test
    @DisplayName("교수는 강의 자료를 1주차에 성공적으로 업로드할 수 있다")
    void uploadMaterial_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture01_slides.pdf",
                "application/pdf",
                "PDF Slide Content".getBytes(StandardCharsets.UTF_8)
        );

        CreateMaterialDto dto = new CreateMaterialDto();
        dto.setWeekNumber(1);
        dto.setTitle("1주차 강의 슬라이드");
        dto.setDescription("오리엔테이션 슬라이드입니다.");
        dto.setFile(file);
        dto.setPublished(true);

        Long materialId = materialService.uploadMaterial(classRoom.getId(), instructor.getId(), dto);

        assertThat(materialId).isNotNull();

        Material saved = materialRepository.findById(materialId).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("1주차 강의 슬라이드");
        assertThat(saved.getOriginalFilename()).isEqualTo("lecture01_slides.pdf");
        assertThat(saved.getFileSize()).isGreaterThan(0);
        assertThat(saved.getDownloadCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("수강 중인 학생은 자료를 다운로드할 수 있고 다운로드 횟수가 증가한다")
    void downloadMaterial_student_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "Sample PDF Content".getBytes(StandardCharsets.UTF_8)
        );

        CreateMaterialDto dto = new CreateMaterialDto();
        dto.setWeekNumber(1);
        dto.setTitle("자료 1");
        dto.setFile(file);
        dto.setPublished(true);

        Long materialId = materialService.uploadMaterial(classRoom.getId(), instructor.getId(), dto);

        // 학생이 다운로드
        DownloadResult result = materialService.downloadMaterial(classRoom.getId(), materialId, student.getId(), false);

        assertThat(result.originalFilename()).isEqualTo("sample.pdf");
        assertThat(result.resource().exists()).isTrue();

        // 1차 캐시를 초기화하여 실제 DB UPDATE 반영 여부를 엄밀 검증
        entityManager.flush();
        entityManager.clear();

        Material updated = materialRepository.findById(materialId).orElseThrow();
        assertThat(updated.getDownloadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 수업 ID로 다운로드를 시도하면 거부된다 (URL 변조 방어)")
    void downloadMaterial_wrongClassroomId_denied() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "Sample PDF Content".getBytes(StandardCharsets.UTF_8)
        );

        CreateMaterialDto dto = new CreateMaterialDto();
        dto.setWeekNumber(1);
        dto.setTitle("자료 1");
        dto.setFile(file);
        dto.setPublished(true);

        Long materialId = materialService.uploadMaterial(classRoom.getId(), instructor.getId(), dto);

        Long wrongClassroomId = 9999L;
        assertThatThrownBy(() -> materialService.downloadMaterial(wrongClassroomId, materialId, student.getId(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 수업에 속한 강의 자료가 아닙니다");
    }

    @Test
    @DisplayName("수강생이 아닌 외부 사용자는 자료 다운로드가 거부된다")
    void downloadMaterial_outsider_denied() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "confidential.pdf",
                "application/pdf",
                "Content".getBytes(StandardCharsets.UTF_8)
        );

        CreateMaterialDto dto = new CreateMaterialDto();
        dto.setWeekNumber(1);
        dto.setTitle("기말고사 대비");
        dto.setFile(file);
        dto.setPublished(true);

        Long materialId = materialService.uploadMaterial(classRoom.getId(), instructor.getId(), dto);

        assertThatThrownBy(() -> materialService.downloadMaterial(classRoom.getId(), materialId, outsider.getId(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수강생만");
    }

    @Test
    @DisplayName("허용되지 않은 확장자(.exe) 업로드 시 거부된다")
    void uploadMaterial_dangerousExtension_blocked() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malware.exe",
                "application/octet-stream",
                "evil byte".getBytes(StandardCharsets.UTF_8)
        );

        CreateMaterialDto dto = new CreateMaterialDto();
        dto.setWeekNumber(1);
        dto.setTitle("프로그램");
        dto.setFile(file);

        assertThatThrownBy(() -> materialService.uploadMaterial(classRoom.getId(), instructor.getId(), dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 파일 형식");
    }

    @Test
    @DisplayName("수강생이 아닌 외부 사용자가 주차별 자료 목록 조회 시 예외가 발생한다")
    void getWeeksWithMaterials_outsider_denied() {
        assertThatThrownBy(() -> materialService.getWeeksWithMaterials(classRoom.getId(), outsider.getId(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("접근 권한이 없습니다");
    }

    @Test
    @DisplayName("교수는 주차 정보를 휴강(HOLIDAY)으로 변경하고 날짜와 공지사항을 설정할 수 있다")
    void updateWeekSection_holiday_success() {
        List<WeekSectionDto> weeks = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);
        Long week5Id = weeks.get(4).getId();

        com.uniclass.domain.material.dto.UpdateWeekSectionDto dto = com.uniclass.domain.material.dto.UpdateWeekSectionDto.builder()
                .title("5주차 (추석 연휴)")
                .description("추석 명절로 인한 법정 공휴일")
                .sectionType(com.uniclass.domain.material.entity.SectionType.HOLIDAY)
                .startDate(java.time.LocalDate.of(2026, 10, 5))
                .endDate(java.time.LocalDate.of(2026, 10, 9))
                .noticeText("추석 연휴로 이번 주는 휴강입니다. 가족과 풍성한 한가위 보내세요.")
                .isHidden(false)
                .build();

        materialService.updateWeekSection(classRoom.getId(), week5Id, dto, instructor.getId());

        List<WeekSectionDto> updatedWeeks = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);
        WeekSectionDto week5 = updatedWeeks.get(4);
        assertThat(week5.getTitle()).isEqualTo("5주차 (추석 연휴)");
        assertThat(week5.isHoliday()).isTrue();
        assertThat(week5.getFormattedDateRange()).isEqualTo("10.05 ~ 10.09");
        assertThat(week5.getNoticeText()).contains("추석 연휴로 이번 주는 휴강입니다");
    }

    @Test
    @DisplayName("교수는 원클릭으로 주차를 휴강으로 전환하고 다시 일반 강의로 복구할 수 있다")
    void toggleHoliday_toggleBackAndForth() {
        List<WeekSectionDto> weeks = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);
        Long week3Id = weeks.get(2).getId();

        // 1. 일반 강의 -> 휴강 전환
        materialService.toggleHoliday(classRoom.getId(), week3Id, "개교기념일 휴강", instructor.getId());
        WeekSectionDto week3Holiday = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true).get(2);
        assertThat(week3Holiday.isHoliday()).isTrue();
        assertThat(week3Holiday.getNoticeText()).isEqualTo("개교기념일 휴강");

        // 2. 휴강 -> 일반 강의 복구
        materialService.toggleHoliday(classRoom.getId(), week3Id, null, instructor.getId());
        WeekSectionDto week3Lecture = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true).get(2);
        assertThat(week3Lecture.isHoliday()).isFalse();
        assertThat(week3Lecture.getSectionType()).isEqualTo(com.uniclass.domain.material.entity.SectionType.LECTURE);
    }

    @Test
    @DisplayName("교수는 16주 이후에 보강 주차(17주차)를 새롭게 추가할 수 있다")
    void addCustomWeekSection_makeup_success() {
        materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true); // 1~16주차 초기화

        Long newSectionId = materialService.addCustomWeekSection(
                classRoom.getId(),
                "17주차 (보강 강의)",
                com.uniclass.domain.material.entity.SectionType.MAKEUP,
                instructor.getId()
        );

        List<WeekSectionDto> allWeeks = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);
        assertThat(allWeeks).hasSize(17);

        WeekSectionDto lastWeek = allWeeks.get(16);
        assertThat(lastWeek.getId()).isEqualTo(newSectionId);
        assertThat(lastWeek.getWeekNumber()).isEqualTo(17);
        assertThat(lastWeek.getTitle()).isEqualTo("17주차 (보강 강의)");
        assertThat(lastWeek.isMakeup()).isTrue();
    }

    @Test
    @DisplayName("자료가 없는 빈 주차는 교수가 삭제할 수 있다")
    void deleteWeekSection_empty_success() {
        List<WeekSectionDto> weeks = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);
        Long week16Id = weeks.get(15).getId();

        materialService.deleteWeekSection(classRoom.getId(), week16Id, instructor.getId());

        List<WeekSectionDto> remainingWeeks = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);
        assertThat(remainingWeeks).hasSize(15);
    }

    @Test
    @DisplayName("숨김(isHidden) 처리된 주차는 교수는 볼 수 있지만 학생에게는 노출되지 않는다")
    void hiddenSection_filterForStudent() {
        List<WeekSectionDto> weeks = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);
        Long week2Id = weeks.get(1).getId();

        com.uniclass.domain.material.dto.UpdateWeekSectionDto dto = com.uniclass.domain.material.dto.UpdateWeekSectionDto.builder()
                .title("2주차 (준비중)")
                .sectionType(com.uniclass.domain.material.entity.SectionType.LECTURE)
                .isHidden(true)
                .build();
        materialService.updateWeekSection(classRoom.getId(), week2Id, dto, instructor.getId());

        // 교수 시점: 16개 주차 전체 조회됨 (숨김 여부 포함)
        List<WeekSectionDto> profView = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);
        assertThat(profView).hasSize(16);
        assertThat(profView.get(1).isHidden()).isTrue();

        // 학생 시점: 2주차가 필터링되어 15개 주차만 조회됨
        List<WeekSectionDto> studentView = materialService.getWeeksWithMaterials(classRoom.getId(), student.getId(), false);
        assertThat(studentView).hasSize(15);
        assertThat(studentView.stream().noneMatch(w -> w.getId().equals(week2Id))).isTrue();
    }

    @Test
    @DisplayName("시작일이 종료일보다 이후인 경우 주차 설정 변경 시 예외가 발생한다")
    void updateWeekSection_invalidDateRange_throwsException() {
        List<WeekSectionDto> weeks = materialService.getWeeksWithMaterials(classRoom.getId(), instructor.getId(), true);
        Long week1Id = weeks.get(0).getId();

        com.uniclass.domain.material.dto.UpdateWeekSectionDto dto = com.uniclass.domain.material.dto.UpdateWeekSectionDto.builder()
                .title("1주차")
                .startDate(java.time.LocalDate.of(2026, 9, 10))
                .endDate(java.time.LocalDate.of(2026, 9, 1)) // 종료일이 시작일보다 앞섬
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> materialService.updateWeekSection(classRoom.getId(), week1Id, dto, instructor.getId())
        );
    }
}

