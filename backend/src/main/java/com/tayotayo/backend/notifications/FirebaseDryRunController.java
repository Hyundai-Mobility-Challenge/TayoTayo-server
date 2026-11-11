package com.tayotayo.backend.notifications;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.tayotayo.backend.auth.AccessTokenStore;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dev/fcm")
@ConditionalOnProperty(prefix = "app.firebase", name = "test-endpoint-enabled", havingValue = "true")
public class FirebaseDryRunController {
    private final AccessTokenStore tokens;
    private final ObjectProvider<FirebaseMessaging> firebaseProvider;

    public FirebaseDryRunController(AccessTokenStore tokens, ObjectProvider<FirebaseMessaging> firebaseProvider) {
        this.tokens = tokens;
        this.firebaseProvider = firebaseProvider;
    }

    @PostMapping("/dry-run")
    public Map<String, Object> dryRun(@RequestHeader(name = "Authorization", required = false) String authorization)
            throws Exception {
        tokens.requireUser(authorization);
        FirebaseMessaging firebase = firebaseProvider.getIfAvailable();
        if (firebase == null) throw new IllegalStateException("Firebase is disabled. Enable FIREBASE_ENABLED first.");

        Message message = Message.builder()
                .setTopic("tayotayo-dry-run")
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle("TayoTayo 연결 확인")
                        .setBody("드라이런 검증용 메시지입니다. 실제 기기로 전송되지 않습니다.")
                        .build())
                .build();
        String messageId = firebase.send(message, true);
        return Map.of("status", "VALIDATED", "dryRun", true, "messageId", messageId);
    }
}
