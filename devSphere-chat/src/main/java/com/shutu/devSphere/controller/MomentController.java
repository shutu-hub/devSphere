package com.shutu.devSphere.controller;


import com.shutu.devSphere.model.dto.moment.CreateMomentReq;
import com.shutu.devSphere.model.entity.UserProfile;
import com.shutu.devSphere.model.vo.moment.MomentResp;
import com.shutu.devSphere.service.MomentService;
import com.shutu.devSphere.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/moments")
@RequiredArgsConstructor
public class MomentController {

    private final MomentService momentService;
    private final UserProfileService profileService;


    @PostMapping("/create")
    public ResponseEntity<MomentResp> create(@RequestHeader("X-User-Id") Long userId,
                                             @RequestBody CreateMomentReq req) {
        MomentResp resp = momentService.createMoment(userId, req);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/list")
    public ResponseEntity<List<MomentResp>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        List<MomentResp> list = momentService.pageMoments(null, page, size);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<Void> like(@RequestHeader("X-User-Id") Long userId, @PathVariable Long postId) {
        momentService.like(userId, postId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{postId}/unlike")
    public ResponseEntity<Void> unlike(@RequestHeader("X-User-Id") Long userId, @PathVariable Long postId) {
        momentService.unlike(userId, postId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{postId}/comment")
    public ResponseEntity<Void> comment(@RequestHeader("X-User-Id") Long userId, @PathVariable Long postId,
                                        @RequestParam String content, @RequestParam(required = false) Long replyTo) {
        momentService.comment(userId, postId, content, replyTo);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@RequestHeader("X-User-Id") Long userId, @PathVariable Long postId) {
        momentService.deleteMoment(userId, postId);
        return ResponseEntity.ok().build();
    }

    // 个人资料接口
    @GetMapping("/profile")
    public ResponseEntity<UserProfile> getProfile(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(profileService.getByUserId(userId));
    }

    @PostMapping("/profile")
    public ResponseEntity<Void> updateProfile(@RequestHeader("X-User-Id") Long userId,
                                              @RequestBody UserProfile p) {
        p.setUserId(userId);
        profileService.updateProfile(p);
        return ResponseEntity.ok().build();
    }


}
