package com.shutu.devSphere.websocket.Handler;

import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.shutu.devSphere.model.enums.ws.WSReqTypeEnum;
import com.shutu.devSphere.model.vo.ws.request.WSBaseReq;
import com.shutu.devSphere.websocket.service.WebSocketService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WebSocketServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {


    /**
     * 客户端连接
     * @param ctx
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        getService().connect(channel);
    }


    /**
     * 客户端断开
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
     * @param channelHandlerContext
     * @param textWebSocketFrame
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, TextWebSocketFrame textWebSocketFrame){
        String text = textWebSocketFrame.text();
        // 将 text 文本信息转为 java对象
        WSBaseReq wsBaseRequest = JSONUtil.toBean(text, WSBaseReq.class);
        WSReqTypeEnum wsReqTypeEnum = WSReqTypeEnum.of(wsBaseRequest.getType());
        switch (wsReqTypeEnum) {
            case CHAT:
                getService().sendMessage(channelHandlerContext.channel(), wsBaseRequest);
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
