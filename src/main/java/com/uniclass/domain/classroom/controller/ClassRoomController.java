package com.uniclass.domain.classroom.controller;

import com.uniclass.domain.classroom.dto.CreateClassRoomDto;
import com.uniclass.domain.classroom.dto.JoinClassRoomDto;
import com.uniclass.domain.classroom.entity.ClassRoom;
import com.uniclass.domain.classroom.service.ClassRoomService;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.uniclass.domain.material.dto.CreateMaterialDto;
import com.uniclass.domain.material.dto.WeekSectionDto;
import com.uniclass.domain.material.service.MaterialService;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;

@Controller
@RequestMapping("/classes")
public class ClassRoomController {

    private final ClassRoomService classRoomService;
    private final MaterialService materialService;
    private final String currentSemester;

    public ClassRoomController(ClassRoomService classRoomService,
                               MaterialService materialService,
                               @Value("${uniclass.current-semester:2026-1학기}") String currentSemester) {
        this.classRoomService = classRoomService;
        this.materialService = materialService;
        this.currentSemester = currentSemester;
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String newClassRoomPage(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        CreateClassRoomDto form = new CreateClassRoomDto();
        form.setSemester(currentSemester);
        model.addAttribute("form", form);
        return "classroom/create";
    }

    @PostMapping("/new")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String createClassRoom(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute("form") CreateClassRoomDto form,
                                 BindingResult bindingResult,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            return "classroom/create";
        }

        try {
            Long classId = classRoomService.createClassRoom(userDetails.getId(), form);
            return "redirect:/classes/" + classId + "?created=true";
        } catch (Exception e) {
            bindingResult.reject("createError", e.getMessage());
            return "classroom/create";
        }
    }

    @GetMapping("/join")
    public String joinClassRoomPage(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails.getRole() == Role.ROLE_INSTRUCTOR) {
            return "redirect:/?error=instructor_cannot_join";
        }
        model.addAttribute("form", new JoinClassRoomDto());
        return "classroom/join";
    }

    @PostMapping("/join")
    public String joinClassRoom(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @Valid @ModelAttribute("form") JoinClassRoomDto form,
                               BindingResult bindingResult,
                               Model model) {
        if (userDetails.getRole() == Role.ROLE_INSTRUCTOR) {
            return "redirect:/?error=instructor_cannot_join";
        }

        if (bindingResult.hasErrors()) {
            return "classroom/join";
        }

        try {
            Long classId = classRoomService.joinClassRoom(userDetails.getId(), form.getInviteCode());
            return "redirect:/classes/" + classId + "?joined=true";
        } catch (IllegalArgumentException e) {
            bindingResult.reject("joinError", e.getMessage());
            return "classroom/join";
        }
    }

    @GetMapping("/{id}")
    public String classRoomDetail(@PathVariable("id") Long id,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  @RequestParam(value = "created", required = false) String created,
                                  @RequestParam(value = "joined", required = false) String joined,
                                  Model model) {
        // 인가 검증: 담당 교수이거나 해당 수업의 수강생(ENROLLED)만 접근 허용
        if (!classRoomService.isUserEnrolledOrInstructor(id, userDetails.getId())) {
            return "redirect:/?error=not_enrolled";
        }

        ClassRoom classRoom = classRoomService.getClassRoomDetail(id);
        model.addAttribute("classroom", classRoom);

        boolean isInstructor = classRoom.getInstructor().getId().equals(userDetails.getId());
        model.addAttribute("isInstructor", isInstructor);

        List<WeekSectionDto> weeks = materialService.getWeeksWithMaterials(id, userDetails.getId(), isInstructor);
        model.addAttribute("weeks", weeks);

        CreateMaterialDto materialForm = new CreateMaterialDto();
        model.addAttribute("materialForm", materialForm);

        if (created != null) {
            model.addAttribute("alertSuccess", "수업이 성공적으로 개설되었습니다! 초대 코드를 학생들에게 공유하세요.");
        }
        if (joined != null) {
            model.addAttribute("alertSuccess", "수업에 성공적으로 참가했습니다!");
        }

        return "classroom/detail";
    }
}
