package com.emakon.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée de l'API. {@code @SpringBootApplication} active l'auto-configuration
 * et le scan des composants dans ce package et ses sous-packages.
 */
@SpringBootApplication
public class ReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationApplication.class, args);
    }
}
