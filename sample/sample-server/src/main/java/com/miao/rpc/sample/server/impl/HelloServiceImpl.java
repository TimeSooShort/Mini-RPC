package com.miao.rpc.sample.server.impl;

import com.miao.rpc.core.annotation.RpcService;
import com.miao.rpc.sample.api.domain.User;
import com.miao.rpc.sample.api.service.HelloService;
import org.springframework.stereotype.Service;

@RpcService
@Service
public class HelloServiceImpl implements HelloService {
    @Override
    public String hello(User user) {
        return "Hello! " + user.getUserName();
    }
}
