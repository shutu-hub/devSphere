package com.shutu.interview.model.request;

import lombok.Data;

@Data
public class InterviewCreateRequest {
    /**
     * 岗位ID
     */
    private Long jobId;

    /**
     * 简历地址 (OSS URL)
     */
    private String resumeUrl;

    /**
     * 面试方向/分类 (如: Java, Frontend)
     */
    private String category;
}
