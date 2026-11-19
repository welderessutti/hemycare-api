package com.hemycare.api;

import org.springframework.boot.SpringApplication;

public class TestHemycareApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(HemycareApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
