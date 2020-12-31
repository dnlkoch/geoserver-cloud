/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.catalog.caching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.plugin.CatalogPlugin;
import org.geoserver.catalog.plugin.ExtendedCatalogFacade;
import org.geoserver.catalog.plugin.Patch;
import org.geoserver.config.GeoServerFacade;
import org.geoserver.config.plugin.GeoServerImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = GeoServerBackendCacheConfiguration.class)
@EnableAutoConfiguration
@RunWith(SpringRunner.class)
@SuppressWarnings("deprecation")
public class CachingCatalogFacadeTest {

    private @MockBean @Qualifier("rawCatalog") CatalogPlugin rawCatalog;
    private @MockBean @Qualifier("geoServer") GeoServerImpl rawGeoServer;
    private @MockBean @Qualifier("geoserverFacade") GeoServerFacade rawGeoServerFacade;

    private @MockBean @Qualifier("catalogFacade") ExtendedCatalogFacade mock;

    private @Autowired @Qualifier("cachingCatalogFacade") CachingCatalogFacade caching;
    private @Autowired CacheManager cacheManager;

    private WorkspaceInfo ws;
    private WorkspaceInfo ws2;
    private NamespaceInfo ns;
    private NamespaceInfo ns2;

    private DataStoreInfo ds;
    private CoverageStoreInfo cs;
    private FeatureTypeInfo ft;
    private CoverageInfo c;
    private LayerInfo layer;
    private LayerGroupInfo lg;
    private StyleInfo style;

    private Cache cache;

    public @Before void before() {
        ws = stub(WorkspaceInfo.class, 1);
        ws2 = stub(WorkspaceInfo.class, 2);
        ns = stub(NamespaceInfo.class, 1);
        ns2 = stub(NamespaceInfo.class, 2);

        ds = stub(DataStoreInfo.class);
        cs = stub(CoverageStoreInfo.class);
        ft = stub(FeatureTypeInfo.class);
        c = stub(CoverageInfo.class);
        layer = stub(LayerInfo.class);
        when(layer.getResource()).thenReturn(ft);
        lg = stub(LayerGroupInfo.class);
        style = stub(StyleInfo.class);

        when(mock.getWorkspace(eq(ws.getId()))).thenReturn(ws);
        when(mock.getNamespace(eq(ns.getId()))).thenReturn(ns);

        when(mock.getStore(eq(ds.getId()), eq(StoreInfo.class))).thenReturn(ds);
        when(mock.getStore(eq(ds.getId()), eq(DataStoreInfo.class))).thenReturn(ds);

        when(mock.getStore(eq(cs.getId()), eq(StoreInfo.class))).thenReturn(cs);
        when(mock.getStore(eq(cs.getId()), eq(CoverageStoreInfo.class))).thenReturn(cs);

        when(mock.getResource(eq(ft.getId()), eq(ResourceInfo.class))).thenReturn(ft);
        when(mock.getResource(eq(ft.getId()), eq(FeatureTypeInfo.class))).thenReturn(ft);

        when(mock.getResource(eq(c.getId()), eq(ResourceInfo.class))).thenReturn(c);
        when(mock.getResource(eq(c.getId()), eq(CoverageInfo.class))).thenReturn(c);

        when(mock.getLayer(eq(layer.getId()))).thenReturn(layer);
        when(mock.getLayerGroup(eq(lg.getId()))).thenReturn(lg);

        when(mock.getStyle(eq(style.getId()))).thenReturn(style);

        this.cache = cacheManager.getCache(CachingCatalogFacade.CACHE_NAME);
        this.cache.clear();
    }

    public @Test void testEvict() {
        testEvicts(ws, caching::evict);
        testEvicts(ns, caching::evict);
        testEvicts(ds, caching::evict);
        testEvicts(cs, caching::evict);
        testEvicts(ft, caching::evict);
        testEvicts(c, caching::evict);
    }

    public @Test void testSuperTypeEvicts() {
        CatalogInfoKey concreteTypeKey = new CatalogInfoKey(ds);
        CatalogInfoKey abstractTypeKey = new CatalogInfoKey(ds.getId(), StoreInfo.class);

        cache.put(concreteTypeKey, ds);
        assertSame(ds, cache.get(concreteTypeKey).get());
        assertSame(ds, cache.get(abstractTypeKey).get());

        assertTrue(cache.evictIfPresent(abstractTypeKey));
        assertNull(cache.get(concreteTypeKey));
    }

    public @Test void testSubTypeEvicts() {
        CatalogInfoKey concreteTypeKey = new CatalogInfoKey(ft);
        CatalogInfoKey abstractTypeKey = new CatalogInfoKey(ft.getId(), ResourceInfo.class);

        cache.put(abstractTypeKey, ft);

        assertSame(ft, cache.get(concreteTypeKey).get());
        assertSame(ft, cache.get(abstractTypeKey).get());

        assertTrue(cache.evictIfPresent(concreteTypeKey));
        assertNull(cache.get(abstractTypeKey));
    }

