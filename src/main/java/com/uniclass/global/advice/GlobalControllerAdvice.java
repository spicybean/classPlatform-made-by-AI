package com.uniclass.global.advice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final String currentSemester;

    public GlobalControllerAdvice(@Value("${uniclass.current-semester:2026-1학기}") String currentSemester) {
        this.currentSemester = currentSemester;
    }

    /**
     * 모든 Thymeleaf 템플릿에서 ${currentSemester}를 일관되게 사용할 수 있도록 전역 공급
     */
    @ModelAttribute("currentSemester")
    public String currentSemester() {
        return this.currentSemester;
    }
}
