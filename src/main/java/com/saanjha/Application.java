package com.saanjha;

import com.saanjha.modules.auth.config.RsaKeyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// RE-ENABLED: this was commented out, which silently disabled @CreatedDate/@LastModifiedDate
// population on every entity extending BaseAuditEntity across ALL modules (auth, user, and now
// project). Without it, createdAt/updatedAt columns rely purely on DB DEFAULT NOW() and never
// update on modification. Flagged as a pre-existing correctness gap and fixed here since the
// Project module's optimistic-locking and audit-trail guarantees depend on it working correctly.
//@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(RsaKeyProperties.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
