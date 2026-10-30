package com.meterengine.webhook.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookAddressPolicy {
  private final boolean allowPrivateNetwork;

  public WebhookAddressPolicy(
      @Value("${meterengine.webhook.allow-private-network:false}") boolean allowPrivateNetwork) {
    this.allowPrivateNetwork = allowPrivateNetwork;
  }

  public InetAddress resolve(String url) throws IOException {
    URI uri = URI.create(url);
    boolean allowedScheme =
        "https".equalsIgnoreCase(uri.getScheme())
            || (allowPrivateNetwork && "http".equalsIgnoreCase(uri.getScheme()));
    if (!allowedScheme
        || uri.getHost() == null
        || uri.getRawUserInfo() != null
        || uri.getRawFragment() != null
        || uri.getPort() == 0
        || uri.getPort() > 65535) {
      throw new BlockedAddressException();
    }
    InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
    for (InetAddress address : addresses) {
      if (!allowPrivateNetwork && isBlocked(address)) {
        throw new BlockedAddressException();
      }
    }
    return addresses[0];
  }

  static boolean isBlocked(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    int first = Byte.toUnsignedInt(bytes[0]);
    int second = Byte.toUnsignedInt(bytes[1]);
    if (bytes.length == 4) {
      int third = Byte.toUnsignedInt(bytes[2]);
      return first == 0
          || first >= 224
          || first == 100 && second >= 64 && second <= 127
          || first == 198 && (second == 18 || second == 19)
          || first == 192 && second == 0 && (third == 0 || third == 2);
    }
    return (first & 0xe0) != 0x20
        || first == 0x20 && second == 0x02
        || first == 0x20 && second == 0x01 && bytes[2] == 0 && bytes[3] == 0;
  }
}
