package com.shutu.devSphere.websocket.Handler;

import cn.hutool.extra.spring.SpringUtil;
import com.shutu.commons.security.cache.TokenStoreCache;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.exception.UnauthorizedException;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

/**
 * 认证 Handler
 * 必须放在 {@link WebSocketServerProtocolHandler} 之前
 */
@ChannelHandler.Sharable
public class AuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);
    private static final String WEBSOCKET_PATH = "/ws";
    private static final String TOKEN_PARAM = "accessToken";
    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");

    public TokenStoreCache getTokenStoreCache() {
        return SpringUtil.getBean(TokenStoreCache.class);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (isWebSocketHandshake(request)) {
            try {
                handleWebSocketHandshake(ctx, request);
            } catch (Exception e) {
                log.error("WebSocket握手请求处理异常", e);
                sendHttpResponseAndClose(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
            }
        } else {
            sendHttpResponseAndClose(ctx, HttpResponseStatus.FORBIDDEN, "此接口仅支持WebSocket请求");
        }
    }

    private boolean isWebSocketHandshake(FullHttpRequest request) {
        return request.method() == HttpMethod.GET
                && request.uri().startsWith(WEBSOCKET_PATH)
                && "websocket".equalsIgnoreCase(request.headers().get(HttpHeaderNames.UPGRADE));
    }

    private void handleWebSocketHandshake(ChannelHandlerContext ctx, FullHttpRequest request) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        String token = Optional.ofNullable(decoder.parameters().get(TOKEN_PARAM))
                .map(params -> params.get(0))
                .orElse(null);

        if (token == null || token.isEmpty()) {
            log.warn("握手失败：客户端 {} 的连接请求中缺少Token。", ctx.channel().remoteAddress());
            sendHttpResponseAndClose(ctx, HttpResponseStatus.UNAUTHORIZED, "缺少认证凭证");
            return;
        }

        try {
            UserDetail user = getTokenStoreCache().getUser(token);
            log.info("用户 {} 的WebSocket连接认证成功。", user.getId());

            ctx.channel().attr(USER_ID_KEY).set(user.getId());
            request.setUri(WEBSOCKET_PATH);
            ctx.fireChannelRead(request.retain());

        } catch (UnauthorizedException e) {
            log.warn("握手失败：客户端 {} 的Token已过期。", ctx.channel().remoteAddress());
            sendHttpResponseAndClose(ctx, HttpResponseStatus.UNAUTHORIZED, "凭证已过期");
        } catch (Exception e) {
            log.error("握手失败：客户端 {} 的Token校验时发生内部错误。", ctx.channel().remoteAddress(), e);
            sendHttpResponseAndClose(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "凭证校验失败");
        }
    }
    
    private void sendHttpResponseAndClose(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.content().writeBytes(message.getBytes());
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("AuthHandler捕获到未处理异常，Channel ID: {}，异常信息: {}", ctx.channel().id(), cause.getMessage(), cause);
        ctx.close();
    }
}