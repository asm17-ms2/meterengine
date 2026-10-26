package com.meterengine.account.repository;

import com.meterengine.account.entity.Consent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {}
