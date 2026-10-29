package com.meterengine.webhook.client;

import java.io.IOException;

public class BlockedAddressException extends IOException {
  public BlockedAddressException() {
    super("webhook address is blocked");
  }
}
