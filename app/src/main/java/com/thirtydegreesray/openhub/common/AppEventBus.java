package com.thirtydegreesray.openhub.common;

import androidx.annotation.NonNull;

import com.thirtydegreesray.openhub.util.AppLog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small event bus inside the app (replaces the EventBus library). Objects register
 * themselves and receive the posted events in their {@link Subscribe} methods, on the
 * posting thread.
 */
public enum AppEventBus {
    INSTANCE;

    private final Bus bus = new Bus();

    @NonNull
    public Bus getEventBus() {
        return bus;
    }

    public static final class Bus {

        /** subscriber -> its @Subscribe methods (insertion order kept per post) */
        private final Map<Object, List<Method>> subscribers = new ConcurrentHashMap<>();

        public void register(@NonNull Object subscriber) {
            List<Method> methods = new ArrayList<>();
            for (Method method : subscriber.getClass().getMethods()) {
                if (method.isAnnotationPresent(Subscribe.class)
                        && method.getParameterTypes().length == 1) {
                    methods.add(method);
                }
            }
            if (methods.isEmpty()) {
                throw new IllegalArgumentException(subscriber.getClass().getName()
                        + " has no public @Subscribe methods");
            }
            subscribers.put(subscriber, methods);
        }

        public void unregister(@NonNull Object subscriber) {
            subscribers.remove(subscriber);
        }

        public boolean isRegistered(@NonNull Object subscriber) {
            return subscribers.containsKey(subscriber);
        }

        public void post(@NonNull Object event) {
            for (Map.Entry<Object, List<Method>> entry : subscribers.entrySet()) {
                for (Method method : entry.getValue()) {
                    if (!method.getParameterTypes()[0].isInstance(event)) continue;
                    try {
                        method.invoke(entry.getKey(), event);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                        if (cause instanceof Error) throw (Error) cause;
                        AppLog.e("AppEventBus", "subscriber failed", cause);
                    } catch (IllegalAccessException e) {
                        AppLog.e("AppEventBus", "subscriber not reachable", e);
                    }
                }
            }
        }
    }
}
