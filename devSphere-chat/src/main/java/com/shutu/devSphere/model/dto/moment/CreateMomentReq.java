package com.shutu.devSphere.model.dto.moment;

import lombok.Data;

import java.util.List;

@Data
public class CreateMomentReq {
    private String content;
    private List<String> imageUrls; // 前端上传后获取的 URL 列表
    private Integer visibility; // optional
}