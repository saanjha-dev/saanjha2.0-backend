package com.saanjha.shared.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /** Identifier for the action (e.g., "login", "reset-password") */
    String action() default ""; 
    
    /** How many attempts before exponential backoff triggers */
    int baseLimit() default 5;
    
    /** The base time window in seconds */
    long baseTimeSeconds() default 60;
    
    String errorMessage() default "Too many requests";
}