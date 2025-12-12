package com.shutu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shutu.model.entity.MomentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;
import java.util.List;

@Mapper
public interface MomentLikeMapper extends BaseMapper<MomentLike> {

    /**
     * 是否点过赞
     */
    Integer hasLiked(@Param("momentId") Long momentId,
            @Param("userId") Long userId);

    /**
     * 点赞计数 +1
     */
    int incrLikeCount(@Param("momentId") Long momentId);

    /**
     * 点赞计数 -1
     */
    int decrLikeCount(@Param("momentId") Long momentId);

    /** 插入点赞（不存在才插入） */
    int insertLikeIfNotExist(Map<String, Object> params);

    /** 取消点赞 */
    int deleteByPostUser(@Param("postId") Long postId,
            @Param("userId") Long userId);

    /** 删除某个动态的所有点赞 */
    int deleteByPost(@Param("postId") Long postId);

    /** 查询某条动态的点赞列表（带用户信息） */
    List<com.shutu.model.vo.moment.UserVo> selectLikesByPostId(@Param("postId") Long postId);

}
