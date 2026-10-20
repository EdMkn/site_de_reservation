package com.emakon.reservation.service;

import com.emakon.reservation.availability.AvailabilityCalculator;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans dont la couche service a besoin.
 *
 * AvailabilityCalculator n'a aucune annotation Spring (le package availability reste en
 * Java pur) : c'est ici qu'on le déclare comme bean, avec l'horloge et le fuseau.
 */
@Configuration
@EnableConfigurationProperties(SessionProperties.class)
public class ServiceConfig {

    /** Horloge système. Les tests la remplacent par une horloge figée. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId practiceZone(@Value("${reservation.timezone}") String timezone) {
        return ZoneId.of(timezone);
    }

    @Bean
    AvailabilityCalculator availabilityCalculator(Clock clock, ZoneId practiceZone) {
        return new AvailabilityCalculator(clock, practiceZone);
    }
}
