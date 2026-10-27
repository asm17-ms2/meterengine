package com.meterengine.invoice.service;

import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.mail.entity.OutboundEmail;
import com.meterengine.mail.repository.OutboundEmailRepository;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
class InvoiceEmailService {

  private final TemplateEngine templateEngine;
  private final OutboundEmailRepository outboundEmailRepository;

  InvoiceEmailService(
      TemplateEngine templateEngine, OutboundEmailRepository outboundEmailRepository) {
    this.templateEngine = templateEngine;
    this.outboundEmailRepository = outboundEmailRepository;
  }

  // 확정한 인보이스의 청구 메일을 확정 시점 값으로 남긴다.
  void record(
      String organizationName,
      Invoice invoice,
      List<DraftInvoiceLine> draftInvoiceLines,
      String recipientEmail) {
    String subject = "[%s] %s 사용료 청구서".formatted(organizationName, invoice.getPeriod());

    Context context = new Context(Locale.KOREA);
    context.setVariable("organizationName", organizationName);
    context.setVariable("customerName", invoice.getCustomerName());
    context.setVariable("period", invoice.getPeriod());
    context.setVariable(
        "lines", draftInvoiceLines.stream().map(InvoiceEmailService::toEmailLine).toList());
    context.setVariable("supplyAmount", krw(invoice.getSupplyAmount()));
    context.setVariable("taxAmount", krw(invoice.getTaxAmount()));
    context.setVariable("totalAmount", krw(invoice.getTotalAmount()));

    outboundEmailRepository.save(
        new OutboundEmail(
            UUID.randomUUID(),
            invoice.getOrganizationId(),
            OutboundEmail.INVOICE,
            invoice.getPeriod(),
            invoice.getId(),
            recipientEmail,
            subject,
            templateEngine.process("mail/invoice", context),
            invoice.getFinalizedAt()));
  }

  private static EmailLine toEmailLine(DraftInvoiceLine draftInvoiceLine) {
    return new EmailLine(
        label(draftInvoiceLine),
        dimensionValues(draftInvoiceLine.dimensionValues()),
        decimal(draftInvoiceLine.quantity()),
        krw(draftInvoiceLine.unitPrice()),
        krw(draftInvoiceLine.amount()));
  }

  private static String label(DraftInvoiceLine line) {
    return switch (line.lineType()) {
      case "base_fee" -> "기본료";
      case "minimum_adjustment" -> "최소 사용료 조정";
      case "graduated_tier" -> line.billableMetricCode() + " / 구간 " + (line.tierIndex() + 1);
      case "volume" -> line.billableMetricCode() + " / 총량 단가";
      default -> line.billableMetricCode();
    };
  }

  private static String dimensionValues(Map<String, Object> dimensionValues) {
    return dimensionValues.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(Collectors.joining(", "));
  }

  private static String krw(Number value) {
    return decimal(value) + "원";
  }

  private static String decimal(Number value) {
    NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.KOREA);
    numberFormat.setMaximumFractionDigits(10);
    numberFormat.setRoundingMode(RoundingMode.HALF_UP);
    return numberFormat.format(value);
  }

  public record EmailLine(
      String billableMetricCode,
      String dimensionValues,
      String quantity,
      String unitPrice,
      String amount) {}
}
