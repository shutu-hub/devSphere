package com.shutu.controller;

import com.shutu.commons.security.cache.TokenStoreCache;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.security.utils.TokenUtils;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.commons.tools.redis.RedisKeys;
import com.shutu.commons.tools.redis.RedisUtils;
import com.shutu.commons.tools.utils.CheckCodeUtil;
import com.shutu.commons.tools.utils.Result;
import com.shutu.commons.tools.validator.AssertUtils;
import com.shutu.domain.dto.LoginDTO;
import com.shutu.domain.dto.UserTokenDTO;
import com.shutu.service.CaptchaService;
import com.shutu.service.SysUserTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;


/**
 * 用户登录
 */

@RestController
@Tag(name = "用户登录")
@RequestMapping("/auth")
public class LoginController {
    @Autowired
    private CaptchaService captchaService;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private TokenStoreCache tokenStoreCache;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private SysUserTokenService sysUserTokenService;


    @GetMapping("/captcha")
    @Operation(summary = "验证码")
    @Parameter(name = "uuid", required = true)
    public String captcha(HttpServletResponse response, String uuid) throws IOException {
        //uuid不能为空
        AssertUtils.isBlank(uuid, ErrorCode.IDENTIFIER_NOT_NULL);
        // 1.生成验证码
        ServletOutputStream os = response.getOutputStream();
        String verifyCode = CheckCodeUtil.outputVerifyImage(100, 50, os, 4);
        setCache(uuid, verifyCode);
        return verifyCode;
    }

    @ResponseBody
    @PostMapping("/login")
    @Operation(summary = "账号密码登录")
    public Result<UserTokenDTO> login(@RequestBody LoginDTO login) throws Exception {
        // 验证码效验
        boolean flag = captchaService.validate(login.getUuid(), login.getCaptcha());
        if (!flag) {
            throw new Exception("验证码错误");
        }

        Authentication authentication;
        try {
            // 用户认证
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(login.getUsername(), login.getPassword()));
        } catch (Exception e) {
            throw new Exception("用户名或密码错误");
        }

        // 认证成功拿到用户信息
        UserDetail user = (UserDetail) authentication.getPrincipal();

        // 生成 accessToken
        UserTokenDTO userTokenVO = sysUserTokenService.createToken(user.getId());

        // 保存用户信息到缓存
        tokenStoreCache.saveUser(userTokenVO.getAccessToken(), user);

        return new Result<UserTokenDTO>().ok(userTokenVO);
    }

    @ResponseBody
    @PostMapping("access-token")
    @Operation(summary = "刷新 access_token")
    public Result<UserTokenDTO> getAccessToken(String refreshToken) {
        UserTokenDTO token = sysUserTokenService.refreshToken(refreshToken);

        return new Result<UserTokenDTO>().ok(token);
    }

    @ResponseBody
    @PostMapping("logout")
    @Operation(summary = "退出")
    public Result logout(HttpServletRequest request) {
        String accessToken = TokenUtils.getAccessToken(request);

        // 用户信息
        UserDetail user = tokenStoreCache.getUser(accessToken);

        // 删除用户信息
        tokenStoreCache.deleteUser(accessToken);

        // Token过期
        sysUserTokenService.expireToken(user.getId());
//
//        // 保存日志
//        SysLogLogin log = new SysLogLogin();
//        log.setType(LogTypeEnum.LOGIN.value());
//        log.setOperation(LoginOperationEnum.LOGOUT.value());
//        log.setIp(IpUtils.getIpAddr(request));
//        log.setUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
//        log.setIp(IpUtils.getIpAddr(request));
//        log.setCreator(user.getId());
//        log.setCreatorName(user.getUsername());
//        log.setCreateDate(new Date());
//        logProducer.saveLog(log);

        //清空菜单导航、权限标识
        redisUtils.deleteByPattern(RedisKeys.getUserMenuNavKey(user.getId()));
        redisUtils.delete(RedisKeys.getUserPermissionsKey(user.getId()));

        return new Result();
    }

    private void setCache(String uuid, String captcha) {
        String key = RedisKeys.getLoginCaptchaKey(uuid);

        redisUtils.set(key, captcha, 60 * 5L);
    }

}
