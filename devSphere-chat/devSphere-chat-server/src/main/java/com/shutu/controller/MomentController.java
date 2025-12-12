package com.shutu.controller;


import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.tools.utils.Result;
import com.shutu.model.dto.moment.CreateMomentReq;
import com.shutu.model.entity.UserProfile;
import com.shutu.model.vo.moment.MomentResp;
import com.shutu.service.MomentService;
import com.shutu.service.UserProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@Tag(name = "朋友圈管理")
@RequestMapping("/api/moments")
@RequiredArgsConstructor
public class MomentController {

    private final MomentService momentService;
    private final UserProfileService profileService;


    @PostMapping("/create")
    public Result<MomentResp> create(@RequestBody CreateMomentReq req) {
        MomentResp resp = momentService.createMoment(SecurityUser.getUserId(), req);
        return new Result<MomentResp>().ok(resp);
    }

    @GetMapping("/list")
    public Result<List<MomentResp>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        List<MomentResp> list = momentService.pageMoments(SecurityUser.getUserId(), page, size);
        return  new Result<List<MomentResp>>().ok(list);
    }

    @PostMapping("/{postId}/like")
    public Result<Void> like(@PathVariable Long postId) {
        momentService.like(SecurityUser.getUserId(), postId);
        return new Result<Void>().ok();
    }

    @PostMapping("/{postId}/unlike")
    public Result<Void> unlike(@PathVariable Long postId) {
        momentService.unlike(SecurityUser.getUserId(), postId);
        return new Result<Void>().ok();
    }

    @PostMapping("/{postId}/comment")
    public Result<Void> comment(@PathVariable Long postId,
                                        @RequestParam String content, @RequestParam(required = false) Long replyTo) {
        momentService.comment(SecurityUser.getUserId(), postId, content, replyTo);
        return new Result<Void>().ok();
    }

    @DeleteMapping("/{postId}")
    public Result<Void> delete(@PathVariable Long postId) {
        momentService.deleteMoment(SecurityUser.getUserId(), postId);
        return new Result<Void>().ok();
    }

    // 个人资料接口
    @GetMapping("/profile")
    public Result<UserProfile> getProfile() {
        return new Result<UserProfile>().ok(profileService.getByUserId(SecurityUser.getUserId()));
    }

    @PostMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UserProfile p) {
        p.setUserId(SecurityUser.getUserId());
        profileService.updateProfile(p);
        return new Result<Void>().ok();
    }


}
