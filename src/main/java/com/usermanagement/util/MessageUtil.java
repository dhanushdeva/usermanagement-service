package com.usermanagement.util;

import java.util.Locale;

import org.springframework.context.MessageSource;

/**
 * Reads error messages from messages/messages.properties (same as bm-usermanagement-v4).
 */
public class MessageUtil {

    private final MessageSource messageSource;

    public MessageUtil(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String getMessage(String code) {
        return messageSource.getMessage(code, null, Locale.ENGLISH);
    }

    public String getMessage(String code, Locale locale) {
        return messageSource.getMessage(code, null, locale);
    }

    public String getMessage(String code, Object[] args, Locale locale) {
        return messageSource.getMessage(code, args, locale);
    }
}
