/*
 Licensed to Diennea S.r.l. under one
 or more contributor license agreements. See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership. Diennea S.r.l. licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.

 */
package org.carapaceproxy.configstore;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for {@link ConfigurationKeys}.
 */
@RunWith(JUnitParamsRunner.class)
public class ConfigurationKeysTest {

    /**
     * Every property of every example configuration we ship has to be one the server actually reads.
     * This is what keeps the allowlist from rotting: adding a property without registering it fails here.
     *
     * @param resource the example configuration to go through
     * @throws IOException if the example configuration cannot be read
     */
    @Test
    @Parameters({"conf/server.properties", "conf/cluster.properties", "conf/server.dynamic.properties"})
    public void testShippedConfigurationIsFullyRecognized(String resource) throws IOException {
        final Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat("missing example configuration " + resource, in, is(org.hamcrest.CoreMatchers.notNullValue()));
            properties.load(in);
        }
        assertTrue("no properties read from " + resource, !properties.isEmpty());
        for (final String key : properties.stringPropertyNames()) {
            assertThat(resource + ": " + key + " is retired", ConfigurationKeys.retirementAdviceFor(key), is(Optional.empty()));
            assertThat(resource + ": " + key + " is not recognized", ConfigurationKeys.isKnown(key), is(true));
        }
    }

    @Test
    public void testRecognizesFlatAndIndexedProperties() {
        assertThat(ConfigurationKeys.isKnown("connectionsmanager.idletimeout"), is(true));
        assertThat(ConfigurationKeys.isKnown("listener.1.port"), is(true));
        assertThat(ConfigurationKeys.isKnown("route.100.maintenanceaction"), is(true));
        assertThat(ConfigurationKeys.isKnown("action.2.redirect.location"), is(true));

        // the only camelCase fields of the whole indexed set
        assertThat(ConfigurationKeys.isKnown("backend.1.probePath"), is(true));
        assertThat(ConfigurationKeys.isKnown("backend.1.safeCapacity"), is(true));

        // flat, despite sharing its prefix with the indexed listener family
        assertThat(ConfigurationKeys.isKnown("listener.offset.port"), is(true));
    }

    @Test
    public void testRecognizesForwardedNamespaces() {
        // handed over to HerdDB and to ZooKeeper as they are, so any suffix goes
        assertThat(ConfigurationKeys.isKnown("db.server.base.dir"), is(true));
        assertThat(ConfigurationKeys.isKnown("db.anything.at.all"), is(true));
        assertThat(ConfigurationKeys.isKnown("zookeeper.anything.at.all"), is(true));

        // the filter factory reads whatever the filter type needs
        assertThat(ConfigurationKeys.isKnown("filter.1.type"), is(true));
        assertThat(ConfigurationKeys.isKnown("filter.1.whatever"), is(true));
    }

    @Test
    public void testReportsUnrecognizedProperties() {
        assertThat(ConfigurationKeys.isKnown("connectionsmanger.idletimeout"), is(false));
        assertThat(ConfigurationKeys.isKnown("listener.1.prot"), is(false));
        assertThat(ConfigurationKeys.isKnown("backend.1.probepath"), is(false));
        assertThat(ConfigurationKeys.isKnown("something.entirely.made.up"), is(false));
    }

    @Test
    public void testReportsRetiredProperties() {
        assertThat(ConfigurationKeys.retirementAdviceFor("connectionpool.1.id").isPresent(), is(true));
        assertThat(ConfigurationKeys.retirementAdviceFor("connectionpool.1.maxconnectionsperendpoint").isPresent(), is(true));
        assertThat(ConfigurationKeys.retirementAdviceFor("listener.1.enabled").isPresent(), is(true));
        assertThat(ConfigurationKeys.retirementAdviceFor("listener.2.ocsp").isPresent(), is(true));
        assertThat(ConfigurationKeys.retirementAdviceFor("cache.allocator.usepooledbytebufallocator").isPresent(), is(true));

        // a retired property is not a known one
        assertThat(ConfigurationKeys.isKnown("connectionpool.1.id"), is(false));
        assertThat(ConfigurationKeys.isKnown("listener.1.enabled"), is(false));

        // while the rest of the family is untouched
        assertThat(ConfigurationKeys.retirementAdviceFor("listener.1.port"), is(Optional.empty()));
    }
}
