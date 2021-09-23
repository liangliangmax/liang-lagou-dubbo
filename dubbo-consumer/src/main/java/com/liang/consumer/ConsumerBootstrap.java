package com.liang.consumer;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableDubbo
@SpringBootApplication
public class ConsumerBootstrap {


    public static void main(String[] args) {
        SpringApplication.run(ConsumerBootstrap.class,args);
    }

}
