package com.shutu.service;


import com.shutu.model.dto.moment.CreateMomentReq;
import com.shutu.model.vo.moment.MomentResp;

import java.util.List;

public interface MomentService {
    MomentResp createMoment(Long userId, CreateMomentReq req);

    List<MomentResp> pageMoments(Long userId, int page, int size);

    void like(Long userId, Long postId);

    void unlike(Long userId, Long postId);


    void comment(Long userId, Long postId, String content, Long replyTo);

    void deleteMoment(Long userId, Long postId);
}
