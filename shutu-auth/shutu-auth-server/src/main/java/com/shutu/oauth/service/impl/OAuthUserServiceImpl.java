package com.shutu.oauth.service.impl;

import com.shutu.commons.security.cache.TokenStoreCache;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.oauth.constant.CacheConstants;
import com.shutu.oauth.model.entity.OAuthUserInfo;
import com.shutu.oauth.service.OAuthUserService;
import com.shutu.dao.SysUserDao;
import com.shutu.model.dto.UserTokenDTO;
import com.shutu.model.entity.SysUserEntity;
import com.shutu.service.SysRoleUserService;
import com.shutu.service.SysUserDetailService;
import com.shutu.service.SysUserPostService;
import com.shutu.service.SysUserTokenService;
import com.shutu.utils.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthUserServiceImpl implements OAuthUserService {

    private final SysUserDao sysUserDao;
    private final SysUserTokenService sysUserTokenService;
    private final SysRoleUserService sysRoleUserService;
    private final SysUserPostService sysUserPostService;
    private final DistributedLock distributedLock;
    private final TokenStoreCache tokenStoreCache;
    private final SysUserDetailService sysUserDetailService;
    
    // 假设这是普通用户的角色ID
    private static final Long DEFAULT_USER_ROLE_ID = 1779058124507430914L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserTokenDTO loginOrRegister(OAuthUserInfo userInfo) throws InterruptedException {
        String lockKey = String.format("%s%s:%s",
                CacheConstants.LOGIN_USER_LOCK_KEY_PREFIX, userInfo.getPlatform(), userInfo.getUniqueId());

        boolean isLocked = distributedLock.tryLock(lockKey, 10, TimeUnit.SECONDS);
        if (!isLocked) {
            throw new CommonException("操作频繁，请稍后再试");
        }

        try {
            // 根据平台和唯一ID查找用户
            SysUserEntity user = sysUserDao.findByPlatformAndUniqueId(userInfo.getPlatform(), userInfo.getUniqueId());

            if (user == null) {
                // 用户不存在，执行注册逻辑
                user = registerNewUser(userInfo);
            } else {
                // 用户已存在，执行更新逻辑
                updateUser(user, userInfo);
            }

            // 创建系统Token
            UserTokenDTO tokenDTO = sysUserTokenService.createToken(user.getId());
            tokenDTO.setUserId(user.getId());

            // 缓存用户信息
            UserDetail userDetail = sysUserDetailService.getUserDetailById(user.getId());
            log.error(userDetail.toString());
            tokenStoreCache.saveUser(tokenDTO.getAccessToken(), userDetail);

            return tokenDTO;
        } finally {
            distributedLock.unlock(lockKey);
        }
    }

    private SysUserEntity registerNewUser(OAuthUserInfo userInfo) {
        log.info("注册新用户, platform: {}, uniqueId: {}", userInfo.getPlatform(), userInfo.getUniqueId());
        SysUserEntity newUser = new SysUserEntity();
        
        // 设置来自第三方平台的信息
        newUser.setPlatform(userInfo.getPlatform());
        newUser.setUniqueId(userInfo.getUniqueId());
        newUser.setNickName(userInfo.getNickname());
        newUser.setHeadUrl(userInfo.getAvatarUrl());
        
        // 设置系统默认信息
        String username = generateUniqueUsername(userInfo.getNickname());
        newUser.setUsername(username);
        newUser.setRealName(username); // 默认真实姓名同用户名
        newUser.setStatus(1); // 启用状态
        newUser.setSuperAdmin(0); // 非超级管理员

        sysUserDao.insert(newUser);

        // 分配默认角色
        sysRoleUserService.saveOrUpdate(newUser.getId(), Collections.singletonList(DEFAULT_USER_ROLE_ID));
        // 分配岗位（如果需要）
        sysUserPostService.saveOrUpdate(newUser.getId(), Collections.emptyList());

        return newUser;
    }

    private void updateUser(SysUserEntity user, OAuthUserInfo userInfo) {
        log.info("更新用户信息, userId: {}", user.getId());
        user.setNickName(userInfo.getNickname());
        user.setHeadUrl(userInfo.getAvatarUrl());
        sysUserDao.updateById(user);
    }

    private String generateUniqueUsername(String nickname) {
        // 生成一个不可能重复的用户名
        String baseName = "user_" + nickname.replaceAll("\\s+", "");
        String uniqueSuffix = Long.toHexString(System.nanoTime());
        String username = baseName + "_" + uniqueSuffix.substring(uniqueSuffix.length() - 6);
        // 检查用户名是否已存在，如果存在则重新生成 (简单实现)
        if (sysUserDao.findByUsername(username) != null) {
            return generateUniqueUsername(nickname);
        }
        return username;
    }
}
