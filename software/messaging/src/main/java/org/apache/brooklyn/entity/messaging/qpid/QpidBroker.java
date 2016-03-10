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
package org.apache.brooklyn.entity.messaging.qpid;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.messaging.MessageBroker;
import org.apache.brooklyn.entity.messaging.amqp.AmqpServer;
import org.apache.brooklyn.entity.messaging.jms.JMSBroker;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Qpid broker instance, using AMQP 0-10.
 */
@Catalog(name="Qpid Broker", description="Apache Qpid is an open-source messaging system, implementing the Advanced Message Queuing Protocol (AMQP)", iconUrl="classpath:///qpid-logo.jpeg")
@ImplementedBy(QpidBrokerImpl.class)
public interface QpidBroker extends SoftwareProcess, MessageBroker, UsesJmx, AmqpServer, JMSBroker<QpidQueue, QpidTopic> {

    /* Qpid runtime file locations for convenience. */

    String CONFIG_XML = "etc/config.xml";
    String VIRTUALHOSTS_XML = "etc/virtualhosts.xml";
    String PASSWD = "etc/passwd";

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.20");

    @SetFromFlag("archiveNameFormat")
    ConfigKey<String> ARCHIVE_DIRECTORY_NAME_FORMAT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.ARCHIVE_DIRECTORY_NAME_FORMAT, "qpid-broker-%s");

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = ConfigKeys.newSensorAndConfigKeyWithDefault(SoftwareProcess.DOWNLOAD_URL,
            "http://download.nextag.com/apache/qpid/${version}/qpid-java-broker-${version}.tar.gz");

    @SetFromFlag("amqpVersion")
    AttributeSensorAndConfigKey<String, String> AMQP_VERSION = ConfigKeys.newSensorAndConfigKeyWithDefault(AmqpServer.AMQP_VERSION, AmqpServer.AMQP_0_10);

    @SetFromFlag("httpManagementPort")
    PortAttributeSensorAndConfigKey HTTP_MANAGEMENT_PORT = ConfigKeys.newPortSensorAndConfigKey(
            "qpid.http-management.port", "Qpid HTTP management plugin port");

    @SetFromFlag("jmxUser")
    AttributeSensorAndConfigKey<String, String> JMX_USER = ConfigKeys.newSensorAndConfigKeyWithDefault(UsesJmx.JMX_USER, "admin");

    @SetFromFlag("jmxPassword")
    AttributeSensorAndConfigKey<String, String> JMX_PASSWORD = ConfigKeys.newSensorAndConfigKeyWithDefault(UsesJmx.JMX_PASSWORD, "admin");
}
