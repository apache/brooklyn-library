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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.util.guava.Suppliers;

import com.google.common.base.Supplier;

public class ZooKeeperEnsembleImpl extends DynamicClusterImpl implements ZooKeeperEnsemble {

    public ZooKeeperEnsembleImpl() {}

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the ZooKeeper nodes.
     * Overwrites any value configured for {@link ZooKeeperNode#MY_ID} to use
     * the value given by {@link ZooKeeperEnsemble#NODE_ID_SUPPLIER}.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> spec = getConfig(MEMBER_SPEC, EntitySpec.create(ZooKeeperNode.class));
        spec.configure(ZooKeeperNode.MY_ID, config().get(ZooKeeperEnsemble.NODE_ID_SUPPLIER).get());
        return spec;
    }

    @Override
    public String getClusterName() {
        return getAttribute(CLUSTER_NAME);
    }

    @Override
    protected void initEnrichers() {
        super.initEnrichers();
        EnricherSpec<?> zks = Enrichers.builder()
                .aggregating(ZooKeeperNode.ZOOKEEPER_ENDPOINT)
                .publishing(ZOOKEEPER_SERVERS)
                .fromMembers()
                .build();
        EnricherSpec<?> zke = Enrichers.builder()
                .joining(ZOOKEEPER_SERVERS)
                .publishing(ZOOKEEPER_ENDPOINTS)
                .quote(false)
                .separator(",")
                .build();
        enrichers().add(zks);
        enrichers().add(zke);
    }

    /**
     * @deprecated since 0.10.0 class is unused but kept for persistence backwards compatibility
     */
    @Deprecated
    private static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        private final Object[] mutex = new Object[0];

        @Override
        protected void onEntityAdded(Entity member) {
            if (member.config().get(ZooKeeperNode.MY_ID) == null) {
                Supplier<Integer> id;
                synchronized (mutex) {
                    // Entities may not have been created with NODE_ID_SUPPLIER, so create it if
                    // it's not there. We can't provide any good guarantees about what number to
                    // start with, but then again the previous version of the entity gave no
                    // guarantee either.
                    id = entity.config().get(ZooKeeperEnsemble.NODE_ID_SUPPLIER);
                    if (id == null) {
                        id = Suppliers.incrementing();
                        entity.config().set(ZooKeeperEnsemble.NODE_ID_SUPPLIER, id);
                    }
                }
                member.config().set(ZooKeeperNode.MY_ID, id.get());
            }
        }
    }

}
