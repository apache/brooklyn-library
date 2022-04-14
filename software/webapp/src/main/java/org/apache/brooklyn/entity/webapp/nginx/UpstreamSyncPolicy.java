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
package org.apache.brooklyn.entity.webapp.nginx;

import com.google.common.base.Predicates;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Keeps NGINX 'upstream' server addresses in sync with membership and service-up changes on a corresponding service group.
 */
public class UpstreamSyncPolicy extends AbstractMembershipTrackingPolicy {

    public static ConfigKey<Entity> NGINX_NODE = ConfigKeys.newConfigKey(Entity.class, "nginxNode");
    public static ConfigKey<String> GROUP_NAME = ConfigKeys.newStringConfigKey("groupName");

    @Override
    protected void onEntityEvent(EventType type, Entity entity) {
        defaultHighlightAction(type, entity);

        Entity nginx = config().get(NGINX_NODE);
        Boolean nginxIsUp = nginx.sensors().get(Startable.SERVICE_UP);
        if (!Boolean.TRUE.equals(nginxIsUp))
            return;

        String groupName = config().get(GROUP_NAME);

        List<String> serverAddresses = getEntity().getChildren().stream().map((e) -> {
            Boolean serviceUp = e.sensors().get(Startable.SERVICE_UP);
            String hostName = e.sensors().get(Sensors.newStringSensor("host.name"));
            String port = e.sensors().get(Sensors.newStringSensor("http.port"));
            if (!Boolean.TRUE.equals(serviceUp) || hostName == null || port == null)
                return null;
            return hostName + ":" + port;
        }).filter(Predicates.notNull()).collect(Collectors.toList());

        Maybe<Effector<?>> renderTargets = nginx.getEntityType().getEffectorByName("render-targets");
        Entities.invokeEffectorWithArgs(getEntity(), nginx, renderTargets.get(), groupName, Strings.join(serverAddresses, " "));
    }
}
