package com.example.mcfriends;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients; // НОВАЯ АННОТАЦИЯ

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class McFriendsApplication {

    public static void main(String[] args) {
        SpringApplication.run(McFriendsApplication.class, args);
    }

}