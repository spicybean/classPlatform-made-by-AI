package com.uniclass.domain.material.controller;

import com.uniclass.domain.material.dto.CreateMaterialDto;
import com.uniclass.domain.material.service.MaterialService;
import com.uniclass.domain.material.service.MaterialService.DownloadResult;
import com.uniclass.domain.user.entity.Role;
import com.uniclass.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/classes/{classroomId}/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    /**
     * 강의 자료 업로드 (교수 전용)
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String uploadMaterial(@PathVariable("classroomId") Long classroomId,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 @Valid @ModelAttribute("materialForm") CreateMaterialDto form,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("materialErrorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/classes/" + classroomId;
        }

        try {
            materialService.uploadMaterial(classroomId, userDetails.getId(), form);
            redirectAttributes.addFlashAttribute("materialSuccessMessage", "강의 자료가 성공적으로 등록되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("materialErrorMessage", e.getMessage());
        }

        return "redirect:/classes/" + classroomId;
    }

    /**
     * 강의 자료 다운로드 (수강생 및 담당 교수)
     */
    @GetMapping("/{materialId}/download")
    public ResponseEntity<Resource> downloadMaterial(@PathVariable("classroomId") Long classroomId,
                                                     @PathVariable("materialId") Long materialId,
                                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isInstructor = userDetails.getRole() == Role.ROLE_INSTRUCTOR;
        DownloadResult downloadResult = materialService.downloadMaterial(materialId, userDetails.getId(), isInstructor);

        String encodedFilename = UriUtils.encode(downloadResult.originalFilename(), StandardCharsets.UTF_8);
        String contentDisposition = "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename;

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (downloadResult.contentType() != null && !downloadResult.contentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(downloadResult.contentType());
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(downloadResult.resource());
    }

    /**
     * 강의 자료 삭제 (교수 전용)
     */
    @PostMapping("/{materialId}/delete")
    @PreAuthorize("hasRole('ROLE_INSTRUCTOR')")
    public String deleteMaterial(@PathVariable("classroomId") Long classroomId,
                                 @PathVariable("materialId") Long materialId,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        try {
            materialService.deleteMaterial(materialId, userDetails.getId());
            redirectAttributes.addFlashAttribute("materialSuccessMessage", "강의 자료가 삭제되었습니다.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("materialErrorMessage", e.getMessage());
        }

        return "redirect:/classes/" + classroomId;
    }
}
