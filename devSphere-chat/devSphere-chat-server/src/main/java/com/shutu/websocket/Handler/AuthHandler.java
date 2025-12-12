package com.shutu.websocket.Handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.shutu.commons.security.cache.TokenStoreCache;
import com.shutu.commons.security.user.UserDetail;
import com.shutu.commons.tools.exception.UnauthorizedException;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ChannelHandler.Sharable
public class AuthHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String WEBSOCKET_PATH = "/ws";
    private static final String TOKEN_PARAM = "accessToken";
    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");

    public TokenStoreCache getTokenStoreCache() {
        return SpringUtil.getBean(TokenStoreCache.class);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (isWebSocketHandshake(request)) {
            handleWebSocketHandshake(ctx, request);
        } else {
            log.warn("非WS握手请求: {} {}, Client: {}", request.method(), request.uri(), ctx.channel().remoteAddress());
            sendHttpResponseAndClose(ctx, HttpResponseStatus.FORBIDDEN, "仅支持WebSocket连接");
        }
    }

    private boolean isWebSocketHandshake(FullHttpRequest request) {
        // 只要 URI 以 /ws 开头即可，后面可以带参数
        return request.method() == HttpMethod.GET
                && request.uri().startsWith(WEBSOCKET_PATH)
                && request.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true);
    }

    private void handleWebSocketHandshake(ChannelHandlerContext ctx, FullHttpRequest request) {
        // 1. 从 URL 获取 Token
        String token = getTokenFromUrl(request.uri());

        if (StrUtil.isBlank(token)) {
            log.warn("鉴权失败: URL中未找到accessToken, Client: {}", ctx.channel().remoteAddress());
            sendHttpResponseAndClose(ctx, HttpResponseStatus.UNAUTHORIZED, "请在URL中携带accessToken");
            return;
        }

        try {
            // 2. 验证 Token
            UserDetail user = getTokenStoreCache().getUser(token);
            if (user == null) {
                throw new UnauthorizedException("Token无效或已过期");
            }

            // 3. 绑定用户ID到 Channel
            ctx.channel().attr(USER_ID_KEY).set(user.getId());
            log.info("WS用户上线: {} (Remote: {})", user.getId(), ctx.channel().remoteAddress());

            // 4. 【关键】重写 URI，去掉查询参数
            // 否则后续的 WebSocketServerProtocolHandler 可能因为精确匹配 "/ws" 而失败
            request.setUri(WEBSOCKET_PATH);

            // 5. 传递给下一个 Handler 完成标准握手
            ctx.fireChannelRead(request.retain());

        } catch (Exception e) {
            log.error("WS鉴权异常: {}, Client: {}", e.getMessage(), ctx.channel().remoteAddress());
            sendHttpResponseAndClose(ctx, HttpResponseStatus.UNAUTHORIZED, "认证失败");
        }
    }

    private String getTokenFromUrl(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        List<String> params = decoder.parameters().get(TOKEN_PARAM);
        if (params != null && !params.isEmpty()) {
            return params.get(0);
        }
        return null;
    }

    private void sendHttpResponseAndClose(ChannelHandlerContext ctx, HttpResponseStatus status, String msg) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        if (msg != null) {
            response.content().writeBytes(msg.getBytes(io.netty.util.CharsetUtil.UTF_8));
        }
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}