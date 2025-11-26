package com.shutu.devSphere.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutu.commons.tools.utils.Result;
import com.shutu.devSphere.mapper.MomentCommentMapper;
import com.shutu.devSphere.mapper.MomentLikeMapper;
import com.shutu.devSphere.mapper.MomentPostMapper;
import com.shutu.devSphere.mapper.UserProfileMapper;
import com.shutu.devSphere.model.dto.moment.CreateMomentReq;
import com.shutu.devSphere.model.dto.user.UserDto;
import com.shutu.devSphere.model.entity.MomentComment;
import com.shutu.devSphere.model.entity.MomentPost;
import com.shutu.devSphere.model.entity.RoomFriend;
import com.shutu.devSphere.model.entity.UserProfile;
import com.shutu.devSphere.model.vo.moment.MomentResp;
import com.shutu.devSphere.model.vo.moment.UserVo;
import com.shutu.devSphere.service.MomentService;
import com.shutu.devSphere.service.RoomFriendService;
import com.shutu.dto.SysUserDTO;
import com.shutu.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MomentServiceImpl implements MomentService {

    private final MomentPostMapper postMapper;
    private final UserProfileMapper userProfileMapper;
    private final MomentLikeMapper likeMapper;
    private final RoomFriendService roomFriendService;
    private final ObjectMapper objectMapper;
    private final MomentCommentMapper commentMapper;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional
    public MomentResp createMoment(Long userId, CreateMomentReq req) {
        MomentPost post = new MomentPost();
        post.setUserId(userId);
        post.setContent(req.getContent());
        try {
            String json = req.getImageUrls() == null ? "[]" : objectMapper.writeValueAsString(req.getImageUrls());
            post.setImageUrls(json);
        } catch (Exception e) {
            post.setImageUrls("[]");
        }
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setVisibility(req.getVisibility() == null ? 1 : req.getVisibility());
        postMapper.insert(post);
        // build response
        return toMomentResp(post, userId, Collections.emptyMap());
    }

    @Override
    public List<MomentResp> pageMoments(Long userId, int page, int size) {
        // 1. 获取好友列表
        List<RoomFriend> friends = roomFriendService
                .list(new LambdaQueryWrapper<RoomFriend>()
                        .eq(RoomFriend::getUid1, userId)
                        .or()
                        .eq(RoomFriend::getUid2, userId));

        List<Long> friendIds = new ArrayList<>();
        friendIds.add(userId); // 自己
        for (RoomFriend f : friends) {
            friendIds.add(f.getUid1().equals(userId) ? f.getUid2() : f.getUid1());
        }

        Page<MomentPost> pg = new Page<>(page, size);
        LambdaQueryWrapper<MomentPost> qw = new LambdaQueryWrapper<MomentPost>()
                .in(MomentPost::getUserId, friendIds)
                .orderByDesc(MomentPost::getCreatedAt);

        Page<MomentPost> res = postMapper.selectPage(pg, qw);
        List<MomentPost> records = res.getRecords();

        // Batch fetch user info via Feign
        Set<Long> userIdSet = new HashSet<>();
        for (MomentPost p : records) {
            userIdSet.add(p.getUserId());
        }
        List<Long> idsList = new ArrayList<>(userIdSet);
        // simple batch size of 20
        Map<Long, SysUserDTO> userMap = new HashMap<>();
        int batchSize = 20;
        for (int i = 0; i < idsList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, idsList.size());
            List<Long> sub = idsList.subList(i, end);
            try {
                Result<List<SysUserDTO>> listResult = userFeignClient.listByIds(sub);
                List<SysUserDTO> data = listResult.getData();
                if (data != null) {
                    for (SysUserDTO u : data) {
                        userMap.put(u.getId(), u);
                    }
                }
            } catch (Exception e) {
                // fallback: ignore and continue
            }
        }

        List<MomentResp> list = new ArrayList<>();
        for (MomentPost p : records) {
            list.add(toMomentResp(p, userId, userMap));
        }
        return list;
    }

    @Override
    @Transactional
    public void like(Long userId, Long postId) {
        try {
            Map<String, Object> rec = new HashMap<>();
            rec.put("post_id", postId);
            rec.put("user_id", userId);
            rec.put("id", IdWorker.getId());
            likeMapper.insertLikeIfNotExist(rec);
        } catch (Exception e) {
            // ignore dup
        }
        // increment count
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MomentPost>()
                .eq(MomentPost::getId, postId)
                .setSql("like_count = like_count + 1"));
    }

    @Override
    @Transactional
    public void unlike(Long userId, Long postId) {
        likeMapper.deleteByPostUser(postId, userId);
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MomentPost>()
                .eq(MomentPost::getId, postId)
                .setSql("like_count = GREATEST(like_count - 1, 0)"));
    }

    @Override
    @Transactional
    public void comment(Long userId, Long postId, String content, Long replyTo) {
        MomentComment cmt = new MomentComment();
        cmt.setPostId(postId);
        cmt.setUserId(userId);
        cmt.setContent(content);
        cmt.setReplyToCommentId(replyTo);
        commentMapper.insert(cmt);
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MomentPost>()
                .eq(MomentPost::getId, postId)
                .setSql("comment_count = comment_count + 1"));
    }

    @Override
    @Transactional
    public void deleteMoment(Long userId, Long postId) {
        MomentPost p = postMapper.selectById(postId);
        if (p == null)
            return;
        if (!Objects.equals(p.getUserId(), userId)) {
            throw new IllegalStateException("only owner can delete");
        }
        postMapper.deleteById(postId);
        // cascade delete likes/comments (mapper methods)
        likeMapper.deleteByPost(postId);
        commentMapper.deleteByPost(postId);
    }

    private MomentResp toMomentResp(MomentPost p, Long currentUserId, Map<Long, SysUserDTO> userMap) {
        MomentResp r = new MomentResp();
        r.setId(p.getId());
        r.setUserId(p.getUserId());
        r.setContent(p.getContent());
        try {
            List<String> urls = objectMapper.readValue(Optional.ofNullable(p.getImageUrls()).orElse("[]"),
                    new TypeReference<List<String>>() {
                    });
            r.setImageUrls(urls);
        } catch (Exception e) {
            r.setImageUrls(Collections.emptyList());
        }
        r.setLikeCount(Optional.ofNullable(p.getLikeCount()).orElse(0));
        r.setCommentCount(Optional.ofNullable(p.getCommentCount()).orElse(0));
        r.setCreatedAt(Optional.ofNullable(p.getCreatedAt()).map(Date::toString).orElse(""));

        SysUserDTO ud = userMap.get(p.getUserId());
        UserVo u = new UserVo();
        u.setUserId(p.getUserId());
        if (ud != null) {
            u.setUsername(ud.getUsername());
            u.setAvatar(ud.getHeadUrl());
            u.setDisplayName(ud.getRealName());
        } else {
            // fallback to existing profile mapper if needed
            UserProfile up = userProfileMapper.selectById(p.getUserId());
            u.setUsername(up == null ? String.valueOf(p.getUserId()) : up.getDisplayName());
            u.setAvatar(up == null ? null : up.getAvatar());
            u.setDisplayName(up == null ? null : up.getDisplayName());
        }
        r.setUser(u);

        // 填充互动详情
        r.setLikes(likeMapper.selectLikesByPostId(p.getId()));
        r.setComments(commentMapper.selectCommentsByPostId(p.getId()));

        // 判断当前用户是否点赞
        if (currentUserId != null) {
            Integer count = likeMapper.hasLiked(p.getId(), currentUserId);
            r.setIsLiked(count != null && count > 0);
        } else {
            r.setIsLiked(false);
        }
        return r;
    }
}
