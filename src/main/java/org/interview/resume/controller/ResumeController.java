package org.interview.resume.controller;

import org.interview.common.result.Result;
import org.interview.resume.dto.ResumeDetailDTO;
import org.interview.resume.dto.ResumeListItemDTO;
import org.interview.resume.service.ResumeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Result<Map<String, Object>> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = resumeService.uploadAndAnalyze(file);
        return Result.success(result);
    }

    @GetMapping
    public Result<List<ResumeListItemDTO>> getAllResumes() {
        return Result.success(resumeService.getAllResumes());
    }

    @GetMapping("/{id}/detail")
    public Result<ResumeDetailDTO> getResumeDetail(@PathVariable Long id) {
        return Result.success(resumeService.getResumeDetail(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteResume(@PathVariable Long id) {
        resumeService.deleteResume(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/reanalyze")
    public Result<Void> reanalyze(@PathVariable Long id) {
        resumeService.reanalyze(id);
        return Result.success();
    }
}
