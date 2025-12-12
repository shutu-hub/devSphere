package com.shutu.websocket.Handler;

import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.shutu.model.enums.ws.WSReqTypeEnum;
import com.shutu.model.vo.ws.request.WSBaseReq;
import com.shutu.websocket.service.WebSocketService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class WebSocketServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    /**
     * 客户端连接
     * 当 WebSocket 握手成功，Netty 会回调 handlerAdded
     * 
     * @param ctx
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty 发生异常，连接即将关闭: {}", ctx.channel().id(), cause);
        ctx.close();
    }

    /**
     * 读空闲
     * 
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // 握手成功，此时 AuthHandler 肯定已经执行过并设置了 userId
            getService().connect(ctx.channel());
        }
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.info("连接读空闲超时，关闭连接: {}", ctx.channel().id());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 客户端断开
     * 
     * @param ctx
     * @throws Exception
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        getService().removed(channel);
    }

    /**
     * 读取客户端报文
     * 
     * @param channelHandlerContext
     * @param textWebSocketFrame
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, TextWebSocketFrame textWebSocketFrame) {
        String text = textWebSocketFrame.text();
        // 将 text 文本信息转为 java对象
        WSBaseReq wsBaseRequest = JSONUtil.toBean(text, WSBaseReq.class);
        WSReqTypeEnum wsReqTypeEnum = WSReqTypeEnum.of(wsBaseRequest.getType());
        switch (wsReqTypeEnum) {
            case CHAT:
                getService().sendMessage(channelHandlerContext.channel(), wsBaseRequest);
                break;
            case RTC_SIGNAL:
                getService().handleRtcSignal(channelHandlerContext.channel(), wsBaseRequest);
                break;
            case HEARTBEAT:
                break;
            default:
                break;
        }
    }

    private WebSocketService getService() {
        return SpringUtil.getBean(WebSocketService.class);
    }
}
