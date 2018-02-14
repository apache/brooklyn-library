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
package org.apache.brooklyn.entity.proxy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.Inet4Address;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.HasSubnetHostname;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.location.byon.FixedListMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Sub-classes are concrete tests of {@link AbstractController} and {@link AbstractNonProvisionedController},
 * hence the weird double-abstract name!
 */
public abstract class AbstractAbstractControllerTest<T extends LoadBalancer> extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(AbstractAbstractControllerTest.class);
    
    protected FixedListMachineProvisioningLocation<?> loc;
    protected Cluster cluster;
    protected T controller;
    
    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        List<SshMachineLocation> machines = new ArrayList<SshMachineLocation>();
        for (int i = 1; i <= 10; i++) {
            SshMachineLocation machine = mgmt.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                    .configure("address", Inet4Address.getByName("1.1.1."+i)));
            machines.add(machine);
        }
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .configure("machines", machines));
        
        cluster = app.addChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("memberSpec", EntitySpec.create(TestEntity.class).impl(WebServerEntity.class)));
        
        controller = newController();
        
        app.start(ImmutableList.of(loc));
    }
    
    /**
     * Called during {@link #setUp()}, after app and cluster are created (but before app.start is called).
     */
    protected abstract T newController();
    
    protected abstract List<Collection<String>> getUpdates(T controller);
    
    // Fixes bug where entity that wrapped an AS7 entity was never added to nginx because hostname+port
    // was set after service_up. Now we listen to those changes and reset the nginx pool when these
    // values change.
    @Test
    public void testUpdateCalledWhenChildHostnameAndPortChanges() throws Exception {
        log.info("adding child (no effect until up)");
        TestEntity child = cluster.addChild(EntitySpec.create(TestEntity.class));
        cluster.addMember(child);

        List<Collection<String>> u = Lists.newArrayList(getUpdates(controller));
        assertTrue(u.isEmpty(), "expected no updates, but got "+u);
        
        log.info("setting child service_up");
        child.sensors().set(Startable.SERVICE_UP, true);
        // above may trigger error logged about no hostname, but should update again with the settings below
        
        log.info("setting mymachine:1234");
        child.sensors().set(WebServerEntity.HOSTNAME, "mymachine");
        child.sensors().set(Attributes.SUBNET_HOSTNAME, "mymachine");
        child.sensors().set(WebServerEntity.HTTP_PORT, 1234);
        assertEventuallyExplicitAddressesMatch(ImmutableList.of("mymachine:1234"));
        
        /* a race failure has been observed, https://issues.apache.org/jira/browse/BROOKLYN-206
         * but now (two months later) i (alex) can't see how it could happen. 
         * probably optimistic but maybe it is fixed. if not we'll need the debug logs to see what is happening.
         * i've confirmed:
         * * the policy is attached and active during setup, before start completes
         * * the child is added as a member synchronously
         * * the policy which is "subscribed to members" is in fact subscribed to everything
         *   then filtered for members, not ideal, but there shouldn't be a race in the policy getting notices
         * * the handling of those events are both processed in order and look up the current values
         *   rather than relying on the published values; either should be sufficient to cause the addresses to change
         * there was a sleep(100) marked "Ugly sleep to allow AbstractController to detect node having been added"
         * from the test's addition by aled in early 2014, but can't see why that would be necessary
         */
        
        log.info("setting mymachine2:1234");
        child.sensors().set(WebServerEntity.HOSTNAME, "mymachine2");
        child.sensors().set(Attributes.SUBNET_HOSTNAME, "mymachine2");
        assertEventuallyExplicitAddressesMatch(ImmutableList.of("mymachine2:1234"));
        
        log.info("setting mymachine2:1235");
        child.sensors().set(WebServerEntity.HTTP_PORT, 1235);
        assertEventuallyExplicitAddressesMatch(ImmutableList.of("mymachine2:1235"));
        
        log.info("clearing");
        child.sensors().set(WebServerEntity.HOSTNAME, null);
        child.sensors().set(Attributes.SUBNET_HOSTNAME, null);
        assertEventuallyExplicitAddressesMatch(ImmutableList.<String>of());
    }

    @Test
    public void testUpdateCalledWithAddressesOfNewChildren() {
        // First child
        cluster.resize(1);
        Entity child = Iterables.getOnlyElement(cluster.getMembers());
        
        List<Collection<String>> u = Lists.newArrayList(getUpdates(controller));
        assertTrue(u.isEmpty(), "expected empty list but got "+u);
        
        child.sensors().set(WebServerEntity.HTTP_PORT, 1234);
        child.sensors().set(Startable.SERVICE_UP, true);
        assertEventuallyAddressesMatchCluster();

        // Second child
        cluster.resize(2);
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertEquals(cluster.getMembers().size(), 2);
            }});
        Entity child2 = Iterables.getOnlyElement(MutableSet.<Entity>builder().addAll(cluster.getMembers()).remove(child).build());
        
        child2.sensors().set(WebServerEntity.HTTP_PORT, 1234);
        child2.sensors().set(Startable.SERVICE_UP, true);
        assertEventuallyAddressesMatchCluster();
        
        // And remove all children; expect all addresses to go away
        cluster.resize(0);
        assertEventuallyAddressesMatchCluster();
    }

    @Test(groups = "Integration", invocationCount=10)
    public void testUpdateCalledWithAddressesOfNewChildrenManyTimes() {
        testUpdateCalledWithAddressesOfNewChildren();
    }
    
    @Test
    public void testUpdateCalledWithAddressesRemovedForStoppedChildren() {
        // Get some children, so we can remove one...
        cluster.resize(2);
        for (Entity it: cluster.getMembers()) { 
            it.sensors().set(WebServerEntity.HTTP_PORT, 1234);
            it.sensors().set(Startable.SERVICE_UP, true);
        }
        assertEventuallyAddressesMatchCluster();

        // Now remove one child
        cluster.resize(1);
        assertEquals(cluster.getMembers().size(), 1);
        assertEventuallyAddressesMatchCluster();
    }

    @Test
    public void testUpdateCalledWithAddressesRemovedForServiceDownChildrenThatHaveClearedHostnamePort() {
        // Get some children, so we can remove one...
        cluster.resize(2);
        for (Entity it: cluster.getMembers()) { 
            it.sensors().set(WebServerEntity.HTTP_PORT, 1234);
            it.sensors().set(Startable.SERVICE_UP, true);
        }
        assertEventuallyAddressesMatchCluster();

        // Now unset host/port, and remove children
        // Note the unsetting of hostname is done in SoftwareProcessImpl.stop(), so this is realistic
        for (Entity it : cluster.getMembers()) {
            it.sensors().set(WebServerEntity.HTTP_PORT, null);
            it.sensors().set(WebServerEntity.HOSTNAME, null);
            it.sensors().set(Startable.SERVICE_UP, false);
        }
        assertEventuallyAddressesMatch(ImmutableList.<Entity>of());
    }

    @Test
    public void testUpdateCalledWhenServerPoolGroupSwapped() {
        // Second cluster with one child
        DynamicCluster cluster2 = app.addChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("memberSpec", EntitySpec.create(TestEntity.class).impl(WebServerEntity.class)));
        cluster2.start(ImmutableList.of());
        
        Entity child = Iterables.getOnlyElement(cluster2.getMembers());
        child.sensors().set(WebServerEntity.HTTP_PORT, 1234);
        child.sensors().set(Startable.SERVICE_UP, true);

        // Reconfigure the controller to point at the new cluster
        controller.changeServerPool(cluster2.getId());
        assertEquals(controller.config().get(LoadBalancer.SERVER_POOL), cluster2);
        assertEventuallyAddressesMatchCluster(cluster2);

        // And remove all children; expect all addresses to go away
        cluster2.resize(0);
        assertEventuallyAddressesMatchCluster(cluster2);
    }

    protected void assertEventuallyAddressesMatchCluster() {
        assertEventuallyAddressesMatchCluster(cluster);
    }

    protected void assertEventuallyAddressesMatchCluster(Cluster cluster) {
        assertEventuallyAddressesMatch(cluster.getMembers());
    }

    protected void assertEventuallyAddressesMatch(final Collection<Entity> expectedMembers) {
        Asserts.succeedsEventually(new Runnable() {
                @Override public void run() {
                    assertAddressesMatch(locationsToAddresses(1234, expectedMembers));
                }} );
    }

    protected void assertEventuallyExplicitAddressesMatch(final Collection<String> expectedAddresses) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertAddressesMatch(expectedAddresses);
            }} );
    }

    protected void assertAddressesMatch(final Collection<String> expectedAddresses) {
        List<Collection<String>> u = Lists.newArrayList(getUpdates(controller));
        Collection<String> last = Iterables.getLast(u, null);
        log.debug("test "+u.size()+" updates, expecting "+expectedAddresses+"; actual "+last);
        assertTrue(!u.isEmpty(), "no updates; expecting "+expectedAddresses);
        assertEquals(ImmutableSet.copyOf(last), ImmutableSet.copyOf(expectedAddresses), "actual="+last+" expected="+expectedAddresses);
        assertEquals(last.size(), expectedAddresses.size(), "actual="+last+" expected="+expectedAddresses);
    }

    protected Collection<String> locationsToAddresses(int port, Collection<Entity> entities) {
        Set<String> result = MutableSet.of();
        for (Entity e : entities) {
            SshMachineLocation machine = Machines.findUniqueMachineLocation(e.getLocations(), SshMachineLocation.class).get();
            result.add(machine.getAddress().getHostName()+":"+port);
        }
        return result;
    }

    public static class SshMachineLocationWithSubnetHostname extends SshMachineLocation implements HasSubnetHostname {
        @Override public String getSubnetHostname() {
            return getSubnetIp();
        }
        @Override public String getSubnetIp() {
            Set<String> addrs = getPrivateAddresses();
            return (addrs.isEmpty()) ? getAddress().getHostAddress() : Iterables.get(addrs, 0);
        }
    }
    
    public static class WebServerEntity extends TestEntityImpl {
        @SetFromFlag("hostname")
        public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
        
        @SetFromFlag("port")
        public static final AttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT;
        
        @SetFromFlag("hostAndPort")
        public static final AttributeSensor<String> HOST_AND_PORT = Attributes.HOST_AND_PORT;
        
        MachineProvisioningLocation<MachineLocation> provisioner;
        
        @Override
        public void start(Collection<? extends Location> locs) {
            provisioner = (MachineProvisioningLocation<MachineLocation>) locs.iterator().next();
            MachineLocation machine;
            try {
                machine = provisioner.obtain(MutableMap.of());
            } catch (NoMachinesAvailableException e) {
                throw Exceptions.propagate(e);
            }
            addLocations(Arrays.asList(machine));
            sensors().set(HOSTNAME, machine.getAddress().getHostName());
            sensors().set(Attributes.SUBNET_HOSTNAME, machine.getAddress().getHostName());
            sensors().set(Attributes.MAIN_URI_MAPPED_SUBNET, URI.create(machine.getAddress().getHostName()));
            sensors().set(Attributes.MAIN_URI_MAPPED_PUBLIC, URI.create("http://8.8.8.8:" + sensors().get(HTTP_PORT)));
        }

        @Override
        public void stop() {
            Maybe<MachineLocation> machine = Machines.findUniqueMachineLocation(getLocations(), MachineLocation.class);
            if (provisioner != null) {
                provisioner.release(machine.get());
            }
        }
    }
}
