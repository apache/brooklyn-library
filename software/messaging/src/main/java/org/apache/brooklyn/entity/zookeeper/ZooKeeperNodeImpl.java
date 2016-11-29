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
package org.apache.brooklyn.entity.zookeeper;

import java.net.URI;

import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;

import com.google.common.net.HostAndPort;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single standalone zookeeper instance.
 */
public class ZooKeeperNodeImpl extends AbstractZooKeeperImpl implements ZooKeeperNode {

    public ZooKeeperNodeImpl() {}

    @Override
    public void init() {
        super.init();
        // MY_ID was changed from a sensor to config. Publish it as a sensor to maintain
        // compatibility with any blueprints that reference it.
        Integer myId = config().get(MY_ID);
        if (myId == null) {
            throw new NullPointerException("Require value for " + MY_ID.getName());
        }
        sensors().set(MY_ID, myId);
    }

    @Override
    public Class<?> getDriverInterface() {
        return ZooKeeperDriver.class;
    }

    @Override
    protected void postStart() {
        super.postStart();
        HostAndPort hap = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, sensors().get(ZOOKEEPER_PORT));
        sensors().set(ZooKeeperNode.ZOOKEEPER_ENDPOINT, hap.toString());
        sensors().set(Attributes.MAIN_URI, URI.create("zk://" +hap.toString()));
    }
}
