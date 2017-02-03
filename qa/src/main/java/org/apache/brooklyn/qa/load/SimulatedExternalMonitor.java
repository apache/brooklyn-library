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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.reflect.TypeToken;

@ImplementedBy(SimulatedExternalMonitorImpl.class)
public interface SimulatedExternalMonitor extends Entity, Startable {

    @SuppressWarnings("serial")
    ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = ConfigKeys.newConfigKey(
            new TypeToken<Predicate<? super Entity>>() {},
            "entityFilter",
            "Entities to set the sensors on",
            Predicates.instanceOf(VanillaSoftwareProcess.class));

    ConfigKey<Integer> NUM_SENSORS = ConfigKeys.newIntegerConfigKey(
            "numSensors",
            "Number of attribute sensors to set on each entity",
            1);
    
    ConfigKey<Duration> POLL_PERIOD = ConfigKeys.newConfigKey(
            Duration.class,
            "pollPeriod",
            "Period for polling to get the sensors (delay between polls)",
            Duration.ONE_SECOND);
    
    AttributeSensor<Boolean> SERVICE_UP = Attributes.SERVICE_UP;
}
