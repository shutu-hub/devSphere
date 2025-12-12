package com.shutu.dao; 

import com.shutu.commons.mybatis.dao.BaseDao;
import com.shutu.model.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/**
 * 用户管理
 */
@Mapper
public interface SysUserDao extends BaseDao<SysUserEntity> {

    List<SysUserEntity> getList(Map<String, Object> params);

    SysUserEntity getById(Long id);

    SysUserEntity getByUsername(String username);

    int updatePassword(@Param("id") Long id, @Param("newPassword") String newPassword);

    /**
     * 根据部门ID，查询用户数
     */
    int getCountByDeptId(Long deptId);

    /**
     * 根据部门ID,查询用户ID列表
     */
    List<Long> getUserIdListByDeptId(List<Long> deptIdList);

    List<String> getRealNameList(List<Long> ids);

    List<Long> getUserIdListByRoleIdList(List<Long> ids);

    List<String> getRoleNameList(List<Long> ids);

    List<Long> getUserIdListByPostIdList(List<Long> ids);

    /**
     * 查询部门领导列表
     *
     * @param ids 部门列表
     */
    List<Long> getLeaderIdListByDeptIdList(List<Long> ids);

    /**
     * 获取用户部门领导ID
     *
     * @param userId 用户ID
     */
    Long getLeaderIdListByUserId(Long userId);

    /**
     * 根据QQ OpenID获取用户
     * @return 用户信息
     */
    SysUserEntity getByQQOpenId(@Param("openid") String openid);

    /**
     * 根据邮箱获取用户
     * @return 用户信息
     */
    SysUserEntity getByEmail(String email);

    /**
     * 根据用户名或邮箱获取用户
     * @return 用户信息
     */
    SysUserEntity getUserDetailByUsernameOrEmail(@Param("username") String username, @Param("email") String email);


    /**
     * 根据用户名获取用户
     * @return 用户信息
     */
    SysUserEntity selectByUsername(String username);

    /**
     * 根据第三方登录平台和平台唯一ID查询用户
     * 用于OAuth2.0登录，判断用户是否已存在
     *
     * @param platform 平台名称 (e.g., "gitee", "wechat", "qq")
     * @param uniqueId 用户在该平台上的唯一标识
     * @return 匹配的用户实体，如果不存在则返回 null
     */
    SysUserEntity findByPlatformAndUniqueId(@Param("platform") String platform, @Param("uniqueId") String uniqueId);

    /**
     * 根据用户名查询用户
     * 用于在注册新用户时，确保生成的用户名是唯一的
     *
     * @param username 用户名
     * @return 匹配的用户实体，如果不存在则返回 null
     */
    SysUserEntity findByUsername(@Param("username") String username);

    /**
     * 根据手机号查询用户
     * @param phone 手机号
     * @return 匹配的用户实体
     */
    SysUserEntity getUserDetailByPhone(String phone);

}
