package com.meterengine.mail.client;

import com.meterengine.mail.service.EmailMessage;
import com.meterengine.mail.service.EmailSender;
import java.nio.charset.StandardCharsets;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

public class SesEmailSender implements EmailSender {

  private final SesV2Client sesV2Client;
  private final String from;

  public SesEmailSender(SesV2Client sesV2Client, String from) {
    this.sesV2Client = sesV2Client;
    this.from = from;
  }

  @Override
  public void send(EmailMessage message) {
    sesV2Client.sendEmail(
        SendEmailRequest.builder()
            .fromEmailAddress(from)
            .destination(destination -> destination.toAddresses(message.to()))
            .content(
                content ->
                    content.simple(
                        simple ->
                            simple
                                .subject(utf8(message.subject()))
                                .body(
                                    body ->
                                        body.text(utf8(message.text()))
                                            .html(utf8(message.html())))))
            .build());
  }

  @Override
  public boolean delivers() {
    return true;
  }

  private static Content utf8(String data) {
    return Content.builder().charset(StandardCharsets.UTF_8.name()).data(data).build();
  }
}
