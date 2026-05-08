package com.mali.smartbudget;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartBudgetApplication {
    public static void main(String[] args) {
        // .env dosyasını önce çalışma dizininde, bulamazsa üst dizinde ara.
        // ignoreIfMissing() → üretim ortamında .env olmasa da uygulama çalışır.
        String envDir = new java.io.File(".env").exists() ? "." : "../";
        Dotenv dotenv = Dotenv.configure().directory(envDir).ignoreIfMissing().load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));

        SpringApplication.run(SmartBudgetApplication.class, args);
    }
}
