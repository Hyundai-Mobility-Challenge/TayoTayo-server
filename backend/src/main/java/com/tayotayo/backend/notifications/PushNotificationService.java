package com.tayotayo.backend.notifications;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Service
public class PushNotificationService {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private final JdbcTemplate jdbc;
    private final ObjectProvider<FirebaseMessaging> firebaseProvider;

    public PushNotificationService(JdbcTemplate jdbc, ObjectProvider<FirebaseMessaging> firebaseProvider) {
        this.jdbc = jdbc;
        this.firebaseProvider = firebaseProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sendPendingTripNotifications() {
        if (firebaseProvider.getIfAvailable() == null) return;
        jdbc.queryForList("SELECT id FROM notifications WHERE status='PENDING' AND notification_type IN ('ARRIVED','DROPPED_OFF') ORDER BY id")
                .forEach(row -> deliver(((Number) row.get("id")).longValue()));
    }

    public void notifyStopArrived(long tripId, long tripStopId) {
        jdbc.update("""
                INSERT INTO notifications(user_id,child_id,trip_id,notification_type,dedupe_key,title,body,data,status)
                SELECT DISTINCT cg.user_id,tc.child_id,tc.trip_id,'ARRIVED',
                       CONCAT('trip:',tc.trip_id,':child:',tc.child_id,':stop:',ts.id,':ARRIVED:',cg.user_id),
                       '셔틀 도착 안내',CONCAT(tc.child_name,'의 셔틀이 정류장에 도착했습니다.'),
                       JSON_OBJECT('type','ARRIVED','tripId',tc.trip_id,'childId',tc.child_id),'PENDING'
                FROM trip_children tc
                JOIN trip_stops ts ON ts.id=tc.trip_stop_id AND ts.trip_id=tc.trip_id
                JOIN child_guardians cg ON cg.child_id=tc.child_id AND cg.status='ACTIVE'
                WHERE tc.trip_id=? AND ts.id=? AND tc.boarding_expected=1
                  AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.dedupe_key=CONCAT('trip:',tc.trip_id,':child:',tc.child_id,':stop:',ts.id,':ARRIVED:',cg.user_id))
                """, tripId, tripStopId);
        List<Long> notificationIds = jdbc.queryForList(
                "SELECT id FROM notifications WHERE trip_id=? AND notification_type='ARRIVED' AND dedupe_key LIKE ?",
                Long.class, tripId, "trip:" + tripId + ":child:%:stop:" + tripStopId + ":ARRIVED:%");
        notificationIds.forEach(this::deliver);
    }

    public void notifyChildDroppedOff(long tripId, long tripChildId, long tripEventId) {
        jdbc.update("""
                INSERT INTO notifications(user_id,child_id,trip_id,trip_event_id,notification_type,dedupe_key,title,body,data,status)
                SELECT cg.user_id,tc.child_id,tc.trip_id,?,'DROPPED_OFF',
                       CONCAT('trip:',tc.trip_id,':event:',? ,':DROPPED_OFF:',cg.user_id),
                       '하차 안내',CONCAT(tc.child_name,'이(가) 셔틀에서 하차했습니다.'),
                       JSON_OBJECT('type','DROPPED_OFF','tripId',tc.trip_id,'childId',tc.child_id),'PENDING'
                FROM trip_children tc
                JOIN child_guardians cg ON cg.child_id=tc.child_id AND cg.status='ACTIVE'
                WHERE tc.trip_id=? AND tc.id=?
                  AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.dedupe_key=CONCAT('trip:',tc.trip_id,':event:',? ,':DROPPED_OFF:',cg.user_id))
                """, tripEventId, tripEventId, tripId, tripChildId, tripEventId);
        List<Long> notificationIds = jdbc.queryForList(
                "SELECT id FROM notifications WHERE trip_event_id=? AND notification_type='DROPPED_OFF'",
                Long.class, tripEventId);
        notificationIds.forEach(this::deliver);
    }

    private void deliver(long notificationId) {
        FirebaseMessaging firebase = firebaseProvider.getIfAvailable();
        if (firebase == null) return; // Leave as PENDING; it can be sent after credentials are configured.

        Map<String, Object> notification = jdbc.queryForMap(
                "SELECT user_id,child_id,trip_id,notification_type,title,body FROM notifications WHERE id=?",
                notificationId);
        long userId = ((Number) notification.get("user_id")).longValue();
        List<Map<String, Object>> devices = jdbc.queryForList(
                "SELECT id,fcm_token FROM push_devices WHERE user_id=? AND revoked_at IS NULL AND os_permission='GRANTED'",
                userId);
        if (devices.isEmpty()) {
            jdbc.update("UPDATE notifications SET status='SKIPPED',cancel_reason='NO_ACTIVE_PUSH_DEVICE' WHERE id=? AND status='PENDING'", notificationId);
            return;
        }

        String type = String.valueOf(notification.get("notification_type"));
        String tripId = String.valueOf(notification.get("trip_id"));
        String childId = String.valueOf(notification.get("child_id"));
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(notificationId));
        data.put("type", type);
        data.put("tripId", tripId);
        data.put("childId", childId);
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(devices.stream().map(device -> String.valueOf(device.get("fcm_token"))).toList())
                .setNotification(Notification.builder()
                        .setTitle(String.valueOf(notification.get("title")))
                        .setBody(String.valueOf(notification.get("body"))).build())
                .putAllData(data)
                .build();

        List<Long> deviceIds = devices.stream().map(device -> ((Number) device.get("id")).longValue()).toList();
        List<Long> deliveryIds = new ArrayList<>();
        for (long deviceId : deviceIds) {
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO notification_deliveries(notification_id,push_device_id,status) VALUES(?,?,'QUEUED')",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, notificationId);
                statement.setLong(2, deviceId);
                return statement;
            }, key);
            deliveryIds.add(key.getKey().longValue());
        }

        try {
            BatchResponse response = firebase.sendEachForMulticast(message);
            int succeeded = 0;
            for (int i = 0; i < response.getResponses().size(); i++) {
                SendResponse item = response.getResponses().get(i);
                if (item.isSuccessful()) {
                    succeeded++;
                    jdbc.update("UPDATE notification_deliveries SET status='SENT',fcm_message_id=?,attempted_at=CURRENT_TIMESTAMP(3) WHERE id=?",
                            item.getMessageId(), deliveryIds.get(i));
                } else {
                    MessagingErrorCode messagingError = item.getException() == null ? null : item.getException().getMessagingErrorCode();
                    String error = messagingError == null ? "UNKNOWN" : messagingError.name();
                    boolean invalid = messagingError == MessagingErrorCode.UNREGISTERED;
                    jdbc.update("UPDATE notification_deliveries SET status=?,error_code=?,attempted_at=CURRENT_TIMESTAMP(3) WHERE id=?",
                            invalid ? "INVALID_TOKEN" : "FAILED", error, deliveryIds.get(i));
                    if (invalid) jdbc.update("UPDATE push_devices SET revoked_at=CURRENT_TIMESTAMP(3),revoke_reason='INVALID_TOKEN' WHERE id=?", deviceIds.get(i));
                }
            }
            String status = succeeded == devices.size() ? "SENT" : succeeded == 0 ? "FAILED" : "PARTIAL";
            jdbc.update("UPDATE notifications SET status=?,sent_at=CASE WHEN ?>0 THEN CURRENT_TIMESTAMP(3) ELSE sent_at END WHERE id=?",
                    status, succeeded, notificationId);
        } catch (FirebaseMessagingException | RuntimeException e) {
            log.error("FCM delivery failed for notification {}", notificationId, e);
            for (Long deliveryId : deliveryIds) {
                jdbc.update("UPDATE notification_deliveries SET status='FAILED',error_code=?,attempted_at=CURRENT_TIMESTAMP(3) WHERE id=?",
                        e.getClass().getSimpleName(), deliveryId);
            }
            jdbc.update("UPDATE notifications SET status='FAILED' WHERE id=?", notificationId);
        }
    }
}
