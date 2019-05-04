package com.miao.rpc.core.coder;

import com.miao.rpc.core.domain.Message;
import com.miao.rpc.core.domain.RpcRequest;
import com.miao.rpc.core.domain.RpcResponse;
import com.miao.rpc.core.util.ProtostuffUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class RpcDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext,
                          ByteBuf byteBuf, List<Object> list) throws Exception {
        byte type = byteBuf.readByte();
        log.info("解码消息，消息类型为: {}", type);
        if (type == Message.PING) {
            list.add(Message.PING_MSG);
        } else if (type == Message.PONG) {
            list.add(Message.PONG_MSG);
        } else {
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            // core包是被客户端与服务端两者引用的,所以这里同时有对REQUEST,RESPONSE二者的处理
            if (type == Message.REQUEST) {
                list.add(Message.buildRequest(ProtostuffUtil.deserialize(bytes, RpcRequest.class)));
            } else if (type == Message.RESPONSE) {
                list.add(Message.buildResponse(ProtostuffUtil.deserialize(bytes, RpcResponse.class)));
            }
        }
    }
}
