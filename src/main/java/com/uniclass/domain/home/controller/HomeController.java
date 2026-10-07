package com.uniclass.domain.home.controller;

import com.uniclass.domain.classroom.dto.ClassRoomCardDto;
import com.uniclass.domain.classroom.service.ClassRoomService;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.global.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;

@Controller
public class HomeController {

    private final String currentSemester;
    private final ClassRoomService classRoomService;

    public HomeController(@Value("${uniclass.current-semester:2026-1학기}") String currentSemester,
                          ClassRoomService classRoomService) {
        this.currentSemester = currentSemester;
        this.classRoomService = classRoomService;
    }

    public record AcademicEvent(String date, String title, String category, String badgeColor, String description) {}

    @GetMapping("/")
    public String index(@RequestParam(value = "logout", required = false) String logout,
                        @RequestParam(value = "error", required = false) String error,
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        Model model) {
        model.addAttribute("appName", "UniClass");
        model.addAttribute("currentSemester", currentSemester);

        if (logout != null) {
            model.addAttribute("logoutMessage", "성공적으로 로그아웃되었습니다.");
        }
        if ("forbidden".equals(error)) {
            model.addAttribute("errorMessage", "해당 작업을 수행할 권한이 없습니다.");
        }
        if ("not_enrolled".equals(error)) {
            model.addAttribute("errorMessage", "수강 신청되지 않은 수업입니다. 초대 코드로 먼저 수강신청을 완료해 주세요.");
        }
        if ("instructor_cannot_join".equals(error)) {
            model.addAttribute("errorMessage", "교수 계정은 수강신청을 할 수 없습니다.");
        }

        List<ClassRoomCardDto> myClasses = Collections.emptyList();
        boolean isInstructor = false;
        if (userDetails != null) {
            isInstructor = userDetails.getRole() == Role.ROLE_INSTRUCTOR;
            if (isInstructor) {
                myClasses = classRoomService.getInstructorClassrooms(userDetails.getId());
            } else {
                myClasses = classRoomService.getStudentClassrooms(userDetails.getId());
            }
        }
        model.addAttribute("classes", myClasses);
        model.addAttribute("classCount", myClasses.size());
        model.addAttribute("isInstructor", isInstructor);

        // 2026-1학기 주요 학사 캘린더 이벤트
        List<AcademicEvent> academicEvents = List.of(
                new AcademicEvent("03.02(월)", "2026-1학기 개강일", "학기 시작", "bg-blue-100 text-blue-800 border-blue-200", "정규 16주 학사 일정 개시"),
                new AcademicEvent("03.02(월) ~ 03.06(금)", "수강신청 확인 및 변경", "학사 안내", "bg-slate-100 text-slate-800 border-slate-200", "수강 정정 및 분반 이동"),
                new AcademicEvent("04.20(월) ~ 04.24(금)", "1학기 중간고사 주간 (8W)", "시험", "bg-purple-100 text-purple-800 border-purple-200", "중간고사 평가 및 출석 체크"),
                new AcademicEvent("05.05(화)", "어린이날 공휴일 (휴강)", "공휴일", "bg-amber-100 text-amber-800 border-amber-200", "법정 공휴일 지정 휴강 주간"),
                new AcademicEvent("05.18(월) ~ 05.20(수)", "봄 대동제 (캠퍼스 축제)", "행사", "bg-rose-100 text-rose-800 border-rose-200", "캠퍼스 문화 행사 및 축제"),
                new AcademicEvent("06.08(월) ~ 06.12(금)", "학기말 자율 보강 주간 (15W)", "보강", "bg-emerald-100 text-emerald-800 border-emerald-200", "결손 시수 보강 및 대체 강의"),
                new AcademicEvent("06.15(월) ~ 06.19(금)", "1학기 기말고사 주간 (16W)", "시험", "bg-purple-100 text-purple-800 border-purple-200", "기말평가 및 학기 성적 산출"),
                new AcademicEvent("06.19(금)", "2026-1학기 종강", "종강", "bg-indigo-100 text-indigo-800 border-indigo-200", "종강 및 여름방학 개시")
        );
        model.addAttribute("academicEvents", academicEvents);

        return "index";
    }
}
