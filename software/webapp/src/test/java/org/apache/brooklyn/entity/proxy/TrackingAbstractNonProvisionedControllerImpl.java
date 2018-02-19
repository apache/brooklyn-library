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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.util.text.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class TrackingAbstractNonProvisionedControllerImpl extends AbstractNonProvisionedControllerImpl implements TrackingAbstractNonProvisionedController {
    
    private static final Logger log = LoggerFactory.getLogger(TrackingAbstractNonProvisionedControllerImpl.class);

    private final List<Collection<String>> updates = Lists.newCopyOnWriteArrayList();

    @Override
    public void start(Collection<? extends Location> locations) {
        sensors().set(HOSTNAME, Identifiers.makeRandomId(8) + ".test.brooklyn.apache.org");
        super.start(locations);
    }
    
    @Override
    public List<Collection<String>> getUpdates() {
        return updates;
    }
    
    @Override
    protected void reconfigureService() {
        Set<String> addresses = getServerPoolAddresses();
        log.info("test controller reconfigure, targets "+addresses);
        if ((!addresses.isEmpty() && updates.isEmpty()) || (!updates.isEmpty() && addresses != updates.get(updates.size()-1))) {
            updates.add(addresses);
        }
    }

    @Override
    public void reload() {
        // no-op
    }

    @Override
    public void restart() {
        // no-op
    }

    @Override
    protected String inferProtocol() {
        String result = config().get(PROTOCOL);
        return (result == null ? result : result.toLowerCase());
    }

    @Override
    protected String inferUrl() {
        String scheme = inferProtocol();
        Integer port = sensors().get(PROXY_HTTP_PORT);
        String domainName = sensors().get(HOSTNAME);
        return scheme + "://" + domainName + ":" + port;
    }
}
