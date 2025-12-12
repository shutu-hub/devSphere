package com.shutu.interview.controller;

import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.tools.utils.Result;
import com.shutu.interview.entity.Interview;
import com.shutu.interview.entity.Question;
import com.shutu.interview.model.request.InterviewCreateRequest;
import com.shutu.interview.service.InterviewFlowService;
import com.shutu.interview.service.InterviewService;
import com.shutu.interview.service.InterviewStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/interviews")
public class InterviewController {

    @Autowired
    private InterviewService interviewsService;

    @Autowired
    private InterviewStateService interviewStateService;

    @Autowired
    private InterviewFlowService interviewFlowService;

    @GetMapping
    public Result<List<Interview>> list() {
        return new Result<List<Interview>>().ok(interviewsService.list());
    }

    @GetMapping("/{id}")
    public Result<Interview> getById(@PathVariable Long id) {
        return new Result<Interview>().ok(interviewsService.getById(id));
    }

    @PostMapping
    public Result<Interview> save(@RequestBody InterviewCreateRequest request) {
        Interview interview = new Interview();
        interview.setUserId(SecurityUser.getUserId());
        interview.setJobId(request.getJobId());
        interview.setResumeUrl(request.getResumeUrl());
        interview.setCategory(request.getCategory());
        interview.setStatus(0);
        interviewsService.save(interview);
        return new Result<Interview>().ok(interview);
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody Interview interview) {
        return new Result<Boolean>().ok(interviewsService.updateById(interview));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return new Result<Boolean>().ok(interviewsService.removeById(id));
    }

    // --- Business Logic Endpoints ---

    @PostMapping("/{id}/start")
    public Result<Question> start(@PathVariable Long id) {
        return new Result<Question>().ok(interviewFlowService.startInterview(id));
    }

    @PostMapping("/{id}/submit")
    public Result<Question> submit(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String answer = payload.get("answer");
        return new Result<Question>().ok(interviewFlowService.submitAnswer(id, answer));
    }

    @PostMapping("/{id}/complete")
    public Result<Interview> complete(@PathVariable Long id) {
        return new Result<Interview>().ok(interviewStateService.complete(id));
    }

    @PostMapping("/{id}/abort")
    public Result<Interview> abort(@PathVariable Long id) {
        return new Result<Interview>().ok(interviewStateService.abort(id));
    }
}
