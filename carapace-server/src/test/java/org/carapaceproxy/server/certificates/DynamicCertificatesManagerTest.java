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

import java.net.URL;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Properties;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.carapaceproxy.cluster.impl.NullGroupMembershipHandler;
import org.carapaceproxy.configstore.CertificateData;
import org.carapaceproxy.configstore.ConfigurationStore;
import static org.carapaceproxy.configstore.ConfigurationStoreUtils.base64EncodeCertificateChain;
import org.carapaceproxy.configstore.PropertiesConfigurationStore;
import org.carapaceproxy.server.RuntimeServerConfiguration;
import static org.carapaceproxy.server.certificates.DynamicCertificateState.AVAILABLE;
import static org.carapaceproxy.server.certificates.DynamicCertificateState.DNS_CHALLENGE_WAIT;
import static org.carapaceproxy.server.certificates.DynamicCertificateState.EXPIRED;
import static org.carapaceproxy.server.certificates.DynamicCertificateState.ORDERING;
import static org.carapaceproxy.server.certificates.DynamicCertificateState.REQUEST_FAILED;
import static org.carapaceproxy.server.certificates.DynamicCertificateState.VERIFIED;
import static org.carapaceproxy.server.certificates.DynamicCertificateState.VERIFYING;
import static org.carapaceproxy.server.certificates.DynamicCertificateState.WAITING;
import static org.carapaceproxy.server.certificates.DynamicCertificatesManager.DEFAULT_KEYPAIRS_SIZE;
import static org.carapaceproxy.utils.CertificatesTestUtils.generateSampleChain;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Order;
import static org.shredzone.acme4j.Status.INVALID;
import static org.shredzone.acme4j.Status.VALID;
import org.carapaceproxy.server.HttpProxyServer;
import org.carapaceproxy.server.Listeners;
import org.powermock.reflect.Whitebox;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.connector.Connection;
import org.shredzone.acme4j.toolbox.JSON;
import org.shredzone.acme4j.util.KeyPairUtils;

/**
 * Test for DynamicCertificatesManager.
 *
 * @author paolo.venturi
 */
@RunWith(JUnitParamsRunner.class)
public class DynamicCertificatesManagerTest {

