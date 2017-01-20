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
package org.apache.brooklyn.entity.webapp;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.entitlement.EntitlementClass;
import org.apache.brooklyn.api.mgmt.entitlement.EntitlementContext;
import org.apache.brooklyn.api.mgmt.entitlement.EntitlementManager;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.entitlement.Entitlements;
import org.apache.brooklyn.core.mgmt.internal.EffectorUtils;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.proxy.TrackingAbstractController;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.test.LogWatcher;
import org.apache.brooklyn.test.LogWatcher.EventPredicates;
import org.apache.brooklyn.test.entity.TestJavaWebAppEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * See https://issues.apache.org/jira/browse/BROOKLYN-313
 * 
 * That problems was first encountered with auto-scaling a {@link ControlledDynamicWebAppCluster},
 * hence testing it explicitly here.
 */
public class EffectorExceptionLoggedTest extends BrooklynAppUnitTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(EffectorExceptionLoggedTest.class);

    private LocalhostMachineProvisioningLocation loc;

    public static class ThrowingEntitlementManager implements EntitlementManager {
        @Override 
        public <T> boolean isEntitled(EntitlementContext context, EntitlementClass<T> entitlementClass, T entitlementClassArgument) {
            if (context == null || context.user() == null) {
                LOG.info("Simulating NPE in entitlement manager");
                throw new NullPointerException();
            }
            return true;
        }
    }

    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        loc = app.newLocalhostProvisioningLocation();
    }

    @Override
    protected BrooklynProperties getBrooklynProperties() {
        BrooklynProperties result = BrooklynProperties.Factory.newEmpty();
        result.put(Entitlements.GLOBAL_ENTITLEMENT_MANAGER, ThrowingEntitlementManager.class.getName());
        return result;
    }
    
    // Marked as integration, because takes over a second
    @Test(groups="Integration")
    public void testResizeFailsWhenEntitlementThrowsShouldLogException() throws Exception {
        final AttributeSensor<Integer> scalingMetric = Sensors.newIntegerSensor("scalingMetric");
        
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(TrackingAbstractController.class))
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class))
                .policy(PolicySpec.create(AutoScalerPolicy.class)
                        .configure(AutoScalerPolicy.METRIC, scalingMetric)
                        .configure(AutoScalerPolicy.MIN_POOL_SIZE, 1)
                        .configure(AutoScalerPolicy.MAX_POOL_SIZE, 2)
                        .configure(AutoScalerPolicy.METRIC_LOWER_BOUND, 1)
                        .configure(AutoScalerPolicy.METRIC_UPPER_BOUND, 10)));
        
        Entitlements.setEntitlementContext(new EntitlementContext() {
                @Override
                public String user() {
                    return "myuser";
                }});
        app.start(ImmutableList.of(loc));
        Entitlements.clearEntitlementContext();

        String loggerName = EffectorUtils.class.getName();
        ch.qos.logback.classic.Level logLevel = ch.qos.logback.classic.Level.DEBUG;
        Predicate<ILoggingEvent> filter = Predicates.and(EventPredicates.containsMessage("Error invoking "), 
                EventPredicates.containsExceptionStackLine(ThrowingEntitlementManager.class, "isEntitled"));
        LogWatcher watcher = new LogWatcher(loggerName, logLevel, filter);
                
        watcher.start();
        try {
            // Cause the auto-scaler to resize, which will fail because of the bad entitlement implementation
            cluster.sensors().set(scalingMetric, 50);
            watcher.assertHasEventEventually();
        } finally {
            watcher.close();
        }
    }

}
