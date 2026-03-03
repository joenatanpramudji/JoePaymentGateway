package com.joe.paymentgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the JoePaymentGateway application.
 *
 * <p>This application simulates a payment gateway that processes ISO 8583
 * financial transactions (sale, void, pre-auth) over a REST API with
 * AES-DUKPT encryption for PIN block security.</p>
 *
 * <p>Supports multiple environments via Spring profiles: local, dev, prod.</p>
 */
@SpringBootApplication
public class JoePaymentGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(JoePaymentGatewayApplication.class, args);
    }
}
