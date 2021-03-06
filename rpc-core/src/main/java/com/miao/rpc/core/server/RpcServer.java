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
    private String serviceBasePackage; // 实现类路径
    private ServiceRegistry registry;
    private ApplicationContext applicationContext;

    public RpcServer(String serviceBasePackage, ServiceRegistry registry) {
        this.serviceBasePackage = serviceBasePackage;
        this.registry = registry;
    }

    public void initHandler() {
        log.info("serviceBasePackage:{}", serviceBasePackage);
        // 利用Spring提供的扫描工具，到指定路径下扫描被@RpcService注解的类
        // 存储 接口名 -> 接口实现类对象 的映射关系
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
                    // 对应的是tcp/ip协议listen函数中的backlog参数，
                    // 函数listen(int socketfd,int backlog)用来初始化服务端可连接队列，
                    // 服务端处理客户端连接请求是顺序处理的，所以同一时间只能处理一个客户端连接，
                    // 多个客户端来的时候，服务端将不能处理的客户端连接请求放在队列中等待处理，backlog参数指定了队列的大小
                    // 默认值，Windows为200，其他为128。
                    .option(ChannelOption.SO_BACKLOG, 128)
                    // Socket参数，TCP数据发送缓冲区大小。该缓冲区即TCP发送滑动窗口，
                    // linux操作系统可使用命令：cat /proc/sys/net/ipv4/tcp_smem查询其大小。
                    .option(ChannelOption.SO_SNDBUF, 32*1024)
                    // Socket参数，TCP数据接收缓冲区大小。该缓冲区即TCP接收滑动窗口，
                    // linux操作系统可使用命令：cat /proc/sys/net/ipv4/tcp_rmem查询其大小。
                    // 一般情况下，该值可由用户在任意时刻设置，但当设置值超过64KB时，需要在连接到远端之前设置。
                    .option(ChannelOption.SO_RCVBUF, 32*1024);
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
