/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.entity.messaging.zookeeper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.zookeeper.ZooKeeperEnsemble;
import org.apache.brooklyn.entity.zookeeper.ZooKeeperNode;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;

/**
 * A live test of the {@link org.apache.brooklyn.entity.zookeeper.ZooKeeperEnsemble} entity.
 *
 * Tests that a 3 node cluster can be started on Amazon EC2 and data written on one {@link org.apache.brooklyn.entity.zookeeper.ZooKeeperEnsemble}
 * can be read from another, using the Astyanax API.
 */
public class ZooKeeperEnsembleLiveTest extends BrooklynAppLiveTestSupport {

    private static final Logger log = LoggerFactory.getLogger(ZooKeeperEnsembleLiveTest.class);
    private static final String DEFAULT_LOCATION = "jclouds:aws-ec2:eu-west-1";

    private Location testLocation;
    private String locationSpec;

    @BeforeClass(alwaysRun = true)
    @Parameters({"locationSpec"})
    public void setLocationSpec(@Optional String locationSpec) {
        this.locationSpec = !Strings.isBlank(locationSpec)
                            ? locationSpec
                            : DEFAULT_LOCATION;
        log.info("Running {} with in {}", this, this.locationSpec);
    }

    @Override
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.getManagementContext().getLocationRegistry().getLocationManaged(locationSpec);
    }

    /**
     * Test that a two node cluster starts up and allows access through both nodes.
     */
    @Test(groups = "Live")
    public void testStartUpConnectAndResize() throws Exception {
        final String zkDataPath = "/ensembletest";
        final int initialSize = 3;
        ZooKeeperEnsemble ensemble = app.createAndManageChild(EntitySpec.create(ZooKeeperEnsemble.class)
                .configure(DynamicCluster.INITIAL_SIZE, initialSize)
                .configure(ZooKeeperEnsemble.CLUSTER_NAME, "ZooKeeperEnsembleLiveTest"));

        app.start(ImmutableList.of(testLocation));
        Entities.dumpInfo(app);

        EntityAsserts.assertAttributeEqualsEventually(ensemble, ZooKeeperEnsemble.GROUP_SIZE, 3);
        EntityAsserts.assertAttributeEqualsEventually(ensemble, Startable.SERVICE_UP, true);
        assertNotNull(ensemble.sensors().get(ZooKeeperEnsemble.ZOOKEEPER_ENDPOINTS),
                "expected value for " + ZooKeeperEnsemble.ZOOKEEPER_ENDPOINTS + " on " + ensemble + ", was null");
        Set<Integer> nodeIds = Sets.newHashSet();
        for (Entity zkNode : ensemble.getMembers()) {
            nodeIds.add(zkNode.config().get(ZooKeeperNode.MY_ID));
        }
        assertEquals(nodeIds.size(), initialSize, "expected " + initialSize + " node ids, found " + Iterables.toString(nodeIds));

        // Write data to one and read from the others.
        List<String> servers = ensemble.sensors().get(ZooKeeperEnsemble.ZOOKEEPER_SERVERS);
        assertNotNull(servers, "value for sensor should not be null: " + ZooKeeperEnsemble.ZOOKEEPER_SERVERS);
        assertEquals(servers.size(), initialSize, "expected " + initialSize + " entries in " + servers);

        // Write to one
        String firstServer = servers.get(0);
        HostAndPort conn = HostAndPort.fromString(firstServer);
        log.info("Writing data to {}", conn);
        try (ZooKeeperTestSupport zkts = new ZooKeeperTestSupport(conn)) {
            zkts.create(zkDataPath, "data".getBytes());
            assertEquals(new String(zkts.get(zkDataPath)), "data");
        }

        // And read from the others.
        for (int i = 1; i < servers.size(); i++) {
            conn = HostAndPort.fromString(servers.get(i));
            log.info("Asserting that data can be read from {}", conn);
            assertPathDataEventually(conn, zkDataPath, "data");
        }
    }

    protected void assertPathDataEventually(HostAndPort hostAndPort, final String path, String expected) throws Exception {
        try (ZooKeeperTestSupport zkts = new ZooKeeperTestSupport(hostAndPort)) {
            final Supplier<String> dataSupplier = new Supplier<String>() {
                @Override
                public String get() {
                    try {
                        return new String(zkts.get(path));
                    } catch (Exception e) {
                        throw Exceptions.propagate(e);
                    }
                }
            };
            Asserts.eventually(dataSupplier, Predicates.equalTo(expected));
        }
    }

}
