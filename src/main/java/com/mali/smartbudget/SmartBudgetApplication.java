package com.mali.smartbudget;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartBudgetApplication {
    public static void main(String[] args) {
        // .env dosyasını proje kök dizininden yükle ve sistem özelliklerine aktar
        // ignoreIfMissing() → üretim ortamında .env olmasa da uygulama çalışır
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));

        SpringApplication.run(SmartBudgetApplication.class, args);
    }
}
