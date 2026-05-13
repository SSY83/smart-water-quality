package com.waterquality.websocket;

import com.waterquality.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertWebSocketHandler.class);
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private static final long HEARTBEAT_INTERVAL = 30;
    private final JwtTokenProvider jwtTokenProvider;
    private final ScheduledExecutorService heartbeatScheduler;

    public AlertWebSocketHandler(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        this.heartbeatScheduler.scheduleAtFixedRate(
            this::checkHeartbeats, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从子协议中提取JWT令牌进行身份认证
        String token = extractToken(session);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        String userId = String.valueOf(jwtTokenProvider.getUserId(token));
        session.getAttributes().put("userId", userId);
        session.getAttributes().put("lastHeartbeat", System.currentTimeMillis());
        sessions.put(userId, session);
        log.info("WebSocket连接建立: userId={}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
            session.getAttributes().put("lastHeartbeat", System.currentTimeMillis());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            log.info("WebSocket连接关闭: userId={}", userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket传输错误", exception);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            log.error("关闭WebSocket连接失败", e);
        }
    }

    /**
     * 向所有在线用户广播告警消息
     */
    public void broadcastAlert(String payload) {
        TextMessage message = new TextMessage(payload);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            try {
                if (entry.getValue().isOpen()) {
                    entry.getValue().sendMessage(message);
                }
            } catch (IOException e) {
                log.error("WebSocket推送失败: userId={}", entry.getKey(), e);
            }
        }
    }

    /**
     * 向指定用户推送消息
     */
    public void sendToUser(String userId, String payload) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.error("WebSocket推送失败: userId={}", userId, e);
            }
        }
    }

    private void checkHeartbeats() {
        long now = System.currentTimeMillis();
        long timeout = HEARTBEAT_INTERVAL * 2 * 1000;
        sessions.forEach((userId, session) -> {
            Long lastHeartbeat = (Long) session.getAttributes().get("lastHeartbeat");
            if (lastHeartbeat != null && now - lastHeartbeat > timeout) {
                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException e) {
                    log.error("关闭超时连接失败", e);
                }
                sessions.remove(userId);
            }
        });
    }

    private String extractToken(WebSocketSession session) {
        String protocol = session.getHandshakeHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocol != null && protocol.startsWith("Bearer ")) {
            return protocol.substring(7);
        }
        return null;
    }
}
