package com.example.consensus;

import org.springframework.boot.SpringApplication;

public class TestConsensusApplication {

    public static void main(String[] args) {
        SpringApplication.from(ConsensusApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
