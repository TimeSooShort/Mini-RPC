package com.miao.rpc.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private byte type;
    private RpcRequest request;
    private RpcResponse response;

    public Message(byte type) {
        this.type = type;
    }

    public static Message buildRequest(RpcRequest request) {
        return new Message(Message.REQUEST, request, null);
    }

    public static Message buildResponse(RpcResponse response) {
        return new Message(Message.RESPONSE, null, response);
    }

    public static final byte PING = 1;
    public static final byte PONG = 1 << 1;
    public static final byte REQUEST = 1 << 2;
    public static final byte RESPONSE = 1 << 3;
    public static final Message PING_MSG = new Message(Message.PING);
    public static final Message PONG_MSG = new Message(Message.PONG);
}
