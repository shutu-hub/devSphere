package com.shutu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shutu.model.entity.MomentComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MomentCommentMapper extends BaseMapper<MomentComment> {

    /**
     * 查询某条动态下的所有评论
     */
    List<MomentComment> listByMomentId(@Param("momentId") Long momentId);

    /**
     * 评论数量 +1
     */
    int incrCommentCount(@Param("momentId") Long momentId);

    /**
     * 评论数量 -1
     */
    int decrCommentCount(@Param("momentId") Long momentId);

    /** 删除某个动态的所有评论 */
    int deleteByPost(@Param("postId") Long postId);

    int insert(MomentComment comment);

    /** 查询某条动态的评论列表（带用户信息） */
    List<com.shutu.model.vo.moment.CommentVo> selectCommentsByPostId(@Param("postId") Long postId);

}
