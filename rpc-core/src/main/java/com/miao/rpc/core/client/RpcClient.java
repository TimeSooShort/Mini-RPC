package com.miao.rpc.core.client;

import com.github.rholder.retry.*;
import com.miao.rpc.core.coder.RpcDecoder;
import com.miao.rpc.core.coder.RpcEncoder;
import com.miao.rpc.core.constant.Constant.ConnectionFailureStrategy;
import com.miao.rpc.core.domain.Message;
import com.miao.rpc.core.domain.RpcRequest;
import com.miao.rpc.core.registry.ServiceDiscovery;
import com.miao.rpc.core.registry.ServiceRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.rmi.server.ServerNotActiveException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.MAX_FRAME_LENGTH;
import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.LENGTH_FIELD_OFFSET;
import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.LENGTH_FIELD_LENGTH;
import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.LENGTH_ADJUSTMENT;
import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.INITIAL_BYTES_TO_STRIP;

@Slf4j
public class RpcClient {

    private ConnectionFailureStrategy connectionFailureStrategy = ConnectionFailureStrategy.RETRY; // 默认连接失败策略
    private ServiceDiscovery discovery;
    private String clientID = UUID.randomUUID().toString(); // 用于ConsistentHashLoadBalance
    private Bootstrap bootstrap;
    private Channel socketChannel;
    private EventLoopGroup group;
    // 每个request都有个requestId，其结果封装成RpcResponseFuture
    // 该map存储的就是这种关系，为了保证安全，选用ConcurrentHashMap
    private Map<String, RpcResponseFuture> requestWithItsResponse;
    public static final AttributeKey<RpcRequest> CURRENT_REQUEST = AttributeKey.valueOf("current.request");

    public void init() {
        log.info("初始化RPC客户端");
        this.group = new NioEventLoopGroup();
        this.requestWithItsResponse = new ConcurrentHashMap<>();
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast("IdleStateHandler", new IdleStateHandler(0, 7, 0))
                                .addLast("LengthFieldPrepender", new LengthFieldPrepender(LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT))
                                .addLast("RpcEncoder", new RpcEncoder())
                                .addLast("LengthFieldBasedFrameDecoder", new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET,
                                        LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP))
                                .addLast("RpcDecoder", new RpcDecoder())
                                .addLast("RpcClientHandler", new RpcClientHandler(RpcClient.this, requestWithItsResponse));
                    }
                });
        try {
            this.socketChannel = connect();
            log.info("客户端初始化完毕");
        } catch (ServerNotActiveException | InterruptedException e) {
            log.error("", e);
            handleException();
        }
    }

    /**
     * 连接失败或IO时失败均会调用此方法处理异常
     */
    public void handleException() {
        if (connectionFailureStrategy == ConnectionFailureStrategy.CLOSE) {
            log.info("连接失败的处理策略为直接关闭， 关闭客户端");
            this.close();
        } else if (connectionFailureStrategy == ConnectionFailureStrategy.RETRY) {
            log.info("开始重新连接");
            try {
                this.socketChannel = reconnect();
            } catch (ExecutionException e) {
                log.error("重试过程中抛出异常，关闭客户端", e);
                this.close();
            } catch (RetryException e) {
                log.error("重试次数达到上限， 关闭客户端", e);
                this.close();
            }
        }
    }

    /**
     * 关闭socketChannel， 关闭zookeeper，NioEventLoopGroup
     */
    public void close() {
        try {
            if (this.socketChannel != null) {
                socketChannel.close().sync();
            }
        } catch (InterruptedException e) {
            log.error("", e);
        } finally {
            this.discovery.close(); // 关闭zookeeper
            group.shutdownGracefully();
        }
    }

    /**
     * 实现重新连接的重试策略
     * @return
     */
    private Channel reconnect() throws ExecutionException, RetryException {
        Retryer<Channel> retryer = RetryerBuilder.<Channel>newBuilder()
                .retryIfExceptionOfType(Exception.class)
                .withWaitStrategy(WaitStrategies.incrementingWait(2,
                        TimeUnit.SECONDS, 1, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(2))
                .build();
        return retryer.call(() -> {
            log.info("重新连接中...");
            return connect();
        });
    }

    /**
     * RpcClientHandler 是客户端handler链中最后一个inbound handler，在其exceptionCaught对一场进行处理，
     * 发生异常便重新发送信息
     */
    public void reExecute(RpcRequest request) {
        log.info(request.getRequestId() + " 重新请求");
        this.socketChannel.writeAndFlush(Message.buildRequest(request));
    }

    private Channel connect() throws ServerNotActiveException, InterruptedException {
        log.info("向ZK查询服务器地址");
        String serverAddress = discovery.discover(clientID);
        log.info("本次连接的地址为：{}", serverAddress);
        if (serverAddress == null) {
            throw new ServerNotActiveException("无法获得服务器地址");
        }
        String[] address = serverAddress.split(":");
        String host = address[0];
        Integer port = Integer.parseInt(address[1]);
        ChannelFuture future = bootstrap.connect(host, port).sync();
        return future.channel();
    }

    /**
     * 客户端通过此方法发送请求
     * @param request
     * @return
     */
    public RpcResponseFuture execute(RpcRequest request) {
        // 这里为了处理重连发生时，请求仍在继续的情况
        while (this.socketChannel == null) {
            try {
                log.info("客户端连接未建立，等待连接完成");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        log.info("客户端发起请求:{}", request);
        RpcResponseFuture responseFuture = new RpcResponseFuture();
        this.requestWithItsResponse.put(request.getRequestId(), responseFuture);
        this.socketChannel.attr(CURRENT_REQUEST).set(request);
        this.socketChannel.writeAndFlush(Message.buildRequest(request));
        log.info("请求已发送");
        return responseFuture;
    }

    public void setDiscovery(ServiceDiscovery discovery) {
        this.discovery = discovery;
    }
}
