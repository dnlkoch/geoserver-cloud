/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.event.catalog;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import lombok.NonNull;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.event.CatalogRemoveEvent;
import org.geoserver.cloud.event.info.ConfigInfoType;
import org.geoserver.cloud.event.info.InfoRemoveEvent;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonTypeName("CatalogInfoRemoved")
public class CatalogInfoRemoveEvent
        extends InfoRemoveEvent<CatalogInfoRemoveEvent, Catalog, CatalogInfo> {

    protected CatalogInfoRemoveEvent() {}

    CatalogInfoRemoveEvent(
            Catalog source, Catalog target, @NonNull String id, @NonNull ConfigInfoType type) {
        super(source, target, id, type);
    }

    public static CatalogInfoRemoveEvent createLocal(
            @NonNull Catalog source, @NonNull CatalogRemoveEvent event) {

        return createLocal(source, event.getSource());
    }

    public static CatalogInfoRemoveEvent createLocal(
            @NonNull Catalog source, @NonNull CatalogInfo info) {

        String id = resolveId(info);
        ConfigInfoType type = ConfigInfoType.valueOf(info);
        return new CatalogInfoRemoveEvent(source, null, id, type);
    }
}
