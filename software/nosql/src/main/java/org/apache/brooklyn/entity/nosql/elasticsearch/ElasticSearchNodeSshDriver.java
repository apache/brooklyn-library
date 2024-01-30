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
package org.apache.brooklyn.entity.nosql.elasticsearch;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import com.google.common.collect.ImmutableList;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;

public class ElasticSearchNodeSshDriver extends JavaSoftwareProcessSshDriver implements ElasticSearchNodeDriver {

    public ElasticSearchNodeSshDriver(ElasticSearchNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void install() {
        final String tmpLimitsFile = Os.mergePaths(getRunDir(), "99-elasticsearch-limits.conf");
        final String tmpSysctlFile = Os.mergePaths(getRunDir(), "99-elasticsearch-sysctl.conf");
        copyResource("classpath://org/apache/brooklyn/entity/nosql/elasticsearch/99-elasticsearch-limits.conf", tmpLimitsFile);
        copyResource("classpath://org/apache/brooklyn/entity/nosql/elasticsearch/99-elasticsearch-sysctl.conf", tmpSysctlFile);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        
        List<String> commands = ImmutableList.<String>builder()
            .add(BashCommands.sudo(String.format("cp %s %s", tmpLimitsFile, "/etc/security/limits.d/99-elasticsearch.conf")))
            .add(BashCommands.sudo(String.format("cp %s %s", tmpSysctlFile, "/etc/sysctl.d/99-elasticsearch.conf")))
            .add(BashCommands.sudo(String.format("sysctl --load %s", "/etc/sysctl.d/99-elasticsearch.conf")))
            .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
            .add(String.format("tar zxvf %s", saveAs))
            .build();
        
        newScript(INSTALLING).body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).execute();  //create the directory
        
        String configFileUrl = entity.getConfig(ElasticSearchNode.TEMPLATE_CONFIGURATION_URL);
        
        if (configFileUrl == null) {
            return;
        }

        String configScriptContents = processTemplate(configFileUrl);
        Reader configContents = new StringReader(configScriptContents);

        getMachine().copyTo(configContents, Urls.mergePaths(getRunDir(), getConfigFile()));
    }

    @Override
    public void launch() {
        String pidFile = getRunDir() + "/" + AbstractSoftwareProcessSshDriver.PID_FILENAME;
        entity.sensors().set(ElasticSearchNode.PID_FILE, pidFile);
        StringBuilder commandBuilder = new StringBuilder()
            .append(String.format("%s/bin/elasticsearch -d -p %s", getExpandedInstallDir(), pidFile));
        commandBuilder.append(" -Enetwork.host=" + getEntity().sensors().get(SoftwareProcess.ADDRESS));
        if (entity.getConfig(ElasticSearchNode.TEMPLATE_CONFIGURATION_URL) != null) {
            commandBuilder.append(" -Epath.conf=" + Os.mergePaths(getRunDir(), getConfigFile()));
        }
        appendConfigIfPresent(commandBuilder, "path.data", ElasticSearchNode.DATA_DIR, Os.mergePaths(getRunDir(), "data"));
        appendConfigIfPresent(commandBuilder, "path.logs", ElasticSearchNode.LOG_DIR, Os.mergePaths(getRunDir(), "logs"));
        appendConfigIfPresent(commandBuilder, "node.name", ElasticSearchNode.NODE_NAME.getConfigKey());
        appendConfigIfPresent(commandBuilder, "cluster.name", ElasticSearchNode.CLUSTER_NAME.getConfigKey());
        appendConfigIfPresent(commandBuilder, "discovery.zen.ping.multicast.enabled", ElasticSearchNode.MULTICAST_ENABLED);
        appendConfigIfPresent(commandBuilder, "discovery.zen.ping.unicast.enabled", ElasticSearchNode.UNICAST_ENABLED);
        commandBuilder.append(" > out.log 2> err.log < /dev/null");
        final List<String> cmds = ImmutableList.of(
                String.format("for p in $( pidof bash ); do echo set prlimit on process $p; %s; done", BashCommands.sudo("prlimit --nofile=65536:65536 --pid=$p")),
                commandBuilder.toString());
        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(cmds)
            .execute();
    }
    
    private void appendConfigIfPresent(StringBuilder builder, String parameter, ConfigKey<?> configKey) {
        appendConfigIfPresent(builder, parameter, configKey, null);
    }
    
    private void appendConfigIfPresent(StringBuilder builder, String parameter, ConfigKey<?> configKey, String defaultValue) {
        String config = null;
        if (entity.getConfig(configKey) != null) {
            config = String.valueOf(entity.getConfig(configKey));
        }
        if (config == null && defaultValue != null) {
            config = defaultValue;
        }
        if (config != null) {
            builder.append(String.format(" -E%s=%s", parameter, config));
        }
    }
    
    public String getConfigFile() {
        return "elasticsearch.yaml";
    }
    
    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", true), CHECK_RUNNING).execute() == 0;
    }
    
    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", true), STOPPING).execute();
    }
    
    @Override
    public void kill() {
        newScript(MutableMap.of("usePidFile", true), KILLING).execute();
    }

    @Override
    protected String getLogFileLocation() {
        String  logFileLocation = entity.config().get(ElasticSearchNode.LOG_DIR);
        return (logFileLocation != null) ? logFileLocation : Os.mergePaths(getRunDir(), "logs");
    }
}
