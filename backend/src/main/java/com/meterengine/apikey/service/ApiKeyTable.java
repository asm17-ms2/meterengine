package com.meterengine.apikey.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyTable {

  private final AtomicReference<Map<ApiKeyHash, ApiKeySnapshot>> snapshotsByKeyHash =
      new AtomicReference<>(new ConcurrentHashMap<>());

  public Optional<ApiKeySnapshot> find(ApiKeyHash keyHash) {
    return Optional.ofNullable(snapshotsByKeyHash.get().get(keyHash));
  }

  public int size() {
    return snapshotsByKeyHash.get().size();
  }

  // 다 읽은 뒤 한 번에 바꿔 끼워, 읽는 쪽은 언제나 완전한 테이블을 본다. 읽기가 실패하면 옛 테이블이 남는다.
  public synchronized void replaceAll(Supplier<List<ApiKeySnapshot>> loader) {
    Map<ApiKeyHash, ApiKeySnapshot> replacement = new ConcurrentHashMap<>();
    loader.get().forEach(snapshot -> replacement.put(snapshot.keyHash(), snapshot));
    snapshotsByKeyHash.set(replacement);
  }
}
