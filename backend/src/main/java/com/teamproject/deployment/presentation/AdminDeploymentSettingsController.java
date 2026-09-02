package com.teamproject.deployment.presentation;

import com.teamproject.deployment.application.DeploymentSettingsService;
import com.teamproject.deployment.application.dto.DeploymentSettingsDtos.JobView;
import com.teamproject.deployment.application.dto.DeploymentSettingsDtos.SettingsView;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/deployment-settings")
public class AdminDeploymentSettingsController {

    private final DeploymentSettingsService service;

    public AdminDeploymentSettingsController(DeploymentSettingsService service) {
        this.service = service;
    }

    @GetMapping
    SettingsView current() {
        return service.currentView();
    }

    @PostMapping("/drafts")
    JobView createDraft(Authentication auth,
            @RequestParam String publicUrl,
            @RequestParam MultipartFile certificate,
            @RequestParam MultipartFile privateKey) {
        return service.createDraft(publicUrl, certificate, privateKey, (Long) auth.getPrincipal());
    }

    @PostMapping("/{jobId}/test")
    JobView test(@PathVariable Long jobId) {
        return service.test(jobId);
    }

    @PostMapping("/{jobId}/apply")
    JobView apply(@PathVariable Long jobId) {
        return service.apply(jobId);
    }

    @GetMapping("/jobs/{jobId}")
    JobView job(@PathVariable Long jobId) {
        return service.jobView(jobId);
    }
}
