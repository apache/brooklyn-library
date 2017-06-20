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
package org.apache.brooklyn.entity.nosql.redis;

import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

//TODO Does it really need to be a live test? When converting from ApplicationBuilder, preserved
//existing behaviour of using the live BrooklynProperties.
public class RedisClusterIntegrationTest extends BrooklynAppLiveTestSupport {

    private Location loc;
    private RedisCluster cluster;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.newLocalhostProvisioningLocation();
    }

    @Test(groups = { "Integration" })
    public void testRedisClusterReplicates() throws Exception {
        final String key = "mykey";
        final String val = "1234567890";
        
        cluster = app.createAndManageChild(EntitySpec.create(RedisCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, 3));
        app.start(ImmutableList.of(loc));

        EntityAsserts.assertAttributeEqualsEventually(cluster, Startable.SERVICE_UP, true);

        RedisStore master = cluster.getMaster();
        List<RedisSlave> slaves = ImmutableList.<RedisSlave>copyOf((Collection)cluster.getSlaves().getMembers());
        
        assertEquals(slaves.size(), 3);
        
        JedisSupport viaMaster = new JedisSupport(master);
        viaMaster.writeData(key, val);
        assertEquals(viaMaster.readData(key), val);

        for (RedisSlave slave : slaves) {
            final JedisSupport viaSlave = new JedisSupport(slave);
            Asserts.succeedsEventually(new Callable<Void>() {
                @Override public Void call() throws Exception {
                    assertEquals(viaSlave.readData(key), val);
                    return null;
                }});
        }

        // Check that stopping slave will not stop anything else
        // (it used to stop master because wasn't supplying port!)
        slaves.get(0).stop();
        EntityAsserts.assertAttributeEqualsEventually(slaves.get(0), Startable.SERVICE_UP, false);
        
        assertEquals(master.getAttribute(Startable.SERVICE_UP), Boolean.TRUE);
        for (RedisSlave slave : slaves.subList(1, slaves.size())) {
            assertEquals(slave.getAttribute(Startable.SERVICE_UP), Boolean.TRUE);
        }
        
        // Check that stopping cluster will stop everything
        cluster.stop();

        EntityAsserts.assertAttributeEqualsEventually(cluster, Startable.SERVICE_UP, false);
        assertEquals(master.getAttribute(Startable.SERVICE_UP), Boolean.FALSE);
        for (RedisSlave slave : slaves) {
            assertEquals(slave.getAttribute(Startable.SERVICE_UP), Boolean.FALSE);
        }
    }
}
