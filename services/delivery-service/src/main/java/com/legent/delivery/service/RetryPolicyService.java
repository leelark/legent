package com.legent.delivery.service;

import com.legent.delivery.adapter.ProviderDispatchException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Random;

@Service
public class RetryPolicyService {

    private static final Random JITTER_RANDOM = new Random();

    public RetryDecision decide(ProviderDispatchException exception, int attempts) {
        String failureClass = classify(exception);
        if (isPermanentFailure(exception, failureClass)) {
            return new RetryDecision(false, failureClass, null, "permanent failure");
        }

        int maxAttempts = maxAttempts(failureClass);
        if (attempts >= maxAttempts) {
            return new RetryDecision(false, failureClass, null, "retry budget exhausted");
        }

        long delayMinutes = delayMinutes(failureClass, Math.max(1, attempts));
        Instant nextRetryAt = Instant.now().plus(delayMinutes, ChronoUnit.MINUTES);
        return new RetryDecision(true, failureClass, nextRetryAt, "retry with jitter");
    }

    public String classify(ProviderDispatchException exception) {
        if (exception == null) {
            return "UNKNOWN";
        }
        String msg = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        if (msg.contains("complaint") || msg.contains("abuse")) {
            return "COMPLAINT";
        }
        if (msg.contains("auth") || msg.contains("spf") || msg.contains("dkim") || msg.contains("dmarc")) {
            return "AUTH_REJECTION";
        }
        if (msg.contains("suppress") || msg.contains("unsubscribe") || msg.contains("blocked contact")) {
            return "SUPPRESSED";
        }
        if (msg.contains("content") || msg.contains("spam") || msg.contains("policy")) {
            return "CONTENT_BLOCKED";
        }
        if (msg.contains("reputation") || msg.contains("inbox safety")) {
            return "REPUTATION_BLOCKED";
        }
        if (exception.isPermanent()) {
            if (msg.contains("mailbox") || msg.contains("user unknown") || msg.contains("550") || msg.contains("invalid recipient")) {
                return "HARD_BOUNCE";
            }
            return "PERMANENT";
        }
        if (msg.contains("throttle") || msg.contains("rate") || msg.contains("too many")) {
            return "THROTTLED";
        }
        if (msg.contains("timeout") || msg.contains("connect") || msg.contains("network")) {
            return "NETWORK";
        }
        if (msg.contains("queue")) {
            return "QUEUE_PRESSURE";
        }
        return "TRANSIENT";
    }

    private boolean isPermanentFailure(ProviderDispatchException exception, String failureClass) {
        return exception != null && exception.isPermanent()
                || switch (failureClass) {
                    case "HARD_BOUNCE", "COMPLAINT", "AUTH_REJECTION", "SUPPRESSED",
                            "CONTENT_BLOCKED", "REPUTATION_BLOCKED", "PERMANENT" -> true;
                    default -> false;
                };
    }

    private int maxAttempts(String failureClass) {
        return switch (failureClass) {
            case "THROTTLED", "QUEUE_PRESSURE" -> 6;
            case "NETWORK", "TRANSIENT" -> 4;
            default -> 3;
        };
    }

    private long delayMinutes(String failureClass, int attempts) {
        long base = switch (failureClass) {
            case "THROTTLED" -> switch (attempts) {
                case 1 -> 5;
                case 2 -> 20;
                default -> 60;
            };
            case "QUEUE_PRESSURE" -> switch (attempts) {
                case 1 -> 10;
                case 2 -> 30;
                default -> 120;
            };
            case "NETWORK" -> switch (attempts) {
                case 1 -> 2;
                case 2 -> 10;
                default -> 30;
            };
            default -> switch (attempts) {
                case 1 -> 5;
                case 2 -> 30;
                default -> 90;
            };
        };
        double jitterFactor = 0.75 + (JITTER_RANDOM.nextDouble() * 0.5);
        return Math.max(1, Math.round(base * jitterFactor));
    }

    public record RetryDecision(boolean shouldRetry, String failureClass, Instant nextRetryAt, String reason) {}
}
