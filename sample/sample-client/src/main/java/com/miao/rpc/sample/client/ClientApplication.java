package com.miao.rpc.sample.client;

import com.miao.rpc.core.annotation.RpcReference;
import com.miao.rpc.sample.api.domain.User;
import com.miao.rpc.sample.api.service.HelloService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class ClientApplication implements CommandLineRunner{
    @Autowired
    @RpcReference
    HelloService helloService;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ClientApplication.class);
        app.setWebEnvironment(false);
        app.run(args);
    }

    public void test() throws InterruptedException {
        log.info(helloService.hello(new User("张大")));
        log.info(helloService.hello(new User("张二")));

        Thread.sleep(3000);
        log.info(helloService.hello(new User("张三")));
        Thread.sleep(8000);
        log.info(helloService.hello(new User("李四")));
    }

    @Override
    public void run(String... strings) throws Exception {
        test();
    }
}
