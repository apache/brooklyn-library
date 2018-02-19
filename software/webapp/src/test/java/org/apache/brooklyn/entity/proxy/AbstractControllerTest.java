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

import static org.testng.Assert.assertTrue;

import java.net.Inet4Address;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class AbstractControllerTest extends AbstractAbstractControllerTest<TrackingAbstractController> {

    private static final Logger log = LoggerFactory.getLogger(AbstractControllerTest.class);
    
    @Override
    protected TrackingAbstractController newController() {
        return app.addChild(EntitySpec.create(TrackingAbstractController.class)
                .configure("serverPool", cluster) 
                .configure("portNumberSensor", WebServerEntity.HTTP_PORT)
                .configure("domain", "mydomain"));
    }
    
    @Override
    protected List<Collection<String>> getUpdates(TrackingAbstractController controller) {
        return controller.getUpdates();
    }

    @Test
    public void testUsesHostAndPortSensor() throws Exception {
        controller = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class)
                .configure("serverPool", cluster) 
                .configure("hostAndPortSensor", WebServerEntity.HOST_AND_PORT)
                .configure("domain", "mydomain"));
        controller.start(Arrays.asList(loc));
        
        TestEntity child = cluster.addChild(EntitySpec.create(TestEntity.class));
        cluster.addMember(child);

        List<Collection<String>> u = Lists.newArrayList(controller.getUpdates());
        assertTrue(u.isEmpty(), "expected no updates, but got "+u);
        
        child.sensors().set(Startable.SERVICE_UP, true);
        
        // TODO Ugly sleep to allow AbstractController to detect node having been added
        Thread.sleep(100);
        
        child.sensors().set(WebServerEntity.HOST_AND_PORT, "mymachine:1234");
        assertEventuallyExplicitAddressesMatch(ImmutableList.of("mymachine:1234"));
    }

    @Test
    public void testFailsIfSetHostAndPortAndHostnameOrPortNumberSensor() throws Exception {
        try {
            TrackingAbstractController controller2 = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class)
                    .configure("serverPool", cluster) 
                    .configure("hostAndPortSensor", WebServerEntity.HOST_AND_PORT)
                    .configure("hostnameSensor", WebServerEntity.HOSTNAME)
                    .configure("domain", "mydomain"));
            controller2.start(Arrays.asList(loc));
        } catch (Exception e) {
            IllegalStateException unwrapped = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (unwrapped != null && unwrapped.toString().contains("Must not set Sensor")) {
                // success
            } else {
                throw e;
            }
        }

        try {
            TrackingAbstractController controller3 = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class)
                    .configure("serverPool", cluster) 
                    .configure("hostAndPortSensor", WebServerEntity.HOST_AND_PORT)
                    .configure("portNumberSensor", WebServerEntity.HTTP_PORT)
                    .configure("domain", "mydomain"));
            controller3.start(Arrays.asList(loc));
        } catch (Exception e) {
            IllegalStateException unwrapped = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (unwrapped != null && unwrapped.toString().contains("Must not set Sensor")) {
                // success
            } else {
                throw e;
            }
        }
    }

    // Manual visual inspection test. Previously it repeatedly logged:
    //     Unable to construct hostname:port representation for TestEntityImpl{id=jzwSBRQ2} (null:null); skipping in TrackingAbstractControllerImpl{id=tOn4k5BA}
    // every time the service-up was set to true again.
    @Test
    public void testMemberWithoutHostAndPortDoesNotLogErrorRepeatedly() throws Exception {
        controller = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class)
                .configure("serverPool", cluster) 
                .configure("domain", "mydomain"));
        controller.start(ImmutableList.of(loc));
        
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        cluster.addMember(child);

        for (int i = 0; i < 100; i++) {
            child.sensors().set(Attributes.SERVICE_UP, true);
        }
        
        Thread.sleep(100);
        List<Collection<String>> u = Lists.newArrayList(controller.getUpdates());
        assertTrue(u.isEmpty(), "expected no updates, but got "+u);
    }

    @Test
    public void testMainUriSensorsCorrectlyComputedWithDomain() throws Exception {
        URI expected = URI.create("http://mydomain:8000/");

        EntityAsserts.assertAttributeEquals(controller, TrackingAbstractController.MAIN_URI, expected);
        EntityAsserts.assertAttributeEquals(controller, TrackingAbstractController.MAIN_URI_MAPPED_SUBNET, expected);
        EntityAsserts.assertAttributeEquals(controller, TrackingAbstractController.MAIN_URI_MAPPED_PUBLIC, expected);
    }

    @Test
    public void testMainUriSensorsCorrectlyComputedWithoutDomain() throws Exception {
        // The MachineLocation needs to implement HasSubnetHostname for the Attributes.SUBNET_HOSTNAME 
        // to be set with the subnet addresss (otherwise it will fall back to using machine.getAddress()).
        // See Machines.getSubnetHostname. 
        
        TrackingAbstractController controller2 = app.addChild(EntitySpec.create(TrackingAbstractController.class)
                .configure(TrackingAbstractController.SERVER_POOL, cluster)
                .configure(TrackingAbstractController.PROXY_HTTP_PORT, PortRanges.fromInteger(8081))
                .location(LocationSpec.create(SshMachineLocationWithSubnetHostname.class)
                        .configure("address", Inet4Address.getByName("1.1.1.1"))
                        .configure(SshMachineLocation.PRIVATE_ADDRESSES, ImmutableList.of("2.2.2.2"))));
        controller2.start(ImmutableList.<Location>of());

        EntityAsserts.assertAttributeEquals(controller2, Attributes.ADDRESS, "1.1.1.1");
        EntityAsserts.assertAttributeEquals(controller2, Attributes.SUBNET_ADDRESS, "2.2.2.2");
        EntityAsserts.assertAttributeEquals(controller2, Attributes.MAIN_URI, URI.create("http://2.2.2.2:8081/"));
        EntityAsserts.assertAttributeEquals(controller2, Attributes.MAIN_URI_MAPPED_PUBLIC, URI.create("http://1.1.1.1:8081/"));
        EntityAsserts.assertAttributeEquals(controller2, Attributes.MAIN_URI_MAPPED_SUBNET, URI.create("http://2.2.2.2:8081/"));
    }
}
