/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.event.catalog;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.event.CatalogPostModifyEvent;
import org.geoserver.catalog.plugin.Patch;
import org.geoserver.catalog.plugin.PropertyDiff;
import org.geoserver.catalog.plugin.PropertyDiff.Change;
import org.geoserver.cloud.event.info.ConfigInfoType;
import org.geoserver.cloud.event.info.InfoEvent;

import java.util.Objects;

import javax.annotation.Nullable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonTypeName("DefaultDataStoreSet")
@EqualsAndHashCode(callSuper = true)
public class DefaultDataStoreEvent extends CatalogInfoModifyEvent {

    private @NonNull @Getter String workspaceId;
    private @Getter String defaultDataStoreId;

    protected DefaultDataStoreEvent() {}

    DefaultDataStoreEvent(
            Catalog source,
            Catalog target,
            @NonNull String workspaceId,
            String defaultDataStoreId,
            @NonNull Patch patch) {

        super(source, target, InfoEvent.CATALOG_ID, ConfigInfoType.Catalog, patch);

        this.workspaceId = workspaceId;
        this.defaultDataStoreId = defaultDataStoreId;
    }

    @Override
    public String toString() {
        return String.format(
                "%s[workspace: %s, store: %s]",
                getClass().getSimpleName(), getWorkspaceId(), getDefaultDataStoreId());
    }

    public static DefaultDataStoreEvent createLocal(
            @NonNull Catalog source, @NonNull CatalogPostModifyEvent event) {

        PropertyDiff diff =
                PropertyDiff.valueOf(
                        event.getPropertyNames(), event.getOldValues(), event.getNewValues());

        final Change change =
                diff.get("defaultDataStore")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "defaultDataStore is not in the change list"));

        final @Nullable DataStoreInfo newStore = (DataStoreInfo) change.getNewValue();

        final @Nullable String newDefaultStoreId = resolveId(newStore);
        final @NonNull String workspaceId = resolveId(resolveWorkspace(change));

        Patch patch = diff.toPatch();

        return new DefaultDataStoreEvent(source, null, workspaceId, newDefaultStoreId, patch);
    }

    public static DefaultDataStoreEvent createLocal(
            @NonNull Catalog source, @NonNull WorkspaceInfo workspace, DataStoreInfo newStore) {

        Patch patch = new Patch();
        patch.add("defaultDataStore", newStore);

        final @Nullable String newDefaultStoreId = resolveId(newStore);
        final @NonNull String workspaceId = resolveId(workspace);

        return new DefaultDataStoreEvent(source, null, workspaceId, newDefaultStoreId, patch);
    }

    private static @NonNull WorkspaceInfo resolveWorkspace(Change change) {
        DataStoreInfo oldStore = (DataStoreInfo) change.getOldValue();
        DataStoreInfo newStore = (DataStoreInfo) change.getNewValue();
        DataStoreInfo store = oldStore == null ? newStore : oldStore;
        Objects.requireNonNull(store);
        return store.getWorkspace();
    }
}
