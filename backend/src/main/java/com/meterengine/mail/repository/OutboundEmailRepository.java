package com.meterengine.mail.repository;

import com.meterengine.mail.entity.OutboundEmail;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundEmailRepository extends JpaRepository<OutboundEmail, UUID> {}
