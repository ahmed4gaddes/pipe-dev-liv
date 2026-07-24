package com.pipedevliv.pipeline;

import com.pipedevliv.pipeline.config.FeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
public class PipelineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PipelineServiceApplication.class, args);
    }
}
