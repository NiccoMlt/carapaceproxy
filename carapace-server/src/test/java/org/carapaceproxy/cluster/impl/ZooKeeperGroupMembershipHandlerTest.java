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
package org.carapaceproxy.cluster.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.curator.test.TestingServer;
import org.carapaceproxy.cluster.GroupMembershipHandler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ZooKeeperGroupMembershipHandlerTest {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    String peerId1 = "p1";
    String peerId2 = "p2";
    String peerId3 = "p3";

    @Test
    public void testPeerDiscovery() throws Exception {
        try (TestingServer testingServer = new TestingServer(2229, tmpDir.newFolder())) {
            testingServer.start();
            try (ZooKeeperGroupMembershipHandler peer1 = new ZooKeeperGroupMembershipHandler(testingServer.getConnectString(),
                    6000, peerId1, Collections.EMPTY_MAP);
                    ZooKeeperGroupMembershipHandler peer2 = new ZooKeeperGroupMembershipHandler(testingServer.getConnectString(),
                    6000, peerId2, Collections.EMPTY_MAP)) {
                peer1.start();
                peer2.start();
                List<String> peersFrom1 = peer1.getPeers();
                List<String> peersFrom2 = peer2.getPeers();
                assertEquals(Arrays.asList(peerId1, peerId2), peersFrom1);
                assertEquals(Arrays.asList(peerId1, peerId2), peersFrom2);

                try (ZooKeeperGroupMembershipHandler peer3 = new ZooKeeperGroupMembershipHandler(testingServer.getConnectString(),
                        6000, peerId3, Collections.EMPTY_MAP)) {
                    peer3.start();
                    peersFrom1 = peer1.getPeers();
                    peersFrom2 = peer2.getPeers();
                    List<String> peersFrom3 = peer3.getPeers();
                    assertEquals(Arrays.asList(peerId1, peerId2, peerId3), peersFrom1);
                    assertEquals(Arrays.asList(peerId1, peerId2, peerId3), peersFrom2);
                    assertEquals(Arrays.asList(peerId1, peerId2, peerId3), peersFrom3);
                }

                // peer3 exits
                peersFrom1 = peer1.getPeers();
                peersFrom2 = peer2.getPeers();
                assertEquals(Arrays.asList(peerId1, peerId2), peersFrom1);
                assertEquals(Arrays.asList(peerId1, peerId2), peersFrom2);

            }
        }
    }

    @Test
    public void testWatchEvent() throws Exception {
        try (TestingServer testingServer = new TestingServer(2229, tmpDir.newFolder());) {
            testingServer.start();
            try (ZooKeeperGroupMembershipHandler peer1 = new ZooKeeperGroupMembershipHandler(testingServer.getConnectString(),
                    6000, peerId1, Collections.EMPTY_MAP);
                    ZooKeeperGroupMembershipHandler peer2 = new ZooKeeperGroupMembershipHandler(testingServer.getConnectString(),
                    6000, peerId2, Collections.EMPTY_MAP)) {
                peer1.start();
                peer2.start();
                List<String> peersFrom1 = peer1.getPeers();
                List<String> peersFrom2 = peer2.getPeers();
                assertEquals(Arrays.asList(peerId1, peerId2), peersFrom1);
                assertEquals(Arrays.asList(peerId1, peerId2), peersFrom2);

                AtomicInteger eventFired2 = new AtomicInteger();;
                peer2.watchEvent("foo", new GroupMembershipHandler.EventCallback() {
                    @Override
                    public void eventFired(String eventId) {
                        eventFired2.incrementAndGet();
                    }

                    @Override
                    public void reconnected() {

                    }
                });

                peer1.fireEvent("foo");

                for (int i = 0; i < 10; i++) {
                    if (eventFired2.get() >= 1) {
                        break;
                    }
                    Thread.sleep(100);
                }
                assertTrue(eventFired2.get() >= 1);
                eventFired2.set(0);

                try (ZooKeeperGroupMembershipHandler peer3 = new ZooKeeperGroupMembershipHandler(testingServer.getConnectString(),
                        6000, peerId3, Collections.EMPTY_MAP)) {
                    peer3.start();

                    AtomicInteger eventFired3 = new AtomicInteger();
                    peer3.watchEvent("foo", new GroupMembershipHandler.EventCallback() {
                        @Override
                        public void eventFired(String eventId) {
                            eventFired3.incrementAndGet();
                        }

                        @Override
                        public void reconnected() {

                        }
                    });

                    peer1.fireEvent("foo");

                    for (int i = 0; i < 10; i++) {
                        if (eventFired2.get() >= 1
                                && eventFired3.get() >= 1) {
                            break;
                        }
                        Thread.sleep(100);
                    }
                    assertTrue(eventFired3.get() >= 1);
                    assertTrue(eventFired2.get() >= 1);

                    eventFired3.set(0);
                    eventFired2.set(0);

                    peer3.fireEvent("foo");

                    for (int i = 0; i < 10; i++) {
                        if (eventFired2.get() >= 1
                                && eventFired3.get() >= 1) {
                            break;
                        }
                        Thread.sleep(100);
                    }
                    assertTrue(eventFired3.get() == 0); // self events are not fired
                    assertTrue(eventFired2.get() > 0);
                }

            }
        }
    }

    @Test
    public void testPeerInfo() throws Exception {
        try (TestingServer testingServer = new TestingServer(2229, tmpDir.newFolder());) {
            testingServer.start();
            try (ZooKeeperGroupMembershipHandler peer1 = new ZooKeeperGroupMembershipHandler(testingServer.getConnectString(),
                    6000, peerId1, Map.of("name", "peer1"));
                    ZooKeeperGroupMembershipHandler peer2 = new ZooKeeperGroupMembershipHandler(testingServer.getConnectString(),
                    6000, peerId2, Map.of("name", "peer2"))) {
                peer1.start();
                peer2.start();

                // OP on peer1
                {
                    Map<String, String> info = peer1.loadInfoForPeer(peerId1);
                    assertEquals("peer1", info.get("name"));
                    info = peer1.loadInfoForPeer(peerId2);
                    assertEquals("peer2", info.get("name"));

                    peer1.storeLocalPeerInfo(Map.of("name", "newpeer1", "address", "localhost"));
                    info = peer1.loadInfoForPeer(peerId1);
                    assertEquals("newpeer1", info.get("name"));
                    assertEquals("localhost", info.get("address"));                    

                    peer1.storeLocalPeerInfo(Collections.EMPTY_MAP);
                    info = peer1.loadInfoForPeer(peerId1);
                    assertTrue(info.isEmpty());
                    peer1.storeLocalPeerInfo(Map.of("port", "8080"));
                    info = peer1.loadInfoForPeer(peerId1);
                    assertNull(info.get("name"));
                    peer1.storeLocalPeerInfo(null); // discarded
                    info = peer1.loadInfoForPeer(peerId1);
                    assertNull(info);
                }

                // OP on peer2
                {
                    peer2.storeLocalPeerInfo(Map.of("name", "peer2", "address", "localhost:8080"));
                    Map<String, String> info = peer2.loadInfoForPeer(peerId2);
                    assertEquals("peer2", info.get("name"));
                    assertEquals("localhost:8080", info.get("address"));
                    info = peer2.loadInfoForPeer(peerId1);
                    assertNull(info);
                }              
            }
        }
    }
}