    public @Test void testPublishedInfoEvicts() {
        CatalogInfoKey concreteTypeKey = new CatalogInfoKey(layer);
        CatalogInfoKey abstractTypeKey = new CatalogInfoKey(layer.getId(), PublishedInfo.class);

        cache.put(concreteTypeKey, layer);

        assertSame(layer, cache.get(concreteTypeKey).get());
        assertSame(layer, cache.get(abstractTypeKey).get());

        assertTrue(cache.evictIfPresent(abstractTypeKey));
        assertNull(cache.get(concreteTypeKey));
    }

    public @Test void testAddStoreInfo() {
        DataStoreInfo info = this.ds;
        DataStoreInfo added = stub(DataStoreInfo.class, 1); // same id
        when(mock.add(same(info))).thenReturn(added);
        assertSame(added, caching.add(info));
        assertSame("expected cache put", added, cache.get(new CatalogInfoKey(info)).get());
    }

    public @Test void testRemoveStoreInfo() {
        testEvicts(ds, caching::remove);
        testEvicts(cs, caching::remove);
    }

    public @Test void testSaveStoreInfo() {
        testEvicts(ds, caching::save);
        testEvicts(cs, caching::save);
    }

    public @Test void testGetStore() {
        assertSameTimesN(ds, id -> caching.getStore(id, DataStoreInfo.class), 3);
        verify(mock, times(1)).getStore(eq(ds.getId()), eq(DataStoreInfo.class));

        assertSameTimesN(ds, id -> caching.getStore(id, StoreInfo.class), 3);
        // assertSame(ds, caching.getStore(ds.getId(), StoreInfo.class));
        verify(mock, times(0)).getStore(eq(ds.getId()), eq(StoreInfo.class));

        assertSameTimesN(cs, id -> caching.getStore(id, StoreInfo.class), 3);
        verify(mock, times(1)).getStore(eq(cs.getId()), eq(StoreInfo.class));

        assertSameTimesN(cs, id -> caching.getStore(id, CoverageStoreInfo.class), 3);
        verify(mock, times(0)).getStore(eq(cs.getId()), eq(CoverageStoreInfo.class));

        verifyNoMoreInteractions(mock);

        assertNull(caching.getStore(ds.getId(), CoverageStoreInfo.class));
        assertNull(caching.getStore(cs.getId(), DataStoreInfo.class));
    }

    public @Test void testGetDefaultDataStore() {
        final Object key = CachingCatalogFacade.generateDefaultDataStoreKey(ws);
        assertNull(caching.getDefaultDataStore(ws));
        assertNull("null return value should have not been cached", cache.get(key));

        when(mock.getDefaultDataStore(same(ws))).thenReturn(ds);

        assertSame(ds, caching.getDefaultDataStore(ws));
        assertSame(ds, cache.get(key).get());
        assertNull(caching.getDefaultDataStore(ws2));

        clearInvocations(mock);
        cache.evict(key);

        assertSame(ds, caching.getDefaultDataStore(ws));
        assertSame(ds, caching.getDefaultDataStore(ws));
        assertSame(ds, caching.getDefaultDataStore(ws));
        verify(mock, times(1)).getDefaultDataStore(same(ws));
    }

    public @Test void testSetDefaultDataStore() {
        final Object key = CachingCatalogFacade.generateDefaultDataStoreKey(ws);
        when(mock.getDefaultDataStore(same(ws))).thenReturn(ds);
        assertSame(ds, caching.getDefaultDataStore(ws));

        // should evict
        caching.setDefaultDataStore(ws, ds);
        verify(mock, times(1)).setDefaultDataStore(same(ws), same(ds));

        assertNull("expected cache evict", cache.get(key));

        when(mock.getDefaultDataStore(same(ws))).thenReturn(ds);
        assertSame(ds, caching.getDefaultDataStore(ws));
        caching.setDefaultDataStore(ws, null);
        assertNull("expected cache evict", cache.get(key));
    }

    public @Test void testAddResourceInfo() {
        FeatureTypeInfo info = this.ft;
        FeatureTypeInfo added = stub(FeatureTypeInfo.class, 1); // same id
        when(mock.add(same(info))).thenReturn(added);
        assertSame(added, caching.add(info));
        assertSame("expected cache put", added, cache.get(new CatalogInfoKey(info)).get());
    }

    public @Test void testRemoveResourceInfo() {
        testEvicts(ft, caching::remove);
        testEvicts(c, caching::remove);
    }

    public @Test void testSaveResourceInfo() {
        testEvicts(ft, caching::save);
        testEvicts(c, caching::save);
    }

