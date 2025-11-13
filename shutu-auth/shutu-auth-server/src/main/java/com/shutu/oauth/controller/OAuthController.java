package com.shutu.oauth.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutu.commons.tools.exception.CommonException;
import com.shutu.commons.tools.utils.Result;
import com.shutu.oauth.constant.CacheConstants;
import com.shutu.oauth.model.entity.OAuthUserInfo;
import com.shutu.oauth.service.OAuthService;
import com.shutu.oauth.service.OAuthUserService;
import com.shutu.model.dto.UserTokenDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/oauth2")
@Tag(name = "统一OAuth2.0认证")
public class OAuthController {

    private final Map<String, OAuthService> oauthServices;
    private final OAuthUserService oauthUserService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.login-redirect-url}")
    private String frontendRedirectUrl;

    private static final DefaultRedisScript<String> GET_AND_DEL_SCRIPT;

    static {
        GET_AND_DEL_SCRIPT = new DefaultRedisScript<>();
        GET_AND_DEL_SCRIPT.setResultType(String.class);
        GET_AND_DEL_SCRIPT.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/get_and_del.lua")));
    }

    public OAuthController(List<OAuthService> services,
                           OAuthUserService oauthUserService,
                           StringRedisTemplate stringRedisTemplate,
                           ObjectMapper objectMapper) {
        this.oauthServices = services.stream()
                .collect(Collectors.toMap(OAuthService::getPlatform, Function.identity()));
        this.oauthUserService = oauthUserService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取指定平台的授权URL
     */
    @GetMapping("/authorize/{platform}")
    @Operation(summary = "获取授权URL", description = "获取指定第三方平台的登录授权URL")
    public Result<Map<String, String>> getAuthorizeUrl(
            @Parameter(description = "平台名称 (gitee, wechat, qq)") @PathVariable String platform) {
        OAuthService service = getOAuthService(platform);
        String state = UUID.randomUUID().toString().replace("-", "");

        // 存储 state 到 Redis，防止CSRF攻击，有效期5分钟
        stringRedisTemplate.opsForValue().set(
                CacheConstants.OAUTH_STATE_KEY_PREFIX + state,
                platform,
                5,
                TimeUnit.MINUTES
        );

        String authorizeUrl = service.buildAuthorizeUrl(state);
        log.info("为平台[{}]生成授权URL, state: {}", platform, state);

        return new Result<Map<String, String>>().ok(Map.of("authorizeUrl", authorizeUrl));
    }

    /**
     * 所有平台的统一回调接口
     */
    @GetMapping("/callback/{platform}")
    @Operation(summary = "统一回调接口", description = "接收所有第三方平台的回调请求")
    public void handleCallback(
            @Parameter(description = "平台名称") @PathVariable String platform,
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpServletResponse response) throws IOException {
        
        String redirectUrlWithError = frontendRedirectUrl + "?error=login_failed";
        
        try {
            // 1. 校验 state
            validateState(state, platform);

            // 2. 获取服务，并通过 code 获取用户信息
            OAuthService service = getOAuthService(platform);
            OAuthUserInfo userInfo = service.getUserInfo(code);

            // 3. 执行登录或注册
            UserTokenDTO tokenDTO = oauthUserService.loginOrRegister(userInfo);
            log.info("平台[{}]用户登录成功, userId: {}", platform, tokenDTO.getUserId());

            // 4. 生成一次性票据 (Ticket)
            String ticket = generateAndStoreTicket(tokenDTO);

            // 5. 重定向到前端
            String redirectUrl = frontendRedirectUrl + "?ticket=" + ticket;
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            log.error("平台[{}]回调处理失败: {}", platform, e.getMessage(), e);
            response.sendRedirect(redirectUrlWithError);
        }
    }

    /**
     * 前端通过 Ticket 换取真实的 Token
     */
    @PostMapping("/token")
    @Operation(summary = "使用Ticket换取Token", description = "前端使用一次性票据换取登录凭证")
    public Result<UserTokenDTO> claimToken(@RequestParam String ticket) {
        String redisKey = CacheConstants.OAUTH_TICKET_KEY_PREFIX + ticket;

        // 使用Lua脚本原子性地获取并删除key
        String json = stringRedisTemplate.execute(
                GET_AND_DEL_SCRIPT,
                Collections.singletonList(redisKey)
        );

        if (json == null || json.isEmpty()) {
            log.warn("Ticket无效或已过期, ticket: {}", ticket);
            throw new CommonException("凭证无效或已过期");
        }

        try {
            UserTokenDTO dto = objectMapper.readValue(json, UserTokenDTO.class);
            return new Result<UserTokenDTO>().ok(dto);
        } catch (JsonProcessingException e) {
            log.error("反序列化Token DTO失败, ticket: {}", ticket, e);
            throw new CommonException("交换凭证失败，请稍后重试");
        }
    }
    
    private OAuthService getOAuthService(String platform) {
        OAuthService service = oauthServices.get(platform);
        if (service == null) {
            log.error("不支持的OAuth平台: {}", platform);
            throw new CommonException("不支持的登录方式: " + platform);
        }
        return service;
    }

    private void validateState(String state, String expectedPlatform) {
        String redisKey = CacheConstants.OAUTH_STATE_KEY_PREFIX + state;
        String storedPlatform = stringRedisTemplate.opsForValue().get(redisKey);

        if (storedPlatform == null) {
            log.warn("State校验失败或已超时, state: {}", state);
            throw new CommonException("登录已超时，请重试");
        }

        if (!storedPlatform.equals(expectedPlatform)) {
            log.warn("State平台不匹配! 期望平台: {}, 实际平台: {}. 可能存在CSRF攻击风险。", expectedPlatform, storedPlatform);
            stringRedisTemplate.delete(redisKey); // 不管怎样都删除
            throw new CommonException("无效的登录请求");
        }
        
        // 校验成功后立即删除，防止重放攻击
        stringRedisTemplate.delete(redisKey);
        log.info("State校验成功, state: {}", state);
    }
    
    private String generateAndStoreTicket(UserTokenDTO tokenDTO) throws JsonProcessingException {
        String ticket = UUID.randomUUID().toString();
        String redisKey = CacheConstants.OAUTH_TICKET_KEY_PREFIX + ticket;
        String jsonValue = objectMapper.writeValueAsString(tokenDTO);

        // 将 ticket->tokenDTO 存入 Redis，有效期2分钟
        stringRedisTemplate.opsForValue().set(redisKey, jsonValue, Duration.ofMinutes(2));
        return ticket;
    }
}