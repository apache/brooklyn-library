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
package org.apache.brooklyn.entity.webapp.tomcat;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.objs.HasShortName;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.webapp.JavaWebAppService;
import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import org.apache.brooklyn.util.core.ResourcePredicates;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.time.Duration;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Tomcat instance.
 */
@Catalog(name="Tomcat 7 Server",
        description="Apache Tomcat is an open source software implementation of the Java Servlet and JavaServer Pages technologies",
        iconUrl="classpath:///tomcat-logo.png")
@ImplementedBy(TomcatServerImpl.class)
public interface TomcatServer extends JavaWebAppSoftwareProcess, UsesJmx, HasShortName {

    @CatalogConfig(label = "Root WAR")
    @SetFromFlag("war")
    public static final ConfigKey<String> ROOT_WAR = JavaWebAppService.ROOT_WAR;

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "7.0.65");

    @SetFromFlag("archiveNameFormat")
    ConfigKey<String> ARCHIVE_DIRECTORY_NAME_FORMAT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.ARCHIVE_DIRECTORY_NAME_FORMAT, "apache-tomcat-%s");

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = ConfigKeys.newSensorAndConfigKeyWithDefault(SoftwareProcess.DOWNLOAD_URL,
            "http://download.nextag.com/apache/tomcat/tomcat-7/v${version}/bin/apache-tomcat-${version}.tar.gz");

    /**
     * Tomcat insists on having a port you can connect to for the sole purpose of shutting it down.
     * Don't see an easy way to disable it; causes collisions in its out-of-the-box location of 8005,
     * so override default here to a high-numbered port.
     */
    @SetFromFlag("shutdownPort")
    PortAttributeSensorAndConfigKey SHUTDOWN_PORT = ConfigKeys.newPortSensorAndConfigKey(
            "tomcat.shutdownport", 
            "Suggested shutdown port", PortRanges.fromString("31880+"));

    @SetFromFlag("server.xml")
    ConfigKey<String> SERVER_XML_RESOURCE = ConfigKeys.builder(String.class)
            .name("tomcat.serverxml")
            .description("The file to template and use as the Tomcat's server.xml")
            .defaultValue(JavaClassNames.resolveClasspathUrl(TomcatServer.class, "server.xml"))
            .constraint(ResourcePredicates.urlExists())
            .build();

    @SetFromFlag("web.xml")
    ConfigKey<String> WEB_XML_RESOURCE = ConfigKeys.builder(String.class)
            .name("tomcat.webxml")
            .description("The file to template and use as the Tomcat's web.xml")
            .defaultValue(JavaClassNames.resolveClasspathUrl(TomcatServer.class, "web.xml"))
            .constraint(ResourcePredicates.urlExists())
            .build();

    ConfigKey<Duration> START_TIMEOUT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.START_TIMEOUT, Duration.FIVE_MINUTES);

    AttributeSensor<String> CONNECTOR_STATUS = Sensors.newStringSensor(
            "webapp.tomcat.connectorStatus", 
            "Catalina connector state name");

    AttributeSensor<String> JMX_SERVICE_URL = UsesJmx.JMX_URL;

}
