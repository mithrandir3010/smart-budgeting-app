package com.mali.smartbudget;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Bütçe Uygulamasına Hoş Geldin Mehmet Ali!";
    }
}
