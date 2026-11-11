package com.tayotayo.backend.notifications;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
public class FirebaseConfig {
    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
    FirebaseMessaging firebaseMessaging(
            @Value("${app.firebase.credentials-path:}") String credentialsPath,
            @Value("${app.firebase.project-id:}") String projectId) throws Exception {
        Path keyFile = Path.of(credentialsPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(keyFile)) {
            throw new IllegalStateException("Firebase service account file was not found: " + keyFile);
        }
        try (FileInputStream input = new FileInputStream(keyFile.toFile())) {
            FirebaseOptions.Builder options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(input));
            if (projectId != null && !projectId.isBlank()) options.setProjectId(projectId);
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(existing -> existing.getName().equals(FirebaseApp.DEFAULT_APP_NAME))
                    .findFirst().orElseGet(() -> FirebaseApp.initializeApp(options.build()));
            log.info("Firebase Admin SDK initialized for project {}.", app.getOptions().getProjectId());
            return FirebaseMessaging.getInstance(app);
        }
    }
}
