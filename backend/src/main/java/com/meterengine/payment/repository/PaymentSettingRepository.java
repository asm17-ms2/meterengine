package com.meterengine.payment.repository;

import com.meterengine.payment.entity.PaymentSetting;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSettingRepository extends JpaRepository<PaymentSetting, UUID> {}
