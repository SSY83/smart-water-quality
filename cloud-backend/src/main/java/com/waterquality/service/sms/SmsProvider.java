package com.waterquality.service.sms;

public interface SmsProvider {

    String getName();

    boolean send(String phone, String content, String signName);
}
