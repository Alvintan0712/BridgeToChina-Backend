package com.btchina.question;

import com.btchina.common.mybatis.config.EnableMybatisPlus;
import com.btchina.common.swagger.config.EnableSwagger;
import com.btchina.feign.config.DefaultFeignConfiguration;
import com.btchina.redis.config.EnableRedisSerialize;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@EnableMybatisPlus
@EnableSwagger
@EnableRedisSerialize
@SpringBootApplication
@EnableFeignClients(basePackages = {"com.btchina.feign.clients"}, defaultConfiguration = DefaultFeignConfiguration.class)
public class QuestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuestionServiceApplication.class, args);
    }

}