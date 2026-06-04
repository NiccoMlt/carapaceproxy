/*
 * Licensed to Diennea S.r.l. under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Diennea S.r.l. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.carapaceproxy.server.certificates;

import static org.carapaceproxy.server.certificates.DynamicCertificateState.AVAILABLE;
import static org.carapaceproxy.server.certificates.DynamicCertificateState.WAITING;
import static org.carapaceproxy.server.certificates.DynamicCertificatesManager.keystoreDataChanged;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.carapaceproxy.configstore.CertificateData;
import org.junit.Test;

/**
 * Unit tests for {@link DynamicCertificatesManager#keystoreDataChanged(Map, Map)}, the gate that keeps a
 * still-failing ACME certificate from restarting the SSL listeners on every reload cycle.
 */
public class DynamicCertificatesManagerKeystoreDiffTest {

    private static CertificateData cert(final String domain, final byte[] keystoreData) {
        final CertificateData data = new CertificateData(domain, null, keystoreData == null ? WAITING : AVAILABLE);
        data.setKeystoreData(keystoreData);
        return data;
    }

    private static Map<String, CertificateData> snapshot(final CertificateData... certs) {
        final Map<String, CertificateData> map = new HashMap<>();
        for (final CertificateData cert : certs) {
            map.put(cert.getDomain(), cert);
        }
        return map;
    }

    @Test
    public void failingCertWithNoKeystoreBothCyclesIsNotAChange() {
        // The loop case: a perpetually failing cert keeps null keystore bytes across cycles.
        final Map<String, CertificateData> old = snapshot(cert("example.com", null));
        final Map<String, CertificateData> fresh = snapshot(cert("example.com", null));
        assertFalse(keystoreDataChanged(old, fresh));
    }

    @Test
    public void firstIssuanceIsAChange() {
        final Map<String, CertificateData> old = snapshot(cert("example.com", null));
        final Map<String, CertificateData> fresh = snapshot(cert("example.com", new byte[]{1, 2, 3}));
        assertTrue(keystoreDataChanged(old, fresh));
    }

    @Test
    public void sameKeystoreBytesIsNotAChange() {
        final Map<String, CertificateData> old = snapshot(cert("example.com", new byte[]{1, 2, 3}));
        final Map<String, CertificateData> fresh = snapshot(cert("example.com", new byte[]{1, 2, 3}));
        assertFalse(keystoreDataChanged(old, fresh));
    }

    @Test
    public void renewedKeystoreBytesIsAChange() {
        final Map<String, CertificateData> old = snapshot(cert("example.com", new byte[]{1, 2, 3}));
        final Map<String, CertificateData> fresh = snapshot(cert("example.com", new byte[]{4, 5, 6}));
        assertTrue(keystoreDataChanged(old, fresh));
    }

    @Test
    public void oneChangedDomainAmongStableOnesIsAChange() {
        final Map<String, CertificateData> old = snapshot(
                cert("stable.com", new byte[]{1, 2, 3}),
                cert("renewing.com", new byte[]{4, 5, 6}));
        final Map<String, CertificateData> fresh = snapshot(
                cert("stable.com", new byte[]{1, 2, 3}),
                cert("renewing.com", new byte[]{7, 8, 9}));
        assertTrue(keystoreDataChanged(old, fresh));
    }

    @Test
    public void missingPreviousEntryIsAChange() {
        final Map<String, CertificateData> old = snapshot();
        final Map<String, CertificateData> fresh = snapshot(cert("example.com", new byte[]{1, 2, 3}));
        assertTrue(keystoreDataChanged(old, fresh));
    }
}
