package com.uniclass.domain.classroom.controller;

import com.uniclass.domain.classroom.dto.CloneClassRoomDto;
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

import com.uniclass.domain.assignment.dto.AssignmentResponseDto;
import com.uniclass.domain.assignment.service.AssignmentService;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Controller
@RequestMapping("/classes")
public class ClassRoomController {

    private final ClassRoomService classRoomService;
    private final MaterialService materialService;
    private final AssignmentService assignmentService;
    private final com.uniclass.domain.attendance.service.AttendanceService attendanceService;
    private final com.uniclass.domain.grade.service.GradeService gradeService;
    private final String currentSemester;

    public ClassRoomController(ClassRoomService classRoomService,
                               MaterialService materialService,
                               AssignmentService assignmentService,
                               com.uniclass.domain.attendance.service.AttendanceService attendanceService,
                               com.uniclass.domain.grade.service.GradeService gradeService,
                               @Value("${uniclass.current-semester:2026-1학기}") String currentSemester) {
        this.classRoomService = classRoomService;
        this.materialService = materialService;
        this.assignmentService = assignmentService;
        this.attendanceService = attendanceService;
        this.gradeService = gradeService;
        this.currentSemester = currentSemester;
    }

    /**
     * 회원 전용 클래스룸 앱 (내 수업 전체 목록 및 필터링)
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String myClassrooms(@AuthenticationPrincipal CustomUserDetails userDetails,
                              @RequestParam(value = "filter", defaultValue = "all") String filter,
                              Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        List<com.uniclass.domain.classroom.dto.ClassRoomCardDto> allClasses;
        boolean isInstructor = userDetails.getRole() == Role.ROLE_INSTRUCTOR;
        if (isInstructor) {
            allClasses = classRoomService.getInstructorClassrooms(userDetails.getId());
        } else {
            allClasses = classRoomService.getStudentClassrooms(userDetails.getId());
        }

        List<com.uniclass.domain.classroom.dto.ClassRoomCardDto> filteredClasses;
        if ("active".equalsIgnoreCase(filter)) {
            filteredClasses = allClasses.stream().filter(c -> !c.isArchived()).toList();
        } else if ("archived".equalsIgnoreCase(filter)) {
            filteredClasses = allClasses.stream().filter(com.uniclass.domain.classroom.dto.ClassRoomCardDto::isArchived).toList();
        } else {
            filteredClasses = allClasses;
        }

        model.addAttribute("classes", filteredClasses);
        model.addAttribute("totalCount", allClasses.size());
        model.addAttribute("activeCount", allClasses.stream().filter(c -> !c.isArchived()).count());
        model.addAttribute("archivedCount", allClasses.stream().filter(com.uniclass.domain.classroom.dto.ClassRoomCardDto::isArchived).count());
        model.addAttribute("currentFilter", filter.toLowerCase());
        model.addAttribute("isInstructor", isInstructor);
        model.addAttribute("currentSemester", currentSemester);

        return "classroom/list";
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
    @PreAuthorize("isAuthenticated()")
    public String joinClassRoomPage(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        if (userDetails.getRole() == Role.ROLE_INSTRUCTOR) {
            return "redirect:/?error=instructor_cannot_join";
        }
        model.addAttribute("form", new JoinClassRoomDto());
        return "classroom/join";
    }

    @PostMapping("/join")
    @PreAuthorize("isAuthenticated()")
    public String joinClassRoom(@AuthenticationPrincipal CustomUserDetails userDetails,
                               @Valid @ModelAttribute("form") JoinClassRoomDto form,
                               BindingResult bindingResult,
                               Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
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
    @PreAuthorize("isAuthenticated()")
    public String classRoomDetail(@PathVariable("id") Long id,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  @RequestParam(value = "tab", defaultValue = "overview") String currentTab,
                                  @RequestParam(value = "created", required = false) String created,
                                  @RequestParam(value = "joined", required = false) String joined,
                                  Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        // 인가 검증: 담당 교수이거나 해당 수업의 수강생(ENROLLED)만 접근 허용
        if (!classRoomService.isUserEnrolledOrInstructor(id, userDetails.getId())) {
            return "redirect:/?error=not_enrolled";
        }

        ClassRoom classRoom = classRoomService.getClassRoomDetail(id);
        model.addAttribute("classroom", classRoom);

        boolean isInstructor = classRoom.getInstructor() != null && classRoom.getInstructor().getId().equals(userDetails.getId());
        model.addAttribute("isInstructor", isInstructor);
        model.addAttribute("currentTab", currentTab.toLowerCase());

        // 1. 자료실 데이터 (Materials)
        List<WeekSectionDto> weeks = materialService.getWeeksWithMaterials(id, userDetails.getId(), isInstructor);
        model.addAttribute("weeks", weeks);

        // 2. 과제 데이터 (Assignments)
        List<AssignmentResponseDto> assignments = assignmentService.getAssignmentsForClassroom(id, userDetails.getId(), isInstructor);
        model.addAttribute("assignments", assignments);

        // 3. 출석 데이터 (Attendance)
        var attendanceSessions = attendanceService.getSessionsForClassroom(id, userDetails.getId(), isInstructor);
        model.addAttribute("attendanceSessions", attendanceSessions);
        if (!isInstructor) {
            try {
                var studentAttendanceSummary = attendanceService.getStudentSummary(id, userDetails.getId());
                model.addAttribute("studentAttendanceSummary", studentAttendanceSummary);
            } catch (Exception ignored) {}
        }

        // 4. 성적부 데이터 (Grades)
        try {
            var gradeBook = gradeService.getGradeBook(id, userDetails.getId(), isInstructor);
            model.addAttribute("gradeBook", gradeBook);
        } catch (Exception e) {
            model.addAttribute("gradeBook", null);
        }

        // 5. 수강생 명단 (Members)
        var enrolledStudents = classRoomService.getEnrolledStudents(id);
        model.addAttribute("enrolledStudents", enrolledStudents);

        CreateMaterialDto materialForm = new CreateMaterialDto();
        model.addAttribute("materialForm", materialForm);

        if (isInstructor) {
            CloneClassRoomDto cloneForm = new CloneClassRoomDto();
            cloneForm.setSourceClassroomId(id);
            cloneForm.setNewName("[복제] " + classRoom.getName());
            cloneForm.setNewSemester(currentSemester);
            cloneForm.setNewDivision(classRoom.getDivision());
            cloneForm.setNewStartDate(java.time.LocalDate.now().plusMonths(1));
            cloneForm.setCloneMaterials(true);
            cloneForm.setCloneAssignments(true);
            model.addAttribute("cloneForm", cloneForm);
        }

        if (created != null) {
            model.addAttribute("alertSuccess", "수업이 성공적으로 개설되었습니다! 초대 코드를 학생들에게 공유하세요.");
        }
        if (joined != null) {
            model.addAttribute("alertSuccess", "수업에 성공적으로 참가했습니다!");
        }

        return "classroom/detail";
    }

    /**
     * 수업 원클릭 복제 및 날짜 자동 시프트 (교수 전용)
     */
    @PostMapping("/{id}/clone")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String cloneClassRoom(@PathVariable("id") Long id,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute("cloneForm") CloneClassRoomDto cloneForm,
                                 BindingResult bindingResult,
                                 org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("errorMessage", errorMsg);
            return "redirect:/classes/" + id + "?tab=settings";
        }

