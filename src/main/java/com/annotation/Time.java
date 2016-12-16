package com.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by haria on 12.12.16.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Time {
    public enum TimeInterval { MILLISECOND, NANOSECOND };
    com.annotation.Time.TimeInterval interval() default com.annotation.Time.TimeInterval.MILLISECOND;
    String format() default "Elapsed %s";
}
