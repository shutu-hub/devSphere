package com.shutu.service.Impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.enums.SuperAdminEnum;
import com.shutu.commons.tools.exception.UnauthorizedException;
import com.shutu.commons.tools.page.PageData;
import com.shutu.commons.tools.utils.ConvertUtils;
import com.shutu.commons.tools.utils.Result;
import com.shutu.dao.SysUserDao;
import com.shutu.model.LoginRequest;
import com.shutu.model.dto.SysUserDTO;
import com.shutu.model.dto.UserTokenDTO;
import com.shutu.model.entity.SysUserEntity;
import com.shutu.service.*;
import com.shutu.commons.mybatis.service.impl.BaseServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends BaseServiceImpl<SysUserDao, SysUserEntity> implements SysUserService {

    private final SysRoleUserService sysRoleUserService;

    private final SysDeptService sysDeptService;

    private final PasswordEncoder passwordEncoder;

    private final SysUserPostService sysUserPostService;

    private final SysUserTokenService sysUserTokenService;

    @Override
    public PageData<SysUserDTO> page(Map<String, Object> params) {
        //转换成like
        paramsToLike(params, "username");

        //分页
        IPage<SysUserEntity> page = getPage(params, "t1.create_date", false);

        //普通管理员，只能查询所属部门及子部门的数据
        UserDetail user = SecurityUser.getUser();
        if (user.getSuperAdmin() == SuperAdminEnum.NO.value()) {
            params.put("deptIdList", sysDeptService.getSubDeptIdList(user.getDeptId()));
        }

        //查询
        List<SysUserEntity> list = baseDao.getList(params);

        return getPageData(list, page.getTotal(), SysUserDTO.class);
    }

    @Override
    public List<SysUserDTO> list(Map<String, Object> params) {
        //普通管理员，只能查询所属部门及子部门的数据
        UserDetail user = SecurityUser.getUser();
        if (user.getSuperAdmin() == SuperAdminEnum.NO.value()) {
            params.put("deptIdList", sysDeptService.getSubDeptIdList(user.getDeptId()));
        }

        List<SysUserEntity> entityList = baseDao.getList(params);

        return ConvertUtils.sourceToTarget(entityList, SysUserDTO.class);
    }

    @Override
    public SysUserDTO get(Long id) {
        SysUserEntity entity = baseDao.getById(id);

        return ConvertUtils.sourceToTarget(entity, SysUserDTO.class);
    }

    @Override
    public SysUserDTO getByUsername(String username) {
        SysUserEntity entity = baseDao.getByUsername(username);
        return ConvertUtils.sourceToTarget(entity, SysUserDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(SysUserDTO dto) {
        SysUserEntity entity = ConvertUtils.sourceToTarget(dto, SysUserEntity.class);

        //密码加密
        String password = passwordEncoder.encode(entity.getPassword());
        entity.setPassword(password);

        //保存用户
        entity.setSuperAdmin(SuperAdminEnum.NO.value());
        insert(entity);

        //保存角色用户关系
        ArrayList<Long> ids = new ArrayList<>();
        ids.add(1L);
        dto.setRoleIdList(ids);
        sysRoleUserService.saveOrUpdate(entity.getId(), dto.getRoleIdList());

        //保存用户岗位关系
        sysUserPostService.saveOrUpdate(entity.getId(), dto.getPostIdList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysUserDTO dto) {
        SysUserEntity entity = ConvertUtils.sourceToTarget(dto, SysUserEntity.class);

        //密码加密
        if (StringUtils.isBlank(dto.getPassword())) {
            entity.setPassword(null);
        } else {
            String password = passwordEncoder.encode(entity.getPassword());
            entity.setPassword(password);
        }

        //更新用户
        updateById(entity);

        //更新角色用户关系
        sysRoleUserService.saveOrUpdate(entity.getId(), dto.getRoleIdList());

        //保存用户岗位关系
        sysUserPostService.saveOrUpdate(entity.getId(), dto.getPostIdList());

        //更新用户缓存权限
        sysUserTokenService.updateCacheAuthByUserId(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(SysUserDTO dto) {
        SysUserEntity entity = selectById(dto.getId());
        entity.setHeadUrl(dto.getHeadUrl());
        entity.setRealName(dto.getRealName());
        entity.setGender(dto.getGender());
        entity.setMobile(dto.getMobile());
        entity.setEmail(dto.getEmail());

        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long[] ids) {
        //逻辑删除
        logicDelete(ids, SysUserEntity.class);

        //角色用户关系，岗位关系需要保留，不然逻辑删除就变成物理删除了
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long id, String newPassword) {
        newPassword = passwordEncoder.encode(newPassword);

        baseDao.updatePassword(id, newPassword);
    }

    @Override
    public int getCountByDeptId(Long deptId) {
        return baseDao.getCountByDeptId(deptId);
    }

    @Override
    public List<Long> getUserIdListByDeptId(List<Long> deptIdList) {
        return baseDao.getUserIdListByDeptId(deptIdList);
    }

    @Override
    public List<String> getRealNameList(List<Long> ids) {
        return baseDao.getRealNameList(ids);
    }

    @Override
    public List<Long> getUserIdListByRoleIdList(List<Long> ids) {
        return baseDao.getUserIdListByRoleIdList(ids);
    }

    @Override
    public List<String> getRoleNameList(List<Long> ids) {
        return baseDao.getRoleNameList(ids);
    }

    @Override
    public List<Long> getUserIdListByPostIdList(List<Long> ids) {
        return baseDao.getUserIdListByPostIdList(ids);
    }

    @Override
    public List<Long> getLeaderIdListByDeptIdList(List<Long> ids) {
        return baseDao.getLeaderIdListByDeptIdList(ids);
    }

    @Override
    public Long getLeaderIdListByUserId(Long userId) {
        return baseDao.getLeaderIdListByUserId(userId);
    }

    @Override
    public UserTokenDTO login(LoginRequest loginRequest) {
        // 校验参数
        if (loginRequest == null || StringUtils.isEmpty(loginRequest.getPhone())
                || StringUtils.isEmpty(loginRequest.getPassword())) {
            throw new UnauthorizedException("用户名或密码不能为空");
        }

        // 根据用户名查询用户信息
        SysUserEntity user = baseDao.getUserDetailByPhone(loginRequest.getPhone());
        
        // 用户不存在
        if (user == null) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        //状态为停用
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new UnauthorizedException("账户已被停用，请联系管理员");
        }

        // 校验密码
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        // 生成并返回token
        return sysUserTokenService.createToken(user.getId());
    }

    @Override
    public Result<Set<SysUserEntity>> getMyFriend() {
        return null;
    }

    @Override
    public List<SysUserEntity> queryUsersByIds(List<Long> userIds) {
        return List.of();
    }

    @Override
    public List<SysUserDTO> getUsersByIds(List<Long> ids) {
        List<SysUserEntity> userEntityList = baseDao.selectBatchIds(ids);

        // 将Entity列表转换为DTO列表
        return ConvertUtils.sourceToTarget(userEntityList, SysUserDTO.class);
    }
}