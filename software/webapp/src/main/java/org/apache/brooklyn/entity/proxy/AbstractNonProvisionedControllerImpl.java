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

import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.feed.ConfigToAttributes;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.proxy.AbstractControllerImpl.MapAttribute;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/** For use by downstream load-balancers. */
public abstract class AbstractNonProvisionedControllerImpl extends AbstractEntity implements AbstractNonProvisionedController {
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNonProvisionedControllerImpl.class);
    
    protected volatile boolean isActive;
    protected volatile boolean updateNeeded = true;
    
    protected AbstractMembershipTrackingPolicy serverPoolMemberTrackerPolicy;
    protected final Object mutex = new Object();
    
    public AbstractNonProvisionedControllerImpl() {
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityEvent(EventType type, Entity member) {
            defaultHighlightAction(type, entity);
            ((AbstractNonProvisionedControllerImpl)super.entity).onServerPoolMemberChanged(member);
        }
    }

    @Override
    public void init() {
        super.init();
        sensors().set(SERVER_POOL_TARGETS, ImmutableMap.<Entity, String>of());
    }

    @Override
    public void rebind() {
        super.rebind();
        
        // For backwards compatibility, in case anything persisted before this was added
        if (sensors().get(SERVER_POOL_TARGETS) == null) {
            sensors().set(SERVER_POOL_TARGETS, ImmutableMap.<Entity, String>of());
        }
    }

    @Override
    public Set<String> getServerPoolAddresses() {
        return ImmutableSet.copyOf(Iterables.filter(getAttribute(SERVER_POOL_TARGETS).values(), Predicates.notNull()));
    }

    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'serverPool'.
     */
    @Override
    public void bind(Map<?,?> flags) {
        if (flags.containsKey("serverPool")) {
            setConfigEvenIfOwned(SERVER_POOL, (Group) flags.get("serverPool"));
        }
    }

    @Override
    public boolean isActive() {
        return isActive;
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        // TODO execute as tasks?
        preStart();
        doStart(locations);
        postStart();
    }

    @Override
    public void stop() {
        // TODO execute as tasks?
        preStop();
        doStop();
        postStop();
    }
    
    protected void preStart() {
        ConfigToAttributes.apply(this);
    }
    
    protected void doStart(Collection<? extends Location> locations) {
    }

    protected void postStart() {
        sensors().set(PROTOCOL, inferProtocol());
        sensors().set(MAIN_URI, URI.create(inferUrl()));
        sensors().set(ROOT_URL, inferUrl());
        addServerPoolMemberTrackingPolicy();
    }
    
    protected void preStop() {
        removeServerPoolMemberTrackingPolicy();
    }

    protected void doStop() {
    }
    
    protected void postStop() {
    }

    protected abstract String inferProtocol();
    
    protected abstract String inferUrl();

    protected void addServerPoolMemberTrackingPolicy() {
        Group serverPool = getServerPool();
        if (serverPool == null) {
            return; // no-op
        }
        if (serverPoolMemberTrackerPolicy != null) {
            LOG.debug("Call to addServerPoolMemberTrackingPolicy when serverPoolMemberTrackingPolicy already exists, removing and re-adding, in {}", this);
            removeServerPoolMemberTrackingPolicy();
        }
        for (Policy p: policies()) {
            if (p instanceof ServerPoolMemberTrackerPolicy) {
                // TODO want a more elegant idiom for this!
                LOG.info(this+" picking up "+p+" as the tracker (already set, often due to rebind)");
                serverPoolMemberTrackerPolicy = (ServerPoolMemberTrackerPolicy) p;
                return;
            }
        }
        
        serverPoolMemberTrackerPolicy = policies().add(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Controller targets tracker")
                .configure("group", serverPool));
        
        AttributeSensor<?> hostAndPortSensor = getConfig(HOST_AND_PORT_SENSOR);
        AttributeSensor<?> hostnameSensor = getConfig(HOSTNAME_SENSOR);
        AttributeSensor<?> portSensor = getConfig(PORT_NUMBER_SENSOR);
        Set<AttributeSensor<?>> sensorsToTrack;
        if (hostAndPortSensor != null) {
            sensorsToTrack = ImmutableSet.<AttributeSensor<?>>of(hostAndPortSensor);
        } else {
            sensorsToTrack = ImmutableSet.<AttributeSensor<?>>of(hostnameSensor, portSensor);
        }
        
        serverPoolMemberTrackerPolicy = policies().add(PolicySpec.create(ServerPoolMemberTrackerPolicy.class)
                .displayName("Controller targets tracker")
                .configure("group", serverPool)
                .configure("sensorsToTrack", sensorsToTrack));

        LOG.info("Added policy {} to {}", serverPoolMemberTrackerPolicy, this);
        
        
        // Initialize ourselves immediately with the latest set of members; don't wait for
        // listener notifications because then will be out-of-date for short period (causing 
        // problems for rebind)
        Map<Entity,String> serverPoolTargets = Maps.newLinkedHashMap();
        for (Entity member : serverPool.getMembers()) {
            if (belongsInServerPool(member)) {
                if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
                String address = getAddressOfEntity(member);
                serverPoolTargets.put(member, address);
            }
        }

        LOG.info("Resetting {}, server pool targets {}", new Object[] {this, serverPoolTargets});
        sensors().set(SERVER_POOL_TARGETS, serverPoolTargets);
    }
    
    protected void removeServerPoolMemberTrackingPolicy() {
        if (serverPoolMemberTrackerPolicy != null) {
            policies().remove(serverPoolMemberTrackerPolicy);
        }
    }
    
    public static class ServerPoolMemberTrackerPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityEvent(EventType type, Entity entity) {
            defaultHighlightAction(type, entity);
            // relies on policy-rebind injecting the implementation rather than the dynamic-proxy
            ((AbstractNonProvisionedControllerImpl)super.entity).onServerPoolMemberChanged(entity);
        }
    }

    /** 
     * Implementations should update the configuration so that 'serverPoolAddresses' are targeted.
     * The caller will subsequently call reload to apply the new configuration.
     */
    protected abstract void reconfigureService();
    
    public void updateNeeded() {
        synchronized (mutex) {
            if (updateNeeded) return;
            updateNeeded = true;
            LOG.debug("queueing an update-needed task for "+this+"; update will occur shortly");
            Entities.submit(this, Tasks.builder().displayName("update-needed").body(new Runnable() {
                @Override
                public void run() {
                    if (updateNeeded)
                        AbstractNonProvisionedControllerImpl.this.update();
                } 
            }).build());
        }
    }
    
    @Override
    public void update() {
        try {
            Task<?> task = updateAsync();
            if (task != null) task.getUnchecked();
            ServiceStateLogic.ServiceProblemsLogic.clearProblemsIndicator(this, "update");
        } catch (Exception e) {
            ServiceStateLogic.ServiceProblemsLogic.updateProblemsIndicator(this, "update", "update failed with: "+Exceptions.collapseText(e));
            throw Exceptions.propagate(e);
        }
    }
    
    public Task<?> updateAsync() {
        synchronized (mutex) {
            Task<?> result = null;
            if (!isActive()) updateNeeded = true;
            else {
                updateNeeded = false;
                LOG.debug("Updating {} in response to changes", this);
                LOG.info("Updating {}, server pool targets {}", new Object[] {this, getAttribute(SERVER_POOL_TARGETS)});
                reconfigureService();
                LOG.debug("Reloading {} in response to changes", this);
                invoke(RELOAD);
            }
            return result;
        }
    }

    
    protected void onServerPoolMemberChanged(Entity member) {
        synchronized (mutex) {
            if (LOG.isTraceEnabled()) LOG.trace("For {}, considering membership of {} which is in locations {}", 
                    new Object[] {this, member, member.getLocations()});
            if (belongsInServerPool(member)) {
                addServerPoolMember(member);
            } else {
                removeServerPoolMember(member);
            }
            if (LOG.isTraceEnabled()) LOG.trace("Done {} checkEntity {}", this, member);
        }
    }
    
    protected boolean belongsInServerPool(Entity member) {
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not up", this, member);
            return false;
        }
        if (!getServerPool().getMembers().contains(member)) {
            if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, eliminating because not member", this, member);
            return false;
        }
        if (LOG.isTraceEnabled()) LOG.trace("Members of {}, checking {}, approving", this, member);
        return true;
    }
    
    private Group getServerPool() {
        return getConfig(SERVER_POOL);
    }
    
    protected AttributeSensor<Integer> getPortNumberSensor() {
        return getAttribute(PORT_NUMBER_SENSOR);
    }
    
    protected AttributeSensor<String> getHostnameSensor() {
        return getAttribute(HOSTNAME_SENSOR);
    }

    protected AttributeSensor<String> getHostAndPortSensor() {
        return getAttribute(HOST_AND_PORT_SENSOR);
    }

    protected void addServerPoolMember(Entity member) {
        synchronized (mutex) {
            String oldAddress = getAttribute(SERVER_POOL_TARGETS).get(member);
            String newAddress = getAddressOfEntity(member);
            if (Objects.equal(newAddress, oldAddress)) {
                if (LOG.isTraceEnabled())
                    if (LOG.isTraceEnabled()) LOG.trace("Ignoring unchanged address {}", oldAddress);
                return;
            } else if (newAddress == null) {
                LOG.info("Removing from {}, member {} with old address {}, because inferred address is now null", new Object[] {this, member, oldAddress});
            } else {
                if (oldAddress != null) {
                    LOG.info("Replacing in {}, member {} with old address {}, new address {}", new Object[] {this, member, oldAddress, newAddress});
                } else {
                    LOG.info("Adding to {}, new member {} with address {}", new Object[] {this, member, newAddress});
                }
            }

            if (Objects.equal(oldAddress, newAddress)) {
                if (LOG.isTraceEnabled()) LOG.trace("For {}, ignoring change in member {} because address still {}", new Object[] {this, member, newAddress});
                return;
            }

            // TODO this does it synchronously; an async method leaning on `updateNeeded` and `update` might
            // be more appropriate, especially when this is used in a listener
            MapAttribute.put(this, SERVER_POOL_TARGETS, member, newAddress);
            updateAsync();
        }
    }

    protected void removeServerPoolMember(Entity member) {
        synchronized (mutex) {
            if (!getAttribute(SERVER_POOL_TARGETS).containsKey(member)) {
                if (LOG.isTraceEnabled()) LOG.trace("For {}, not removing as don't have member {}", new Object[] {this, member});
                return;
            }

            String address = MapAttribute.remove(this, SERVER_POOL_TARGETS, member);

            LOG.info("Removing from {}, member {} with address {}", new Object[] {this, member, address});

            updateAsync();
        }
    }

    protected String getAddressOfEntity(Entity member) {
        AttributeSensor<String> hostAndPortSensor = getHostAndPortSensor();
        if (hostAndPortSensor != null) {
            String result = member.getAttribute(hostAndPortSensor);
            if (result != null) {
                return result;
            } else {
                LOG.error("No host:port set for {} (using attribute {}); skipping in {}", 
                        new Object[] {member, hostAndPortSensor, this});
                return null;
            }
        } else {
            String ip = member.getAttribute(getHostnameSensor());
            Integer port = member.getAttribute(getPortNumberSensor());
            if (ip!=null && port!=null) {
                return ip+":"+port;
            }
            LOG.error("Unable to construct hostname:port representation for {} ({}:{}); skipping in {}", 
                    new Object[] {member, ip, port, this});
            return null;
        }
    }
}