    @Test
    @Parameters({
        "challenge_null",
        "challenge_status_invalid",
        "order_response_error",
        "available_to_expired",
        "all_ok"
    })
    public void testCertificateStateManagement(String runCase) throws Exception {
        // ACME mocking
        ACMEClient ac = mock(ACMEClient.class);
        Order o = mock(Order.class);
        when(o.getLocation()).thenReturn(new URL("https://localhost/index"));
        when(ac.getLogin()).thenReturn(mock(Login.class));
        when(ac.createOrderForDomain(any())).thenReturn(o);
        Http01Challenge c = mock(Http01Challenge.class);
        when(c.getToken()).thenReturn("");
        when(c.getJSON()).thenReturn(JSON.parse(
                "{\"url\": \"https://localhost/index\", \"type\": \"http-01\", \"token\": \"mytoken\"}"
        ));
        when(c.getAuthorization()).thenReturn("");
        when(ac.getChallengeForOrder(any(), eq(false))).thenReturn(runCase.equals("challenge_null") ? null : c);
        when(ac.checkResponseForChallenge(any())).thenReturn(runCase.equals("challenge_status_invalid") ? INVALID : VALID);
        when(ac.checkResponseForOrder(any())).thenReturn(runCase.equals("order_response_error") ? INVALID : VALID);

        KeyPair keyPair = KeyPairUtils.createKeyPair(DEFAULT_KEYPAIRS_SIZE);
        Certificate cert = mock(Certificate.class);
        X509Certificate _cert = (X509Certificate) generateSampleChain(keyPair, runCase.equals("available_to_expired"))[0];
        when(cert.getCertificateChain()).thenReturn(Arrays.asList(_cert));
        when(ac.fetchCertificateForOrder(any())).thenReturn(cert);

        HttpProxyServer parent = mock(HttpProxyServer.class);
        when(parent.getListeners()).thenReturn(mock(Listeners.class));
        DynamicCertificatesManager man = new DynamicCertificatesManager(parent);
        man.attachGroupMembershipHandler(new NullGroupMembershipHandler());
        Whitebox.setInternalState(man, ac);

        // Store mocking
        ConfigurationStore store = mock(ConfigurationStore.class);
        String chain = base64EncodeCertificateChain(generateSampleChain(keyPair, false), keyPair.getPrivate());
        when(store.loadKeyPairForDomain(anyString())).thenReturn(keyPair);

        // yet available certificate
        String d0 = "localhost0";
        CertificateData cd0 = new CertificateData(d0, "", chain, AVAILABLE, "", "", false);
        when(store.loadCertificateForDomain(eq(d0))).thenReturn(cd0);
        // certificate to order
        String d1 = "localhost1";
        CertificateData cd1 = new CertificateData(d1, "", "", WAITING, "", "", false);
        when(store.loadCertificateForDomain(eq(d1))).thenReturn(cd1);
        man.setConfigurationStore(store);
        // manual certificate
        String d2 = "notacme";
        CertificateData cd2 = new CertificateData(d2, "", "", AVAILABLE, "", "", true);
        when(store.loadCertificateForDomain(eq(d2))).thenReturn(cd2);

        man.setConfigurationStore(store);

        // Manager setup
        Properties props = new Properties();
        props.setProperty("certificate.0.hostname", d0);
        props.setProperty("certificate.0.mode", "acme");
        props.setProperty("certificate.0.daysbeforerenewal", "0");
        props.setProperty("certificate.1.hostname", d1);
        props.setProperty("certificate.1.mode", "acme");
        props.setProperty("certificate.1.daysbeforerenewal", "0");
        props.setProperty("certificate.2.hostname", d2);
        props.setProperty("certificate.2.mode", "manual");
        props.setProperty("certificate.2.daysbeforerenewal", "0");
        ConfigurationStore configStore = new PropertiesConfigurationStore(props);
        RuntimeServerConfiguration conf = new RuntimeServerConfiguration();
        conf.configure(configStore);
        man.reloadConfiguration(conf);

        assertEquals(AVAILABLE, man.getStateOfCertificate(d0));
        assertEquals(AVAILABLE, man.getStateOfCertificate(d2));
        assertNotNull(man.getCertificateForDomain(d2));
        man.setStateOfCertificate(d2, WAITING); // has not to be renewed by the manager (saveCounter = 1)
        assertEquals(WAITING, man.getStateOfCertificate(d2));

        int saveCounter = 1; // at every run the certificate has to be saved to the db (whether not AVAILABLE).

        // WAITING
        assertEquals(WAITING, man.getStateOfCertificate(d1));
        man.run();
        assertEquals(runCase.equals("challenge_null") ? VERIFIED : VERIFYING, man.getStateOfCertificate(d1));
        verify(store, times(++saveCounter)).saveCertificate(any());

        man.run();
        if (runCase.equals("challenge_null")) { // VERIFIED
            assertEquals(ORDERING, man.getStateOfCertificate(d1));
            verify(store, times(++saveCounter)).saveCertificate(any());
        } else { // VERIFYING
            assertEquals(runCase.equals("challenge_status_invalid") ? REQUEST_FAILED : VERIFIED, man.getStateOfCertificate(d1));
            verify(store, times(++saveCounter)).saveCertificate(any());
            man.run();
            assertEquals(runCase.equals("challenge_status_invalid") ? WAITING : ORDERING, man.getStateOfCertificate(d1));
            verify(store, times(++saveCounter)).saveCertificate(any());
        }
        // ORDERING
        if (!runCase.equals("challenge_status_invalid")) {
            man.run();
            assertEquals(runCase.equals("order_response_error") ? REQUEST_FAILED : AVAILABLE, man.getStateOfCertificate(d1));
            verify(store, times(++saveCounter)).saveCertificate(any());
            man.run();
            if (runCase.equals("order_response_error")) { // REQUEST_FAILED
                assertEquals(WAITING, man.getStateOfCertificate(d1));
                verify(store, times(++saveCounter)).saveCertificate(any());
            } else { // AVAILABLE
                DynamicCertificateState state = man.getStateOfCertificate(d1);
                assertEquals(runCase.equals("available_to_expired") ? EXPIRED : AVAILABLE, state);
                saveCounter += AVAILABLE.equals(state) ? 0 : 1; // only with state AVAILABLE the certificate hasn't to be saved.
                verify(store, times(saveCounter)).saveCertificate(any());

                man.run();
                state = man.getStateOfCertificate(d1);
                assertEquals(runCase.equals("available_to_expired") ? WAITING : AVAILABLE, state);
                saveCounter += AVAILABLE.equals(state) ? 0 : 1; // only with state AVAILABLE the certificate hasn't to be saved.
                verify(store, times(saveCounter)).saveCertificate(any());
            }
        }
    }

