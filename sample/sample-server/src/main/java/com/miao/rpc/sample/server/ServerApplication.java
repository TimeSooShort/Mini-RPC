package com.miao.rpc.sample.server;

import com.miao.rpc.core.server.RpcServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class ServerApplication implements CommandLineRunner{

    @Autowired
    private RpcServer server;

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ServerApplication.class);
        application.setWebEnvironment(false);
        application.run(args);
    }

    @Override
    public void run(String... strings) throws Exception {
        server.run(strings[0]);
    }
}
