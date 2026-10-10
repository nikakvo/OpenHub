package com.thirtydegreesray.openhub.util.state;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field that {@link DataAutoAccess} copies to and from a Bundle
 * (intent extras, fragment arguments, saved instance state).
 *
 * Drop-in replacement for com.thirtydegreesray:dataautoaccess, which only
 * existed on the shut-down JCenter repository.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoAccess {
    /** Bundle key. Defaults to the field name. */
    String dataName() default "";
}
