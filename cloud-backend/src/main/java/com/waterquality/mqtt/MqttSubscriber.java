package com.waterquality.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MqttSubscriber implements CommandLineRunner, MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriber.class);

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.qos:1}")
    private int qos;

    @Value("${mqtt.connection-timeout:10}")
    private int connectionTimeout;

    @Value("${mqtt.keep-alive-interval:30}")
    private int keepAliveInterval;

    @Value("#{'${mqtt.topics:}'.split(',')}")
    private List<String> topics;

    private final MqttMessageHandler messageHandler;
    private MqttClient mqttClient;
    private volatile boolean connected = false;

    public MqttSubscriber(MqttMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    public void run(String... args) {
        if (brokerUrl == null || brokerUrl.isEmpty()) {
            log.info("MQTT Broker未配置，跳过MQTT连接");
            return;
        }
        try {
            connect();
        } catch (Exception e) {
            log.error("MQTT初始连接失败，将在后台重试", e);
            startReconnectThread();
        }
    }

    private void connect() throws MqttException {
        String realClientId = clientId.replace("${random.int}",
                String.valueOf((int) (Math.random() * 10000)));
        mqttClient = new MqttClient(brokerUrl, realClientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        options.setAutomaticReconnect(false);

        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
        }
        if (password != null && !password.isEmpty()) {
            options.setPassword(password.toCharArray());
        }

        mqttClient.setCallback(this);
        mqttClient.connect(options);
        connected = true;

        // 订阅主题
        for (String topic : topics) {
            topic = topic.trim();
            mqttClient.subscribe(topic, qos);
            log.info("MQTT订阅主题: {}", topic);
        }
        log.info("MQTT连接成功: {}", brokerUrl);
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT连接断开，准备重连", cause);
        connected = false;
        startReconnectThread();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            messageHandler.handleMessage(topic, message.getPayload());
        } catch (Exception e) {
            log.error("MQTT消息处理异常: topic={}", topic, e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // QoS 0 不需要确认
    }

    private void startReconnectThread() {
        Thread reconnectThread = new Thread(() -> {
            while (!connected) {
                try {
                    Thread.sleep(10000);
                    log.info("尝试重连MQTT...");
                    connect();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("MQTT重连失败，10秒后重试");
                }
            }
        }, "mqtt-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    /**
     * 发布消息到MQTT
     */
    public void publish(String topic, byte[] payload) {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                MqttMessage message = new MqttMessage(payload);
                message.setQos(qos);
                mqttClient.publish(topic, message);
            } catch (MqttException e) {
                log.error("MQTT发布失败: topic={}", topic, e);
            }
        }
    }
}