    public @Test void testGetResource() {
        CatalogInfo info = ft;
        assertSameTimesN(info, id -> caching.getResource(id, ResourceInfo.class), 3);
        verify(mock, times(1)).getResource(eq(info.getId()), eq(ResourceInfo.class));

        assertSameTimesN(info, id -> caching.getResource(id, FeatureTypeInfo.class), 3);
        verify(mock, times(0)).getResource(eq(info.getId()), eq(FeatureTypeInfo.class));

        assertNull(caching.getResource(ft.getId(), CoverageInfo.class));
        assertNull(caching.getResource(c.getId(), FeatureTypeInfo.class));
    }

    public @Test void testAddLayerInfo() {
        LayerInfo info = this.layer;
        LayerInfo added = stub(LayerInfo.class, 1); // same id
        when(mock.add(same(info))).thenReturn(added);
        assertSame(added, caching.add(info));
        assertSame("expected cache put", added, cache.get(new CatalogInfoKey(info)).get());
    }

    public @Test void testRemoveLayerInfo() {
        testEvicts(layer, caching::remove);
    }

    public @Test void testRemoveLayerEvictsLayersPerResource() {
        assertNotNull(layer.getResource());
        List<LayerInfo> layers = Collections.singletonList(layer);
        when(mock.getLayers(same(ft))).thenReturn(layers);

        assertEquals(layers, caching.getLayers(layer.getResource()));

        CatalogInfoKey layersKey =
                CachingCatalogFacade.generateLayersByResourceKey(layer.getResource());
        assertEquals(layers, cache.get(layersKey).get());

        testEvicts(layer, caching::remove);

        assertNull("layers by resource not evicted then layer deleted", cache.get(layersKey));
    }

    public @Test void testSaveLayerInfo() {
        testEvicts(layer, caching::save);
    }

    public @Test void testGetLayer() {
        CatalogInfo info = layer;
        assertSameTimesN(info, caching::getLayer, 3);
        verify(mock, times(1)).getLayer(eq(info.getId()));
    }

    public @Test void testGetLayersByResource() {
        List<LayerInfo> expected = Collections.singletonList(layer);
        when(mock.getLayers(same(ft))).thenReturn(expected);

        assertEquals(expected, caching.getLayers(ft));
        assertEquals(expected, caching.getLayers(ft));
        assertEquals(expected, caching.getLayers(ft));

        verify(mock, times(1)).getLayers(same(ft));

        CatalogInfoKey key = CachingCatalogFacade.generateLayersByResourceKey(ft);
        ValueWrapper layersWrapper = cache.get(key);
        assertNotNull(layersWrapper);
        assertEquals(expected, layersWrapper.get());
    }

    public @Test void testAddLayerGroupInfo() {
        LayerGroupInfo info = this.lg;
        LayerGroupInfo added = stub(LayerGroupInfo.class, 1); // same id
        when(mock.add(same(info))).thenReturn(added);
        assertSame(added, caching.add(info));
        assertSame("expected cache put", added, cache.get(new CatalogInfoKey(info)).get());
    }

    public @Test void testRemoveLayerGroupInfo() {
        testEvicts(lg, caching::remove);
    }

    public @Test void testSaveLayerGroupInfo() {
        testEvicts(lg, caching::save);
    }

    public @Test void testGetLayerGroup() {
        CatalogInfo info = lg;
        assertSameTimesN(info, caching::getLayerGroup, 3);
        verify(mock, times(1)).getLayerGroup(eq(info.getId()));
    }

    public @Test void testAddNamespaceInfo() {
        NamespaceInfo info = this.ns2;
        NamespaceInfo added = stub(NamespaceInfo.class, 2); // same id
        when(mock.add(same(info))).thenReturn(added);
        assertSame(added, caching.add(info));
        assertSame("expected cache put", added, cache.get(new CatalogInfoKey(info)).get());
    }

    public @Test void testRemoveNamespaceInfo() {
        testEvicts(ns, caching::remove);
    }

    public @Test void testSaveNamespaceInfo() {
        testEvicts(ns, caching::save);
    }

    public @Test void testGetDefaultNamespace() {
        final String key = CachingCatalogFacade.DEFAULT_NAMESPACE_CACHE_KEY;
        assertNull(caching.getDefaultNamespace());
        assertNull(cache.get(key));

        clearInvocations(mock);
        when(mock.getDefaultNamespace()).thenReturn(ns);

        assertSameTimesN(ns, s -> caching.getDefaultNamespace(), 3);
        assertNotNull(cache.get(key));
        assertSame(ns, cache.get(key).get());
        verify(mock, times(1)).getDefaultNamespace();
    }

