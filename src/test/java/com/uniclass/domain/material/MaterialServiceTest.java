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
}
