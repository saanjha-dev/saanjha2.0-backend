package com.saanjha.shared.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces the platform-wide rule (Spec Section C): every state-mutating POST
 * endpoint must accept an {@code Idempotency-Key} header, and replaying the
 * same key within the retention window returns the original response instead
 * of re-executing the operation.
 *
 * This did not previously exist anywhere in the codebase despite being a
 * documented global API standard — identified here as a shared-infrastructure
 * gap and implemented once, for reuse by every future module's create-style
 * endpoints (Application, Task, etc.), not just Project.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** Identifier for the protected action, namespaces the Redis key (e.g. "create-project"). */
    String action();

    /** How long a completed response is replayable for. Defaults to the spec's 24-hour window. */
    long ttlHours() default 24;
}