    public @Test void testSetDefaultNamespace() {
        final String key = CachingCatalogFacade.DEFAULT_NAMESPACE_CACHE_KEY;
        when(mock.getDefaultNamespace()).thenReturn(ns);
        assertSame(ns, caching.getDefaultNamespace());

        caching.setDefaultNamespace(null);
        assertNull("expected cache evict", cache.get(key));

        when(mock.getDefaultNamespace()).thenReturn(ns);
        assertSame(ns, caching.getDefaultNamespace());

        caching.setDefaultNamespace(ns2);
        assertNull("expected cache evict", cache.get(key));
    }

    public @Test void testGetNamespace() {
        CatalogInfo info = ns;
        assertSameTimesN(info, caching::getNamespace, 3);
        verify(mock, times(1)).getNamespace(eq(info.getId()));
    }

    public @Test void testGetWorkspace() {
        assertSameTimesN(ws, caching::getWorkspace, 3);
        verify(mock, times(1)).getWorkspace(eq(ws.getId()));
    }

    public @Test void testAddWorkspaceInfo() {
        WorkspaceInfo info = this.ws;
        WorkspaceInfo added = stub(WorkspaceInfo.class, 1); // same id than ws
        when(mock.add(same(info))).thenReturn(added);
        assertSame(added, caching.add(info));
        assertSame("expected cache put", added, cache.get(new CatalogInfoKey(info)).get());
    }

    public @Test void testRemoveWorkspaceInfo() {
        testEvicts(ws, caching::remove);
    }

    public @Test void testSaveWorkspaceInfo() {
        testEvicts(ws, caching::save);
    }

    public @Test void testGetDefaultWorkspace() {
        assertNull(caching.getDefaultWorkspace());
        assertNull(cache.get(CachingCatalogFacade.DEFAULT_WORKSPACE_CACHE_KEY));

        clearInvocations(mock);
        when(mock.getDefaultWorkspace()).thenReturn(ws);

        assertSameTimesN(ws, s -> caching.getDefaultWorkspace(), 3);
        assertNotNull(cache.get(CachingCatalogFacade.DEFAULT_WORKSPACE_CACHE_KEY));
        assertSame(ws, cache.get(CachingCatalogFacade.DEFAULT_WORKSPACE_CACHE_KEY).get());
        verify(mock, times(1)).getDefaultWorkspace();
    }

    public @Test void testSetDefaultWorkspace() {
        final String key = CachingCatalogFacade.DEFAULT_WORKSPACE_CACHE_KEY;
        when(mock.getDefaultWorkspace()).thenReturn(ws);
        assertSame(ws, caching.getDefaultWorkspace());

        caching.setDefaultWorkspace(null);
        assertNull("expected cache evict", cache.get(key));

        when(mock.getDefaultWorkspace()).thenReturn(ws);
        assertSame(ws, caching.getDefaultWorkspace());

        caching.setDefaultWorkspace(ws2);
        assertNull("expected cache evict", cache.get(key));
    }

    public @Test void testAddStyleInfo() {
        StyleInfo info = this.style;
        StyleInfo added = stub(StyleInfo.class, 1); // same id
        when(mock.add(same(info))).thenReturn(added);
        assertSame(added, caching.add(info));
        assertSame("expected cache put", added, cache.get(new CatalogInfoKey(info)).get());
    }

    public @Test void testRemoveStyleInfo() {
        testEvicts(style, caching::remove);
    }

    public @Test void testSaveStyleInfo() {
        testEvicts(style, caching::save);
    }

    public @Test void testGetStyle() {
        CatalogInfo info = style;
        assertSameTimesN(info, caching::getStyle, 3);
        verify(mock, times(1)).getStyle(eq(info.getId()));
    }

    public @Test void testUpdate() {
        DataStoreInfo info = this.ds;
        DataStoreInfo updated = stub(DataStoreInfo.class, 1); // same id
        when(mock.update(same(info), any())).thenReturn(updated);
        assertSame(updated, caching.update(info, new Patch()));
        assertSame("expected cache put", updated, cache.get(new CatalogInfoKey(info)).get());
    }

    private <T extends CatalogInfo> void testEvicts(T info, Consumer<T> op) {
        CatalogInfoKey key = new CatalogInfoKey(info);
        cache.put(key, info);
        op.accept(info);
        assertNull("expected cache evict", cache.get(key));
    }

    private <T extends Info> void assertSameTimesN(T info, Function<String, T> query, int times) {
        for (int i = 0; i < times; i++) {
            T result = query.apply(info.getId());
            Assert.assertSame(info, result);
        }
    }

    private <T extends Info> T stub(Class<T> type) {
        return stub(type, 1);
    }

    private <T extends Info> T stub(Class<T> type, int id) {
        T info = Mockito.mock(type);
        String sid = type.getSimpleName() + "." + id;
        when(info.getId()).thenReturn(sid);
        return info;
    }
}
