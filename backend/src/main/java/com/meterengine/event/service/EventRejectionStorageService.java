package com.meterengine.event.service;

import com.meterengine.event.repository.EventRejectionRepository;
import com.meterengine.global.security.AuthenticatedPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventRejectionStorageService {
  private final EventRejectionRepository eventRejectionRepository;

  public EventRejectionStorageService(EventRejectionRepository eventRejectionRepository) {
    this.eventRejectionRepository = eventRejectionRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      UUID organizationId,
      AuthenticatedPrincipal principal,
      UUID requestId,
      List<EventRejection> rejections) {
    eventRejectionRepository.insert(organizationId, principal, requestId, rejections);
  }
}
