package com.shutu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@MapperScan("com.shutu.mapper")
@EnableFeignClients(basePackages = { "com.shutu.service", "com.shutu.feign" })
@Import(cn.hutool.extra.spring.SpringUtil.class)
@SpringBootApplication(scanBasePackages = "com.shutu")
public class ChatApplication {

    public static void main(String[] args)   {
        SpringApplication.run(ChatApplication.class, args);
    }

}
