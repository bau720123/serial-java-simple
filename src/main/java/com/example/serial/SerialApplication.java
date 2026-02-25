package com.example.serial;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class SerialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SerialApplication.class, args);
    }

    /**
     * 設定應用程式預設時區為台北時間
     * 對應 Laravel config/app.php 的 'timezone' => 'Asia/Taipei'
     */
    @PostConstruct
    void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"));
    }
}
