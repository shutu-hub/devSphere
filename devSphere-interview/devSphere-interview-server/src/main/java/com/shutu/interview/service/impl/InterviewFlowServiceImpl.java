package com.shutu.interview.service.impl;

import com.shutu.interview.entity.Interview;
import com.shutu.interview.entity.InterviewLog;
import com.shutu.interview.entity.Question;
import com.shutu.interview.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InterviewFlowServiceImpl implements InterviewFlowService {

    private final InterviewStateService interviewStateService;
    private final QuestionGenerationService questionGenerationService;
    private final InterviewLogService interviewLogService;
    private final InterviewService interviewService;
    private final QuestionService questionService;

    public InterviewFlowServiceImpl(InterviewStateService interviewStateService,
            QuestionGenerationService questionGenerationService,
            InterviewLogService interviewLogService,
            InterviewService interviewService,
            QuestionService questionService) {
        this.interviewStateService = interviewStateService;
        this.questionGenerationService = questionGenerationService;
        this.interviewLogService = interviewLogService;
        this.interviewService = interviewService;
        this.questionService = questionService;
    }

    @Override
    @Transactional
    public Question startInterview(Long interviewId) {
        // 1. 更新面试状态
        interviewStateService.start(interviewId);

        // 2. 获取面试详情
        Interview interview = interviewService.getById(interviewId);
        if (interview == null) {
            throw new RuntimeException("未找到面试记录: " + interviewId);
        }

        // 真实场景下，这里应该解析 PDF/Word 简历内容
        String resumeContent = "简历地址: " + interview.getResumeUrl();
        // 真实场景下，这里应该从职位服务获取 JD
        String jobDescription = "职位ID: " + interview.getJobId() + ", 分类: " + interview.getCategory();

        // 3. 生成面试题
        List<Question> questions = questionGenerationService.generateQuestions(resumeContent, jobDescription, 5);

        // 如果是新生成的题目，保存到数据库（可选，或者仅用于本次会话）
        // 这里我们假设取第一个问题
        if (questions.isEmpty()) {
            throw new RuntimeException("生成面试题失败");
        }

        Question firstQuestion = questions.get(0);
        // 确保问题已保存，以便拥有 ID
        if (firstQuestion.getId() == null) {
            questionService.save(firstQuestion);
        }

        // 4. 创建第一轮面试日志
        createLog(interviewId, 1, firstQuestion.getContent());

        return firstQuestion;
    }

    @Override
    @Transactional
    public Question submitAnswer(Long interviewId, String answerText) {
        // 1. 查找当前活跃的面试日志
        InterviewLog currentLog = interviewLogService.lambdaQuery()
                .eq(InterviewLog::getInterviewId, interviewId)
                .orderByDesc(InterviewLog::getRound)
                .last("LIMIT 1")
                .one();

        if (currentLog == null) {
            throw new RuntimeException("未找到该面试的活跃问题: " + interviewId);
        }

        // 2. 保存回答
        currentLog.setAnswerText(answerText);
        // TODO: 触发异步评分
        interviewLogService.updateById(currentLog);

        // 3. 决定下一步（追问或下一题）
        Question currentQuestion = new Question();
        currentQuestion.setContent(currentLog.getQuestionText());
        // 我们可能需要更多关于问题的信息，但内容是追问的关键

        Question followUp = questionGenerationService.generateFollowUp(currentQuestion, answerText);

        if (followUp != null) {
            // 进行追问
            createLog(interviewId, currentLog.getRound() + 1, followUp.getContent());
            return followUp;
        } else {
            // 下一个新问题
            // 在真实应用中，我们会检索预生成的列表或根据进度生成新问题
            // 这里我们简单地为下一轮生成一个新问题

            // 再次获取面试详情以获取上下文
            Interview interview = interviewService.getById(interviewId);
            if (interview == null) {
                throw new RuntimeException("未找到面试记录: " + interviewId);
            }
            String resumeContent = "简历地址: " + interview.getResumeUrl();
            String jobDescription = "职位ID: " + interview.getJobId() + ", 分类: " + interview.getCategory();

            List<Question> nextQuestions = questionGenerationService.generateQuestions(resumeContent, jobDescription,
                    1);
            if (nextQuestions.isEmpty()) {
                // 如果没有更多问题，结束面试
                interviewStateService.complete(interviewId);
                return null;
            }
            Question nextQ = nextQuestions.get(0);
            if (nextQ.getId() == null) {
                questionService.save(nextQ);
            }
            createLog(interviewId, currentLog.getRound() + 1, nextQ.getContent());
            return nextQ;
        }
    }

    private void createLog(Long interviewId, int round, String questionText) {
        InterviewLog log = new InterviewLog();
        log.setInterviewId(interviewId);
        log.setRound(round);
        log.setQuestionText(questionText);
        interviewLogService.save(log);
    }
}
