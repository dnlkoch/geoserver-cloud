/*
 * (c) 2024 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.config.catalog.events;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.geoserver.cloud.event.lifecycle.LifecycleEvent;
import org.geoserver.cloud.event.lifecycle.ReloadEvent;
import org.geoserver.cloud.event.lifecycle.ResetEvent;
import org.geoserver.config.impl.GeoServerLifecycleHandler;

import java.util.function.Consumer;

@RequiredArgsConstructor
@Slf4j
class GeoServerLifecycleEventPublisher implements GeoServerLifecycleHandler {

    private final @NonNull Consumer<? super LifecycleEvent> eventPublisher;

    void publish(@NonNull LifecycleEvent event) {
        eventPublisher.accept(event);
    }

    @Override
    public void onReset() {
        log.info("Publishing the onReset event");

        publish(new ResetEvent());
    }

    @Override
    public void onDispose() {
        log.info("Ignoring the onDispose event");
    }

    @Override
    public void beforeReload() {
        log.info("Ignoring the beforeReload event");
    }

    @Override
    public void onReload() {
        log.info("Publishing the onReload event");

        publish(new ReloadEvent());
    }
}
