package com.studentdocs.controller;

import com.studentdocs.service.S3Service;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class DashboardController {

    private final S3Service s3Service;

    public DashboardController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        String username = principal.getName();
        model.addAttribute("username", username);
        model.addAttribute("documents", s3Service.listDocuments(username));
        return "dashboard";
    }

    @PostMapping("/upload")
    public String upload(Principal principal,
                          @RequestParam("document") MultipartFile file,
                          RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Choose a file first.");
            } else {
                s3Service.uploadDocument(principal.getName(), file);
                redirectAttributes.addFlashAttribute("success", "Uploaded " + file.getOriginalFilename() + ".");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/download")
    public String download(Principal principal, @RequestParam String key,
                            RedirectAttributes redirectAttributes) {
        try {
            String url = s3Service.presignedDownloadUrl(principal.getName(), key);
            return "redirect:" + url;
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have access to that file.");
            return "redirect:/dashboard";
        }
    }

    @PostMapping("/delete")
    public String delete(Principal principal, @RequestParam String key,
                          RedirectAttributes redirectAttributes) {
        try {
            s3Service.deleteDocument(principal.getName(), key);
            redirectAttributes.addFlashAttribute("success", "Document deleted.");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have access to that file.");
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/healthz")
    @ResponseBody
    public String healthz() {
        return "{\"status\":\"ok\"}";
    }
}
