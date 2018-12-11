package com.miao.rpc.core.server;

import com.miao.rpc.core.domain.Message;
import com.miao.rpc.core.domain.RpcRequest;
import com.miao.rpc.core.domain.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.reflect.FastClass;
import org.springframework.cglib.reflect.FastMethod;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class Worker implements Runnable {

    private ChannelHandlerContext ctx;
    private RpcRequest request;
    private Map<String, Object> handlerMap;

    @Override
    public void run() {
        RpcResponse response = new RpcResponse();
        response.setRequestId(request.getRequestId());
        try {
            Object result = handle(request);
            response.setResult(result);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            response.setCause(e);
        }
        log.info("服务器返回结果为：{}", response);
        // 由于本线程并非该channel所属的EventLoop的线程，
        // 会调用该EventLoop的execute方法，最终让其唯一的那个线程来处理
        // 关于ChannelHandlerContext的write与writeAndFlush都会从该handler开始
        // 往前找到最近的outHandler，调用其write与writeAndFlush
        ctx.writeAndFlush(Message.buildResponse(response));
    }

    /**
     * CGLib提供的反射API来调用方法
     * @param request
     * @return
     * @throws InvocationTargetException
     */
    private Object handle(RpcRequest request) throws InvocationTargetException {
        String className = request.getClassName();
        Object serviceBean = this.handlerMap.get(className);

        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameter = request.getParameters();

        FastClass serviceFastClass = FastClass.create(serviceBean.getClass());
        FastMethod serviceFastMethod = serviceFastClass.getMethod(methodName, parameterTypes);
        return serviceFastMethod.invoke(serviceBean, parameter);
    }
}
