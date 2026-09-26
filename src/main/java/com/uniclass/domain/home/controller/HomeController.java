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
        if (userDetails != null) {
            if (userDetails.getRole() == Role.ROLE_INSTRUCTOR) {
                myClasses = classRoomService.getInstructorClassrooms(userDetails.getId());
            } else {
                myClasses = classRoomService.getStudentClassrooms(userDetails.getId());
            }
        }
        model.addAttribute("classes", myClasses);

        return "index";
    }
}
