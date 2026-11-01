package br.com.lab.transactions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransactionLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionLabApplication.class, args);
    }
}
