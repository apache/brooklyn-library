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

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Apache ZooKeeper instance.
 */
@Catalog(name="ZooKeeper Node", description="Apache ZooKeeper is a server which enables "
        + "highly reliable distributed coordination.")
@ImplementedBy(ZooKeeperNodeImpl.class)
public interface ZooKeeperNode extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "3.4.10");

    @SetFromFlag("archiveNameFormat")
    ConfigKey<String> ARCHIVE_DIRECTORY_NAME_FORMAT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.ARCHIVE_DIRECTORY_NAME_FORMAT, "zookeeper-%s");

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = ConfigKeys.newSensorAndConfigKeyWithDefault(SoftwareProcess.DOWNLOAD_URL,
            "http://apache.org/dyn/closer.cgi?action=download&filename=zookeeper/zookeeper-${version}/zookeeper-${version}.tar.gz");

    @SetFromFlag("zookeeperPort")
    PortAttributeSensorAndConfigKey ZOOKEEPER_PORT = ConfigKeys.newPortSensorAndConfigKey("zookeeper.port", "Zookeeper port", "2181+");

    @SetFromFlag("zookeeperLeaderPort")
    PortAttributeSensorAndConfigKey ZOOKEEPER_LEADER_PORT = ConfigKeys.newPortSensorAndConfigKey("zookeeper.leader.port", "Zookeeper leader ports", "2888+");

    @SetFromFlag("zookeeperElectionPort")
    PortAttributeSensorAndConfigKey ZOOKEEPER_ELECTION_PORT = ConfigKeys.newPortSensorAndConfigKey("zookeeper.election.port", "Zookeeper election ports", "3888+");

    /**
     * Location of the ZK configuration file template to be copied to the server.
     */
    @SetFromFlag("zookeeperConfig")
    ConfigKey<String> ZOOKEEPER_CONFIG_TEMPLATE = ConfigKeys.newStringConfigKey(
            "zookeeper.configTemplate", "Zookeeper configuration template (in freemarker format)",
            "classpath://org/apache/brooklyn/entity/messaging/zookeeper/zoo.cfg");

    @SetFromFlag("zookeeperId")
    BasicAttributeSensorAndConfigKey<Integer> MY_ID = new BasicAttributeSensorAndConfigKey<>(Integer.class,
            "zookeeper.myid", "ZooKeeper node's myId", 1);

    AttributeSensor<Long> OUTSTANDING_REQUESTS = Sensors.newLongSensor("zookeeper.outstandingRequests", "Outstanding request count");
    AttributeSensor<Long> PACKETS_RECEIVED = Sensors.newLongSensor("zookeeper.packets.received", "Total packets received");
    AttributeSensor<Long> PACKETS_SENT = Sensors.newLongSensor("zookeeper.packets.sent", "Total packets sent");

    AttributeSensor<String> ZOOKEEPER_ENDPOINT = Sensors.newStringSensor(
            "zookeeper.endpoint", "The host:port of the node");

    /** @deprecated since 0.10.0 use <code>sensors().get(ZooKeeperNode.ZOOKEEPER_PORT)</code> instead */
    @Deprecated
    Integer getZookeeperPort();

    /** @deprecated since 0.10.0 use <code>sensors().get(ZooKeeperNode.HOSTNAME)</code> instead */
    @Deprecated
    String getHostname();
}
