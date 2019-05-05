package com.miao.rpc.core.client;

import com.miao.rpc.core.domain.Message;
import com.miao.rpc.core.domain.RpcRequest;
import com.miao.rpc.core.domain.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import javafx.event.EventType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class RpcClientHandler extends SimpleChannelInboundHandler<Message> {
    private RpcClient client;
    private Map<String, RpcResponseFuture> requestWithItsResFuture;
    // 请求解析出现异常后，被exceptionCaught捕获，最多允许重新请求3次，超过后重新进行连接
    private ConcurrentHashMap<String, AtomicInteger> countOneRequestException = new ConcurrentHashMap<>();

    public RpcClientHandler(RpcClient client, Map<String, RpcResponseFuture> map) {
        this.client = client;
        this.requestWithItsResFuture = map;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        log.info("接收到服务器响应");
        if (msg.getType() == Message.PONG) {
            log.info("服务器正常");
        }else if (msg.getType() == Message.RESPONSE) {
            RpcResponse response = msg.getResponse();
            if (requestWithItsResFuture.containsKey(response.getRequestId())) {
                RpcResponseFuture responseFuture = requestWithItsResFuture.remove(response.getRequestId());
                responseFuture.setResponse(response);

                countOneRequestException.remove(ctx.channel().attr(RpcClient.CURRENT_REQUEST).get().getRequestId());
                ctx.channel().attr(RpcClient.CURRENT_REQUEST).set(null); // 清除本次请求存储的RpcRequest对象
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 在这里处理异常，不会继续往下传递
        // 异常处理调用clinet的reExecute，重新发送这次请求
        String id = ctx.channel().attr(RpcClient.CURRENT_REQUEST).get().getRequestId();
        log.info("捕获 " + id + " 异常");
        log.info("异常信息："+ cause.toString());
        if (countOneRequestException.get(id) == null) {
            countOneRequestException.put(id, new AtomicInteger(1));
        }
        int count = countOneRequestException.get(id).getAndAdd(1);
        if ( count < 3) {
            log.info(id + " 第 " + count + " 次尝试重新发出请求");
            log.info(countOneRequestException.toString());
            client.reExecute(ctx.channel().attr(RpcClient.CURRENT_REQUEST).get());
        }else {
            log.info(id + " 已重新请求过2次，仍然出现异常，尝试重新连接来解决问题");
            countOneRequestException.remove(id); // 删除本次请求异常次数的记录
            // 请求线程正在阻塞等待结果，需要唤醒
            RpcResponse response = new RpcResponse();
            response.setCause(new RuntimeException("由于本次请求重试次数超过限制，客户端重新与服务器建立连接"));
            // 删除 本次请求ID->RpcResponseFuture 映射关系，唤醒请求线程
            requestWithItsResFuture.remove(id).setResponse(response);
            client.handleException(); // 重新连接
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("客户端通道已开启");
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
