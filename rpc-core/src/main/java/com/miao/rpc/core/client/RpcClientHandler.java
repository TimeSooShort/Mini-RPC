package com.miao.rpc.core.client;

import com.miao.rpc.core.domain.Message;
import com.miao.rpc.core.domain.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import javafx.event.EventType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class RpcClientHandler extends SimpleChannelInboundHandler<Message> {
    private RpcClient client;
    private Map<String, RpcResponseFuture> map;

    public RpcClientHandler(RpcClient client, Map<String, RpcResponseFuture> map) {
        this.client = client;
        this.map = map;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        log.info("接收到服务器响应");
        if (msg.getType() == Message.PONG) {
            log.info("服务器正常");
        }else if (msg.getType() == Message.RESPONSE) {
            RpcResponse response = msg.getResponse();
            if (map.containsKey(response.getRequestId())) {
                RpcResponseFuture responseFuture = map.remove(response.getRequestId());
                responseFuture.setResponse(response);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.info("客户端捕获异常");
        // 在这里处理异常，不会继续往下传递
        // 异常处理调用clinet的handleException，由于默认策略
        cause.printStackTrace();
        client.handleException();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 说明NioSocketChannel注册完成，在register0中最后调用
        // pipeline.fireChannelActive()，给channel注册READ事件，并触发
        // handler链上handler的channelActive方法
        log.info("channelActive被触发");
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.WRITER_IDLE) {
                log.info("超过指定时间未发送数据，现主动发送一个心跳包");
                ctx.writeAndFlush(Message.PING_MSG);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
