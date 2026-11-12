package com.meterengine.mail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingEmailSender implements EmailSender {

  private static final Logger logger = LoggerFactory.getLogger(LoggingEmailSender.class);

  @Override
  public void send(EmailMessage message) {
    logger.info(
        "Email not sent (log sender) to={} subject={}\n{}",
        message.to(),
        message.subject(),
        message.text());
  }

  @Override
  public boolean delivers() {
    return false;
  }
}
