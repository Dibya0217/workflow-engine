package io.flowlab.domain;

import jakarta.validation.constraints.*;

public record RetryPolicy(
    @Min(1) @Max(100) int maxAttempts,
    @Min(0) @Max(86400000) long initialDelayMs,
    @DecimalMin("1.0") @DecimalMax("100.0") double backoffMultiplier,
    @Min(1) @Max(604800000) long maxDelayMs) {
  public RetryPolicy {
    if (maxDelayMs == 0) maxDelayMs = 30_000;
  }

  public static RetryPolicy defaults() {
    return new RetryPolicy(3, 1000, 2, 30_000);
  }

  public long delayAfter(int failedAttempt) {
    double delay = initialDelayMs * Math.pow(backoffMultiplier, Math.max(0, failedAttempt - 1));
    return (long) Math.min(maxDelayMs, delay);
  }
}
