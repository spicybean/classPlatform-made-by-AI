package com.uniclass.domain.classroom;

import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.entity.Enrollment;
import com.uniclass.domain.classroom.entity.EnrollmentStatus;
import com.uniclass.domain.classroom.repository.ClassRoomRepository;
import com.uniclass.domain.classroom.repository.EnrollmentRepository;
import com.uniclass.domain.home.controller.HomeController;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.domain.user.entity.User;
import com.uniclass.domain.user.repository.UserRepository;
import com.uniclass.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ClassRoomControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassRoomRepository classRoomRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private User instructor;
    private User student;
    private ClassRoom activeClass;
    private ClassRoom archivedClass;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        instructor = userRepository.save(User.builder()
                .email("prof_hub@uniclass.ac.kr")
                .password("encoded_pass")
                .name("김교수")
                .studentNo("P202699")
                .role(Role.ROLE_INSTRUCTOR)
                .build());

        student = userRepository.save(User.builder()
                .email("student_hub@uniclass.ac.kr")
                .password("encoded_pass")
                .name("이학생")
                .studentNo("20261234")
                .role(Role.ROLE_STUDENT)
                .build());

        activeClass = classRoomRepository.save(ClassRoom.builder()
                .name("클라우드 컴퓨팅")
                .courseCode("CS301")
                .division("01분반")
                .semester("2026-1학기")
                .inviteCode("CLOUD1")
                .instructor(instructor)
                .build());

        archivedClass = ClassRoom.builder()
                .name("객체지향 설계")
                .courseCode("CS202")
                .division("02분반")
                .semester("2025-2학기")
                .inviteCode("OOP202")
                .instructor(instructor)
                .build();
        archivedClass.archive();
        archivedClass = classRoomRepository.save(archivedClass);

        enrollmentRepository.save(Enrollment.builder()
                .classroom(activeClass)
                .student(student)
                .status(EnrollmentStatus.ENROLLED)
                .build());

        enrollmentRepository.save(Enrollment.builder()
                .classroom(archivedClass)
                .student(student)
                .status(EnrollmentStatus.ENROLLED)
                .build());
    }

    @Test
    @DisplayName("[포털 홈] 비로그인 사용자: 200 OK 및 8개 학사 캘린더 이벤트 렌더링 확인")
    void anonymousPortalHomeShowsAcademicCalendar() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("classCount", 0))
                .andExpect(model().attribute("isInstructor", false))
                .andExpect(model().attributeExists("academicEvents"))
                .andExpect(result -> {
                    @SuppressWarnings("unchecked")
                    List<HomeController.AcademicEvent> events = (List<HomeController.AcademicEvent>) result.getModelAndView().getModel().get("academicEvents");
                    assertThat(events).hasSize(8);
                    assertThat(events.get(0).title()).contains("개강일");
                    assertThat(events.get(7).title()).contains("종강");
                });
    }

    @Test
    @DisplayName("[포털 홈] 로그인 교수: 내 수업 수(2개)와 학사 캘린더 모델 전달 확인")
    void instructorPortalHomeShowsClassCountAndEvents() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails(instructor);

        mockMvc.perform(get("/").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("classCount", 2))
                .andExpect(model().attribute("isInstructor", true))
                .andExpect(model().attributeExists("academicEvents"));
    }

    @Test
    @DisplayName("[클래스룸 앱] 비인증 접근 시 로그인 페이지로 리다이렉트 (302)")
    void anonymousAccessToClassesRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/classes"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("[클래스룸 앱] 교수 사용자 전체 수업 목록 및 필터 카운트 검증")
    void instructorClassesPageFilterCounts() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails(instructor);

        mockMvc.perform(get("/classes").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("classroom/list"))
                .andExpect(model().attribute("totalCount", 2))
                .andExpect(model().attribute("activeCount", 1L))
                .andExpect(model().attribute("archivedCount", 1L))
                .andExpect(model().attribute("currentFilter", "all"))
                .andExpect(model().attribute("isInstructor", true));
    }

    @Test
    @DisplayName("[클래스룸 앱] 학생 사용자 활성(active) 강좌 필터링 검증")
    void studentClassesPageActiveFilter() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails(student);

        mockMvc.perform(get("/classes").param("filter", "active").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("classroom/list"))
                .andExpect(model().attribute("currentFilter", "active"))
                .andExpect(model().attribute("totalCount", 2))
                .andExpect(model().attribute("activeCount", 1L))
                .andExpect(model().attribute("archivedCount", 1L));
    }

    @Test
    @DisplayName("[보안] 학생 계정이 수업 개설 페이지(/classes/new) 접근 시 403 Forbidden")
    void studentCannotAccessClassRoomCreate() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails(student);

        mockMvc.perform(get("/classes/new").with(user(userDetails)))
                .andExpect(status().isForbidden());
    }
}
