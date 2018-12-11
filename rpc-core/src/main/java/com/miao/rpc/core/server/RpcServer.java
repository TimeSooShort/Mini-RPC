package com.miao.rpc.core.server;

import com.miao.rpc.core.annotation.RpcService;
import com.miao.rpc.core.coder.RpcDecoder;
import com.miao.rpc.core.coder.RpcEncoder;
import com.miao.rpc.core.registry.ServiceRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AspectJTypeFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.INITIAL_BYTES_TO_STRIP;
import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.LENGTH_ADJUSTMENT;
import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.LENGTH_FIELD_LENGTH;
import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.LENGTH_FIELD_OFFSET;
import static com.miao.rpc.core.constant.Constant.LengthFieldConstant.MAX_FRAME_LENGTH;

@Slf4j
public class RpcServer implements ApplicationContextAware{

    private Map<String, Object> handlerMap = new HashMap<>(); // 接口名到服务对象之间的映射关系
    private String serviceBasePackage;
    private ServiceRegistry registry;
    private ApplicationContext applicationContext;

    public RpcServer(String serviceBasePackage, ServiceRegistry registry) {
        this.serviceBasePackage = serviceBasePackage;
        this.registry = registry;
    }

    public void initHandler() {
        log.info("serviceBasePackage:{}", serviceBasePackage);
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RpcService.class));
        Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents(serviceBasePackage);
        beanDefinitions.forEach(beanDefinition -> {
            try {
                log.info("扫描到:{}", beanDefinition);
                String beanClassName = beanDefinition.getBeanClassName();
                Class<?> beanClass = Class.forName(beanClassName);
                Class<?>[] interfaces = beanClass.getInterfaces();
                if (interfaces.length >= 1) {
                    this.handlerMap.put(interfaces[0].getName(), applicationContext.getBean(beanClass));
                }
            } catch (ClassNotFoundException e) {
                log.error("", e);
            }
        });
        log.info("handlerMap: {}", this.handlerMap);
    }

    public void run(String serverAddress) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline()
                                    .addLast("IdleStateHandler", new IdleStateHandler(10, 0, 0))
                                    .addLast("LengthFieldPrepender", new LengthFieldPrepender(4, 0))
                                    .addLast("RpcEncoder", new RpcEncoder())
                                    .addLast("LengthFieldBasedFrameDecoder", new LengthFieldBasedFrameDecoder(
                                            MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH,
                                            LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP))
                                    .addLast("RpcDecoder", new RpcDecoder())
                                    .addLast("RpcServerHandler", new RpcServerHandler(handlerMap));
                        }
                    })
                    // 俩个TCP维护的队列的总大小
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_SNDBUF, 32*1024)
                    .option(ChannelOption.SO_RCVBUF, 32*1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
             String[] address = serverAddress.split(":");
             String host = address[0];
             Integer port = Integer.parseInt(address[1]);
            ChannelFuture future = bootstrap.bind(host, port).sync();
            log.info("服务器启动");
            registry.registry(serverAddress);
            log.info("服务器向Zookeeper注册完毕");
            initHandler();
            // 应用程序一直等待直到channel关闭
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            registry.close();
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        this.applicationContext = applicationContext;
    }
}
