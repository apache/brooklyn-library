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
package org.apache.brooklyn.entity.osgi.karaf;

import java.net.URISyntaxException;
import java.util.Map;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.MapConfigKey;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.java.UsesJava;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 * This sets up a Karaf OSGi container
 */
@Catalog(name="Karaf", description="Apache Karaf is a small OSGi based runtime which provides a lightweight container onto which various components and applications can be deployed.", iconUrl="classpath:///karaf-logo.png")
@ImplementedBy(KarafContainerImpl.class)
public interface KarafContainer extends SoftwareProcess, UsesJava, UsesJmx {

    // TODO Better way of setting/overriding defaults for config keys that are defined in super class SoftwareProcess

    String WRAP_SCHEME = "wrap";
    String FILE_SCHEME = "file";
    String MVN_SCHEME = "mvn";
    String HTTP_SCHEME = "http";

    MethodEffector<Map<Long,Map<String,?>>> LIST_BUNDLES = new MethodEffector(KarafContainer.class, "listBundles");
    MethodEffector<Long> INSTALL_BUNDLE = new MethodEffector<Long>(KarafContainer.class, "installBundle");
    MethodEffector<Void> UNINSTALL_BUNDLE = new MethodEffector<Void>(KarafContainer.class, "uninstallBundle");
    MethodEffector<Void> INSTALL_FEATURE = new MethodEffector<Void>(KarafContainer.class, "installFeature");
    MethodEffector<Void> UPDATE_SERVICE_PROPERTIES = new MethodEffector<Void>(KarafContainer.class, "updateServiceProperties");

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(
            SoftwareProcess.SUGGESTED_VERSION, "2.3.0");

    @SetFromFlag("archiveNameFormat")
    ConfigKey<String> ARCHIVE_DIRECTORY_NAME_FORMAT = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.ARCHIVE_DIRECTORY_NAME_FORMAT, "apache-karaf-%s");

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = ConfigKeys.newSensorAndConfigKeyWithDefault(SoftwareProcess.DOWNLOAD_URL,
            "http://apache.mirror.anlx.net/karaf/${version}/apache-karaf-${version}.tar.gz");

    @SetFromFlag("karafName")
    AttributeSensorAndConfigKey<String, String> KARAF_NAME = ConfigKeys.newStringSensorAndConfigKey(
            "karaf.name", "Karaf instance name", "root");

    // TODO too complicated? Used by KarafContainer; was in JavaApp; where should it be in brave new world?
    MapConfigKey<Map<String,String>> NAMED_PROPERTY_FILES = new MapConfigKey(
            Map.class, "karaf.runtime.files", "Property files to be generated, referenced by name relative to runDir");

    @SetFromFlag("jmxUser")
    AttributeSensorAndConfigKey<String, String> JMX_USER = ConfigKeys.newSensorAndConfigKeyWithDefault(
            UsesJmx.JMX_USER, "karaf");

    @SetFromFlag("jmxPassword")
    AttributeSensorAndConfigKey<String, String> JMX_PASSWORD = ConfigKeys.newSensorAndConfigKeyWithDefault(
            UsesJmx.JMX_PASSWORD, "karaf");

    @SetFromFlag("jmxPort")
    PortAttributeSensorAndConfigKey JMX_PORT = ConfigKeys.newPortSensorAndConfigKeyWithDefault(
            UsesJmx.JMX_PORT, "44444+");

    @SetFromFlag("rmiRegistryPort")
    PortAttributeSensorAndConfigKey RMI_REGISTRY_PORT = UsesJmx.RMI_REGISTRY_PORT;

    @SetFromFlag("jmxContext")
    AttributeSensorAndConfigKey<String, String> JMX_CONTEXT = ConfigKeys.newSensorAndConfigKeyWithDefault(
            UsesJmx.JMX_CONTEXT, "karaf-"+KARAF_NAME.getConfigKey().getDefaultValue());

    AttributeSensor<Map> KARAF_INSTANCES = Sensors.newSensor(Map.class, "karaf.admin.instances", "Karaf admin instances");
    AttributeSensor<Boolean> KARAF_ROOT = Sensors.newBooleanSensor("karaf.admin.isRoot", "Karaf admin isRoot");
    AttributeSensor<String> KARAF_JAVA_OPTS = Sensors.newStringSensor("karaf.admin.java_opts", "Karaf Java opts");
    AttributeSensor<String> KARAF_INSTALL_LOCATION  = Sensors.newStringSensor("karaf.admin.location", "Karaf install location");
    AttributeSensor<Integer> KARAF_PID = Sensors.newIntegerSensor("karaf.admin.pid", "Karaf instance PID");
    AttributeSensor<Integer> KARAF_SSH_PORT = Sensors.newIntegerSensor("karaf.admin.ssh_port", "Karaf SSH Port");
    AttributeSensor<Integer> KARAF_RMI_REGISTRY_PORT = Sensors.newIntegerSensor("karaf.admin.rmi_registry_port", "Karaf instance RMI registry port");
    AttributeSensor<Integer> KARAF_RMI_SERVER_PORT = Sensors.newIntegerSensor("karaf.admin.rmi_server_port", "Karaf RMI (JMX) server port");
    AttributeSensor<String> KARAF_STATE = Sensors.newStringSensor("karaf.admin.state", "Karaf instance state");

    @Effector(description="Updates the OSGi Service's properties, adding (and overriding) the given key-value pairs")
    void updateServiceProperties(
            @EffectorParam(name="serviceName", description="Name of the OSGi service") String serviceName,
            Map<String,String> additionalVals);

    @Effector(description="Installs the given OSGi feature")
    void installFeature(
            @EffectorParam(name="featureName", description="Name of the feature - see org.apache.karaf:type=features#installFeature()") final String featureName)
            throws Exception;

    @Effector(description="Lists all the karaf bundles")
    Map<Long,Map<String,?>> listBundles();

    /**
     * throws URISyntaxException If bundle name is not a valid URI
     */
    @Effector(description="Deploys the given bundle, returning the bundle id - see osgi.core:type=framework#installBundle()")
    long installBundle(
            @EffectorParam(name="bundle", description="URI of bundle to be deployed") String bundle) throws URISyntaxException;

    @Effector(description="Undeploys the bundle with the given id")
    void uninstallBundle(
            @EffectorParam(name="bundleId", description="Id of the bundle") Long bundleId);
}
