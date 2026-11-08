package com.meterengine.apikey.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ApiKeyTable {

  private final AtomicReference<Map<ApiKeyHash, ApiKeySnapshot>> snapshotsByKeyHash =
      new AtomicReference<>(new ConcurrentHashMap<>());

  private final Object changeLock = new Object();

  private final List<Consumer<Map<ApiKeyHash, ApiKeySnapshot>>> changesDuringReplacement =
      new ArrayList<>();

  private boolean replacing;

  public Optional<ApiKeySnapshot> find(ApiKeyHash keyHash) {
    return Optional.ofNullable(snapshotsByKeyHash.get().get(keyHash));
  }

  public int size() {
    return snapshotsByKeyHash.get().size();
  }

  public void put(ApiKeySnapshot snapshot) {
    change(snapshots -> snapshots.merge(snapshot.keyHash(), snapshot, ApiKeySnapshot::mergeNewer));
  }

  private void change(Consumer<Map<ApiKeyHash, ApiKeySnapshot>> change) {
    synchronized (changeLock) {
      change.accept(snapshotsByKeyHash.get());
      if (replacing) {
        changesDuringReplacement.add(change);
      }
    }
  }

  // 롤백된 변경이 테이블에 남지 않도록 커밋된 뒤에만 고친다.
  @TransactionalEventListener
  public void apply(ApiKeyChangedEvent event) {
    put(event.snapshot());
  }

  // 다 읽은 뒤 한 번에 바꿔 끼워, 읽는 쪽은 언제나 완전한 테이블을 본다. 읽기가 실패하면 옛 테이블이 남는다.
  // 읽는 동안 고친 항목은 새 테이블에 다시 얹어, 읽은 뒤 커밋된 폐기를 옛 값으로 덮지 않는다.
  public synchronized void replaceAll(Supplier<List<ApiKeySnapshot>> loader) {
    startReplacement();
    try {
      Map<ApiKeyHash, ApiKeySnapshot> replacement = new ConcurrentHashMap<>();
      loader.get().forEach(snapshot -> replacement.put(snapshot.keyHash(), snapshot));
      synchronized (changeLock) {
        changesDuringReplacement.forEach(change -> change.accept(replacement));
        snapshotsByKeyHash.set(replacement);
      }
    } finally {
      finishReplacement();
    }
  }

  private void startReplacement() {
    synchronized (changeLock) {
      replacing = true;
      changesDuringReplacement.clear();
    }
  }

  private void finishReplacement() {
    synchronized (changeLock) {
      replacing = false;
      changesDuringReplacement.clear();
    }
  }
}