    @Test
    // A) record not created -> request failed
    // B) record created but not ready -> request failed after LIMIT attempts
    // C) record created and ready -> VERIFYING
    // D) challenge verified -> record deleted
    // E) challenge failed -> record deleted
    @Parameters({
        "challenge_creation_failed",
        "challenge_check_limit_expired",
        "challenge_ready",
        "challenge_verified",
        "challenge_failed"
    })
    public void testWidlcardCertificateStateManagement(String runCase) throws Exception {
        System.setProperty("carapace.acme.dnschallengereachabilitycheck.limit", "2");

        Session session = mock(Session.class);
        when(session.connect()).thenReturn(mock(Connection.class));
        Login login = mock(Login.class);
        when(login.getSession()).thenReturn(session);
        when(login.getKeyPair()).thenReturn(KeyPairUtils.createKeyPair(DEFAULT_KEYPAIRS_SIZE));

        // ACME mocking
        ACMEClient ac = mock(ACMEClient.class);
        Order o = mock(Order.class);
        when(o.getLocation()).thenReturn(new URL("https://localhost/index"));
        when(ac.getLogin()).thenReturn(login);
        when(ac.createOrderForDomain(any())).thenReturn(o);

        Dns01Challenge c = mock(Dns01Challenge.class);
        when(c.getDigest()).thenReturn("");
        when(c.getJSON()).thenReturn(JSON.parse(
                "{\"url\": \"https://localhost/index\", \"type\": \"dns-01\", \"token\": \"mytoken\"}"
        ));

        when(ac.getChallengeForOrder(any(), eq(true))).thenReturn(c);
        when(ac.checkResponseForChallenge(any())).thenReturn(runCase.equals("challenge_failed") ? INVALID : VALID);

        KeyPair keyPair = KeyPairUtils.createKeyPair(DEFAULT_KEYPAIRS_SIZE);
        Certificate cert = mock(Certificate.class);
        X509Certificate _cert = (X509Certificate) generateSampleChain(keyPair, false)[0];
        when(cert.getCertificateChain()).thenReturn(Arrays.asList(_cert));
        when(ac.fetchCertificateForOrder(any())).thenReturn(cert);

        HttpProxyServer parent = mock(HttpProxyServer.class);
        when(parent.getListeners()).thenReturn(mock(Listeners.class));
        DynamicCertificatesManager man = new DynamicCertificatesManager(parent);
        man.attachGroupMembershipHandler(new NullGroupMembershipHandler());
        Whitebox.setInternalState(man, ac);

        // Route53Cliente mocking
        man.initAWSClient("access", "secret");
        Route53Client r53Client = mock(Route53Client.class);
        when(r53Client.createDnsChallengeForDomain(any(), any())).thenReturn(!runCase.startsWith("challenge_creation_failed"));
        when(r53Client.isDnsChallengeForDomainAvailable(any(), any())).thenReturn(!(runCase.equals("challenge_creation_failed_n_reboot") || runCase.equals("challenge_check_limit_expired")));
        Whitebox.setInternalState(man, r53Client);

        // Store mocking
        ConfigurationStore store = mock(ConfigurationStore.class);
        when(store.loadKeyPairForDomain(anyString())).thenReturn(keyPair);

        // certificate to order
        String domain = "*.localhost";
        CertificateData cd1 = new CertificateData(domain, "", "", WAITING, "", "", false);
        when(store.loadCertificateForDomain(eq(domain))).thenReturn(cd1);
        man.setConfigurationStore(store);

        // Manager setup
        Properties props = new Properties();
        props.setProperty("certificate.1.hostname", domain);
        props.setProperty("certificate.1.mode", "acme");
        props.setProperty("certificate.1.daysbeforerenewal", "0");
        ConfigurationStore configStore = new PropertiesConfigurationStore(props);
        RuntimeServerConfiguration conf = new RuntimeServerConfiguration();
        conf.configure(configStore);
        man.reloadConfiguration(conf);

        CertificateData certData = man.getCertificateDataForDomain(domain);
        assertThat(certData.isWildcard(), is(true));
        int saveCounter = 0; // at every run the certificate has to be saved to the db (whether not AVAILABLE).

        // WAITING
        assertEquals(WAITING, man.getStateOfCertificate(domain));
        man.run();
        verify(store, times(++saveCounter)).saveCertificate(any());
        if (runCase.equals("challenge_creation_failed")) {
            // WAITING
            assertThat(man.getStateOfCertificate(domain), is(WAITING));
        } else {
            // DNS_CHALLENGE_WAIT
            assertThat(man.getStateOfCertificate(domain), is(DNS_CHALLENGE_WAIT));
            man.run();
            verify(store, times(++saveCounter)).saveCertificate(any());
            if (runCase.equals("challenge_check_limit_expired")) {
                assertThat(man.getStateOfCertificate(domain), is(DNS_CHALLENGE_WAIT));
                man.run();
                verify(store, times(++saveCounter)).saveCertificate(any());
                assertThat(man.getStateOfCertificate(domain), is(REQUEST_FAILED));
                // check dns-challenge-record deleted
                verify(r53Client, times(1)).deleteDnsChallengeForDomain(any(), any());
            } else {
                // VERIFYING
                assertThat(man.getStateOfCertificate(domain), is(VERIFYING));
                man.run();
                verify(store, times(++saveCounter)).saveCertificate(any());
                if (runCase.equals("challenge_failed")) {
                    // REQUEST_FAILED
                    assertThat(man.getStateOfCertificate(domain), is(REQUEST_FAILED));
                    // check dns-challenge-record deleted
                    verify(r53Client, times(1)).deleteDnsChallengeForDomain(any(), any());
                } else if (runCase.equals("challenge_verified")) {
                    // VERIFIED
                    assertThat(man.getStateOfCertificate(domain), is(VERIFIED));
                    // check dns-challenge-record deleted
                    verify(r53Client, times(1)).deleteDnsChallengeForDomain(any(), any());
                }
            }
        }
    }

}
