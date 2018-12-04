package com.miao.rpc.core.server;

import com.miao.rpc.core.domain.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RpcServerHandler extends SimpleChannelInboundHandler<Message> {

    private Map<String, Object> handlerMap;
    private ThreadPoolExecutor pool;

    public RpcServerHandler(Map<String, Object> handlerMap) {
        this.handlerMap = handlerMap;
        int threads = Runtime.getRuntime().availableProcessors();
        pool = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy());
        log.info("handlerMap : {}", handlerMap);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext,
                                Message message) throws Exception {
        byte type = message.getType();
        log.info("服务器已接收到请求:{}, 请求类型：{}", message, type);
        if (type == Message.PING) {
            log.info("接收到客户端的PING心跳请求，发送PONG心跳反应");
            channelHandlerContext.writeAndFlush(Message.PONG_MSG);
        } else if (type == Message.REQUEST) {

        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        log.info("接收到客户端的连接");
    }

    /**
     * 打印异常，关闭连接
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            cause.printStackTrace();
        } finally {
            ctx.close();
        }
    }

    /**
     * 超时关闭连接
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.info("在规定时间内未接收到客户端的消息， 关闭连接");
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
