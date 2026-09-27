package com.studentdocs.controller;

import com.studentdocs.model.User;
import com.studentdocs.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                            @RequestParam String password,
                            Model model) {
        String normalized = username.trim().toLowerCase();

        if (normalized.isEmpty() || password.isEmpty()) {
            model.addAttribute("error", "Username and password are required.");
            return "register";
        }
        if (userRepository.existsByUsername(normalized)) {
            model.addAttribute("error", "That username is already taken.");
            return "register";
        }

        userRepository.save(new User(normalized, passwordEncoder.encode(password)));
        return "redirect:/login?registered";
    }
}
