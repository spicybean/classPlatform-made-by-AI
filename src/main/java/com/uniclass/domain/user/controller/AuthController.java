package com.uniclass.domain.user.controller;

import com.uniclass.domain.user.dto.UserRegisterDto;
import com.uniclass.domain.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "registered", required = false) String registered,
                            Model model) {
        if (isAuthenticated()) {
            return "redirect:/";
        }
        if (error != null) {
            model.addAttribute("errorMessage", "이메일 또는 비밀번호가 일치하지 않습니다.");
        }
        if (registered != null) {
            model.addAttribute("successMessage", "회원가입이 완료되었습니다! 로그인해 주세요.");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (isAuthenticated()) {
            return "redirect:/";
        }
        model.addAttribute("form", new UserRegisterDto());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") UserRegisterDto form,
                           BindingResult bindingResult,
                           Model model) {
        if (isAuthenticated()) {
            return "redirect:/";
        }
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            userService.register(form);
            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException e) {
            bindingResult.reject("duplicate", e.getMessage());
            return "auth/register";
        }
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
}