        try {
            cloneForm.setSourceClassroomId(id);
            Long newClassroomId = classRoomService.cloneClassRoom(userDetails.getId(), cloneForm);
            redirectAttributes.addFlashAttribute("alertSuccess", "새 학기 수업으로 성공적으로 복제되었습니다! 주차 일정 및 과제 기한이 자동 계산되었습니다.");
            return "redirect:/classes/" + newClassroomId + "?created=true";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/classes/" + id + "?tab=settings";
        }
    }

    /**
     * 수업 보관(Archive) 처리 (교수 전용)
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String archiveClassRoom(@PathVariable("id") Long id,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        try {
            classRoomService.archiveClassRoom(id, userDetails.getId());
            redirectAttributes.addFlashAttribute("alertSuccess", "수업이 보관(Archive) 처리되었습니다. 이제 읽기 전용으로 전환됩니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/classes/" + id + "?tab=settings";
    }

    /**
     * 수업 보관 해제(Unarchive) 처리 (교수 전용)
     */
    @PostMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String unarchiveClassRoom(@PathVariable("id") Long id,
                                     @AuthenticationPrincipal CustomUserDetails userDetails,
                                     org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        try {
            classRoomService.unarchiveClassRoom(id, userDetails.getId());
            redirectAttributes.addFlashAttribute("alertSuccess", "수업 보관이 해제되었습니다. 다시 정상 운영 상태로 전환되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/classes/" + id + "?tab=settings";
    }
}
