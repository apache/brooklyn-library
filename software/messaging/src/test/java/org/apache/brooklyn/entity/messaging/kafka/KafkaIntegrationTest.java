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
package org.apache.brooklyn.entity.messaging.kafka;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.entity.messaging.activemq.ActiveMQBroker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test the operation of the {@link ActiveMQBroker} class.
 *
 * TODO test that sensors update.
 */
//TODO Does it really need to be a live test? When converting from ApplicationBuilder, preserved
//existing behaviour of using the live BrooklynProperties.
public class KafkaIntegrationTest extends BrooklynAppLiveTestSupport {

    private Location testLocation;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.newLocalhostProvisioningLocation();
    }

    /**
     * Test that we can start a zookeeper.
     */
    @Test(groups = "Integration")
    public void testZookeeper() {
        final KafkaZooKeeper zookeeper = app.createAndManageChild(EntitySpec.create(KafkaZooKeeper.class));

        zookeeper.start(ImmutableList.of(testLocation));
        EntityAsserts.assertAttributeEqualsEventually(ImmutableMap.of("timeout", 60*1000), zookeeper, Startable.SERVICE_UP, true);

        zookeeper.stop();
        assertFalse(zookeeper.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Test that we can start a  broker and zookeeper together.
     */
    @Test(groups = "Integration")
    public void testBrokerPlusZookeeper() {
        final KafkaZooKeeper zookeeper = app.createAndManageChild(EntitySpec.create(KafkaZooKeeper.class));
        final KafkaBroker broker = app.createAndManageChild(EntitySpec.create(KafkaBroker.class).configure(KafkaBroker.ZOOKEEPER, zookeeper));

        zookeeper.start(ImmutableList.of(testLocation));
        EntityAsserts.assertAttributeEqualsEventually(ImmutableMap.of("timeout", 60 * 1000), zookeeper, Startable.SERVICE_UP, true);

        broker.start(ImmutableList.of(testLocation));
        EntityAsserts.assertAttributeEqualsEventually(ImmutableMap.of("timeout", 60*1000), broker, Startable.SERVICE_UP, true);

        zookeeper.stop();
        assertFalse(zookeeper.getAttribute(Startable.SERVICE_UP));

        broker.stop();
        assertFalse(broker.getAttribute(Startable.SERVICE_UP));
    }

    /**
     * Test that we can start a cluster with zookeeper and one broker.
     *
     * Connects to the zookeeper controller and tests sending and receiving messages on a topic.
     */
    @Test(groups = "Integration")
    public void testTwoBrokerCluster() throws InterruptedException {
        final KafkaCluster cluster = app.createAndManageChild(EntitySpec.create(KafkaCluster.class)
                .configure(KafkaCluster.INITIAL_SIZE, 2));

        cluster.start(ImmutableList.of(testLocation));
        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TWO_MINUTES), new Callable<Void>() {
            @Override
            public Void call() {
                assertTrue(cluster.getAttribute(Startable.SERVICE_UP));
                assertTrue(cluster.getZooKeeper().getAttribute(Startable.SERVICE_UP));
                assertEquals(cluster.getCurrentSize().intValue(), 2);
                return null;
            }
        });

        Entities.dumpInfo(cluster);

        final KafkaSupport support = new KafkaSupport(cluster);

        support.sendMessage("brooklyn", "TEST_MESSAGE");

        Asserts.succeedsEventually(MutableMap.of("timeout", Duration.FIVE_SECONDS), new Runnable() {
            @Override
            public void run() {
                String message = support.getMessage("brooklyn");
                assertEquals(message, "TEST_MESSAGE");
            }
        });
    }
}
