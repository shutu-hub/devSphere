package com.shutu.websocket;

import com.shutu.websocket.Handler.AuthHandler;
import com.shutu.websocket.Handler.WebSocketServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class NettyServer {

    private static final int PORT = 9000;
    // 创建线程执行器
    private static final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private static final EventLoopGroup workerGroup = new NioEventLoopGroup(8);
    private final AuthHandler authHandler;

    /**
     * 启动
     * @throws InterruptedException
     */
    @PostConstruct
    private void start() throws InterruptedException {
        new Thread(() -> {
            try {
                startServer();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 创建 netty 服务端
     */
    private void startServer() throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup,workerGroup)
                .channel(NioServerSocketChannel.class) // 指定使用 NIO 非阻塞传输模型
                .option(ChannelOption.SO_BACKLOG,128) // 设置 TCP 半连接队列大小
                .childOption(ChannelOption.SO_KEEPALIVE,true) // 开启 TCP 底层心跳机制
                .handler(new LoggingHandler(LogLevel.INFO)) // 在 Boss 线程组添加日志处理器
                .childHandler(new ChannelInitializer<SocketChannel>() { // 配置 Worker 线程组的处理器管道
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast(new LoggingHandler("DEBUG_LOGGER", LogLevel.INFO));
                        // 如果 50 秒内没有收到客户端的任何数据（读空闲），会触发一个 IdleStateEvent 事件。
                        pipeline.addLast(new IdleStateHandler(50,0,0));
                        pipeline.addLast(new HttpServerCodec());
                        // 支持异步发送大数据流
                        pipeline.addLast(new ChunkedWriteHandler());
                        // HTTP 消息聚合器，Netty 默认会将一个 HTTP 请求拆分成多个小片段（HttpRequest, HttpContent, LastHttpContent）。
                        // 这个 Handler 会把它们合并成一个完整的 FullHttpRequest 对象，方便后续处理。
                        pipeline.addLast(new HttpObjectAggregator(8192));
                        //websocket
                        pipeline.addLast(authHandler);
                        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
                        pipeline.addLast(new WebSocketServerHandler());
                    }
                });
            serverBootstrap.bind(PORT).sync();
        log.info("Netty启动成功");
    }

    /**
     * 销毁
     */
    @PreDestroy
    public void destroy() {
        Future<?> future = bossGroup.shutdownGracefully();
        Future<?> future1 = workerGroup.shutdownGracefully();
        future.syncUninterruptibly();
        future1.syncUninterruptibly();
        log.info("关闭 ws server 成功");
    }
}
