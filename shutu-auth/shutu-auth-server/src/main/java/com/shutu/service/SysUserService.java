package com.shutu.service;

import com.shutu.commons.mybatis.service.BaseService;
import com.shutu.commons.tools.page.PageData;
import com.shutu.commons.tools.utils.Result;
import com.shutu.domain.dto.SysUserDTO;
import com.shutu.domain.entity.SysUserEntity;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理
 *
 * @author Mark sunlightcs@gmail.com
 * @since 1.0.0
 */
public interface SysUserService extends BaseService<SysUserEntity> {

    PageData<SysUserDTO> page(Map<String, Object> params);

    List<SysUserDTO> list(Map<String, Object> params);

    SysUserDTO get(Long id);

    SysUserDTO getByUsername(String username);

    void save(SysUserDTO dto);

    void update(SysUserDTO dto);

    void updateUserInfo(SysUserDTO dto);

    void delete(Long[] ids);

    /**
     * 修改密码
     *
     * @param id          用户ID
     * @param newPassword 新密码
     */
    void updatePassword(Long id, String newPassword);

    /**
     * 根据部门ID，查询用户数
     */
    int getCountByDeptId(Long deptId);

    /**
     * 根据部门ID,查询用户ID列表
     */
    List<Long> getUserIdListByDeptId(List<Long> deptIdList);

    /**
     * 根据用户ID,查询用户姓名列表
     */
    List<String> getRealNameList(List<Long> ids);

    /**
     * 根据角色ID,查询用户ID列表
     */
    List<Long> getUserIdListByRoleIdList(List<Long> ids);

    /**
     * 根据角色ID,查询角色名称列表
     */
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


    Result<Set<SysUserEntity>> getMyFriend();

    List<SysUserEntity> queryUsersByIds(List<Long> userIds);

    List<SysUserDTO> getUsersByIds(List<Long> ids);
}