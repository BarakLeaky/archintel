package io.archintel.config;

import io.archintel.discovery.NewnessClassifier;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean NewnessClassifier newnessClassifier(Clock clock,
            @Value("${archintel.newness.new-days}") int newDays,
            @Value("${archintel.newness.recent-days}") int recentDays) {
        return new NewnessClassifier(clock, newDays, recentDays);
    }
}
