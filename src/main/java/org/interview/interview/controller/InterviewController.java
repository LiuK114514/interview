package org.interview.interview.controller;

import org.interview.common.result.Result;
import org.interview.interview.dto.*;
import org.interview.interview.service.InterviewService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/api/interview/start")
    public Result<StartInterviewResponse> startInterview(@RequestBody CreateInterviewRequest request) {
        StartInterviewResponse response = interviewService.startInterview(request);
        return Result.success(response);
    }

    @PostMapping("/api/interview/answer")
    public Result<SubmitAnswerResponse> submitAnswer(@RequestBody SubmitAnswerRequest request) {
        SubmitAnswerResponse response = interviewService.submitAnswer(request);
        return Result.success(response);
    }

    @PostMapping("/api/interview/next")
    public Result<NextQuestionResponse> nextQuestion(@RequestBody NextQuestionRequest request) {
        NextQuestionResponse response = interviewService.nextQuestion(request.sessionId());
        return Result.success(response);
    }

    @PostMapping("/api/interview/report")
    public Result<GenerateReportResponse> generateReport(@RequestBody GenerateReportRequest request) {
        GenerateReportResponse response = interviewService.generateReport(request.sessionId());
        return Result.success(response);
    }
}
