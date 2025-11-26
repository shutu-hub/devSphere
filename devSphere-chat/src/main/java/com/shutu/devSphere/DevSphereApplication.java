package com.shutu.devSphere;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@MapperScan("com.shutu.devSphere.mapper")
@EnableFeignClients(basePackages = { "com.shutu.devSphere.service", "com.shutu.feign" })
@Import(cn.hutool.extra.spring.SpringUtil.class)
@SpringBootApplication(scanBasePackages = "com.shutu")
public class DevSphereApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevSphereApplication.class, args);
    }

}
