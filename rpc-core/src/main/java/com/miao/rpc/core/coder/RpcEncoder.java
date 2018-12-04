package com.miao.rpc.core.coder;

import com.miao.rpc.core.domain.Message;
import com.miao.rpc.core.util.ProtostuffUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RpcEncoder extends MessageToByteEncoder {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext,
                          Object o, ByteBuf byteBuf) throws Exception {
        Message message = (Message) o;
        byte type = message.getType();
        byteBuf.writeByte(type);
        log.info("编码信息， 信息类型为: {}", type);
        // PING/PONG信息就传type过去就行了
        if (type == Message.REQUEST) {
            byteBuf.writeBytes(ProtostuffUtil.serialize(message.getRequest()));
        } else if (type == Message.RESPONSE) {
            byteBuf.writeBytes(ProtostuffUtil.serialize(message.getResponse()));
        }
    }
}
