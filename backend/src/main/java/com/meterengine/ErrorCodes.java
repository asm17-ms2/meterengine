package com.meterengine;

public final class ErrorCodes {

  public static final String VALIDATION_ERROR = "validation_error";

  public static final String UNKNOWN_CUSTOMER_REFERENCE = "unknown_customer_reference";

  public static final String INVALID_EVENT = "invalid_event";

  public static final String MALFORMED_REQUEST_BODY = "malformed_request_body";

  public static final String REQUEST_TYPE_NOT_SUPPORTED = "request_type_not_supported";

  public static final String RESPONSE_TYPE_NOT_ACCEPTABLE = "response_type_not_acceptable";

  public static final String METHOD_NOT_ALLOWED = "method_not_allowed";

  public static final String ENDPOINT_NOT_FOUND = "endpoint_not_found";

  public static final String CUSTOMER_NOT_FOUND = "customer_not_found";

  public static final String CUSTOMER_HAS_EVENTS = "customer_has_events";

  public static final String CUSTOMER_HAS_INVOICES = "customer_has_invoices";

  public static final String UNKNOWN_ORGANIZATION = "unknown_organization";

  public static final String METRIC_NOT_FOUND = "metric_not_found";

  public static final String PRICE_POLICY_ALREADY_EXISTS = "price_policy_already_exists";

  public static final String INVALID_PRICE_POLICY = "invalid_price_policy";

  public static final String METRIC_ALREADY_EXISTS = "metric_already_exists";

  public static final String INVALID_BILLABLE_METRIC = "invalid_billable_metric";

  private ErrorCodes() {}
}
