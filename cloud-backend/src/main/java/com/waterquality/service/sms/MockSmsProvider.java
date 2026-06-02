package com.waterquality.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public boolean send(String phone, String content, String signName) {
        log.info("[MockSMS] 收件人={}, 签名={}, 内容={}", phone, signName, content);
        return true;
    }
}
