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
package org.apache.brooklyn.qa.load;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class SimulatedExternalMonitorImpl extends AbstractEntity implements SimulatedExternalMonitor {

    private ScheduledExecutorService executor;
    private Future<?> future;
    
    @Override
    public void rebind() {
        super.rebind();
        if (Boolean.TRUE.equals(sensors().get(SERVICE_UP))) {
            startPolling();
        }
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        if (Boolean.TRUE.equals(sensors().get(SERVICE_UP))) {
            // already up; no-op
        }
        sensors().set(SERVICE_UP, true);
        startPolling();
    }

    @Override
    public void stop() {
        sensors().set(SERVICE_UP, false);
        stopPolling();
    }

    @Override
    public void restart() {
        stop();
        start(ImmutableList.<Location>of());
    }
    
    protected void startPolling() {
        Duration pollPeriod = config().get(POLL_PERIOD);
        executor = Executors.newScheduledThreadPool(1);
        executor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                simulatePoll();
            }}, 
            0, pollPeriod.toMilliseconds(), TimeUnit.MILLISECONDS);
    }
    
    protected void stopPolling() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
    
    protected void simulatePoll() {
        String val = "val-" + System.currentTimeMillis();
        Predicate<? super Entity> filter = config().get(ENTITY_FILTER);
        Integer numSensors = config().get(NUM_SENSORS);
        Iterable<Entity> entities = Iterables.filter(getManagementContext().getEntityManager().getEntities(), filter);
        for (Entity entity : entities) {
            for (int i = 0; i < numSensors; i++) {
                AttributeSensor<String> sensor = Sensors.newStringSensor("externalSensor"+i);
                entity.sensors().set(sensor, val);
            }
        }
    }
}
