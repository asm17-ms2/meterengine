package com.meterengine.apikey.service;

import com.meterengine.global.security.OrganizationStatusChangedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ApiKeyTable {

  private final AtomicReference<Contents> contents = new AtomicReference<>(new Contents());

  private final Object changeLock = new Object();

  private final List<Consumer<Contents>> changesDuringReplacement = new ArrayList<>();

  private boolean replacing;

  public Optional<ApiKeySnapshot> find(ApiKeyHash keyHash) {
    return Optional.ofNullable(contents.get().snapshotsByKeyHash.get(keyHash));
  }

  public int size() {
    return contents.get().snapshotsByKeyHash.size();
  }

  // 모르는 도입사는 활성으로 보지 않는다. 다음 다시 읽기나 그 도입사의 이벤트가 상태를 채운다.
  public boolean isOrganizationActive(UUID organizationId) {
    return contents.get().organizationActiveById.getOrDefault(organizationId, false);
  }

  public void put(ApiKeySnapshot snapshot) {
    change(current -> current.merge(snapshot));
  }

  // 롤백된 변경이 테이블에 남지 않도록 커밋된 뒤에만 고친다.
  // 키 변경이 함께 읽어 온 도입사 상태는 모르는 도입사에만 쓴다. 아는 도입사는 도입사 이벤트와 다시 읽기가 맞춘다.
  @TransactionalEventListener
  public void apply(ApiKeyChangedEvent event) {
    change(
        current -> {
          current.merge(event.snapshot());
          current.organizationActiveById.putIfAbsent(
              event.snapshot().organizationId(), event.organizationActive());
        });
  }

  // 도입사를 정지해도 키 항목은 남기고 상태만 바꿔, 다시 활성으로 돌리면 같은 키가 통과한다.
  @TransactionalEventListener
  public void apply(OrganizationStatusChangedEvent event) {
    change(current -> current.organizationActiveById.put(event.organizationId(), event.active()));
  }

  private void change(Consumer<Contents> change) {
    synchronized (changeLock) {
      change.accept(contents.get());
      if (replacing) {
        changesDuringReplacement.add(change);
      }
    }
  }

  // 다 읽은 뒤 한 번에 바꿔 끼워, 읽는 쪽은 언제나 완전한 테이블을 본다. 읽기가 실패하면 옛 테이블이 남는다.
  // 읽는 동안 고친 항목은 새 테이블에 다시 얹어, 읽은 뒤 커밋된 폐기와 정지를 옛 값으로 덮지 않는다.
  public synchronized void replaceAll(Supplier<ApiKeyTableContents> loader) {
    startReplacement();
    try {
      Contents replacement = Contents.of(loader.get());
      synchronized (changeLock) {
        changesDuringReplacement.forEach(change -> change.accept(replacement));
        contents.set(replacement);
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

  private static final class Contents {
    private final Map<ApiKeyHash, ApiKeySnapshot> snapshotsByKeyHash = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> organizationActiveById = new ConcurrentHashMap<>();

    // 활성 목록에 있는 도입사는 활성, 키는 있는데 목록에 없는 도입사는 정지로 둔다.
    static Contents of(ApiKeyTableContents loaded) {
      Contents contents = new Contents();
      loaded.activeOrganizationIds().forEach(id -> contents.organizationActiveById.put(id, true));
      for (ApiKeySnapshot snapshot : loaded.snapshots()) {
        contents.snapshotsByKeyHash.put(snapshot.keyHash(), snapshot);
        contents.organizationActiveById.putIfAbsent(snapshot.organizationId(), false);
      }
      return contents;
    }

    void merge(ApiKeySnapshot snapshot) {
      snapshotsByKeyHash.merge(snapshot.keyHash(), snapshot, ApiKeySnapshot::mergeNewer);
    }
  }
}
