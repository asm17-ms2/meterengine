package com.meterengine.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerServiceTest {
  private final UUID organizationId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
  private final CustomerRepository repository = mock(CustomerRepository.class);
  private final AuditLogService audit = mock(AuditLogService.class);
  private final CustomerService service = new CustomerService(repository, audit);

  @Test
  void 보관과_복원은_행을_지우지_않고_각각_한번만_감사한다() {
    Customer customer = new Customer(customerId, organizationId, "고객", null);
    when(repository.lock(organizationId, customerId)).thenReturn(Optional.of(customer));
    service.delete(organizationId, customerId);
    service.delete(organizationId, customerId);
    assertThat(customer.getArchivedAt()).isNotNull();
    service.restore(organizationId, customerId);
    service.restore(organizationId, customerId);
    assertThat(customer.getArchivedAt()).isNull();
    verify(repository, never()).delete(any());
    verify(audit, times(1))
        .record(eq(organizationId), eq("customer_archived"), any(), any(), any());
    verify(audit, times(1))
        .record(eq(organizationId), eq("customer_restored"), any(), any(), any());
  }

  @Test
  void 다른_조직이나_없는_고객은_보관할_수_없다() {
    when(repository.lock(organizationId, customerId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(organizationId, customerId))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(audit);
  }
}
