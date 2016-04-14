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
package org.apache.brooklyn.entity.nosql.couchdb;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.stream.Streams;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Start a {@link CouchDBNode} in a {@link Location} accessible over ssh.
 */
public class CouchDBNodeSshDriver extends AbstractSoftwareProcessSshDriver implements CouchDBNodeDriver {

    private static final Logger log = LoggerFactory.getLogger(CouchDBNodeSshDriver.class);

    public CouchDBNodeSshDriver(CouchDBNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);

        entity.sensors().set(Attributes.LOG_FILE_LOCATION, getLogFileLocation());
    }

    public String getLogFileLocation() { return Os.mergePathsUnix(getRunDir(), "couchdb.log"); }

    @Override
    public Integer getHttpPort() { return entity.getAttribute(CouchDBNode.HTTP_PORT); }

    @Override
    public Integer getHttpsPort() { return entity.getAttribute(CouchDBNode.HTTPS_PORT); }

    @Override
    public String getClusterName() { return entity.getAttribute(CouchDBNode.CLUSTER_NAME); }

    @Override
    public String getCouchDBConfigTemplateUrl() { return entity.getAttribute(CouchDBNode.COUCHDB_CONFIG_TEMPLATE_URL); }

    @Override
    public String getCouchDBUriTemplateUrl() { return entity.getAttribute(CouchDBNode.COUCHDB_URI_TEMPLATE_URL); }

    @Override
    public String getCouchDBConfigFileName() { return entity.getAttribute(CouchDBNode.COUCHDB_CONFIG_FILE_NAME); }

    public String getErlangVersion() { return entity.getConfig(CouchDBNode.ERLANG_VERSION); }

    @Override
    public void install() {
        log.info("Installing {}", entity);

       List<String> couchdbUrls = resolver.getTargets();
       String coudhdbSaveAs = resolver.getFilename();

       MutableMap<String, String> installGccPackageFlags = MutableMap.of(
               "onlyifmissing", "gcc",
               "yum", "gcc",
               "apt", "gcc",
               "zypper", "gcc gcc-c++",
               "port", null);
       MutableMap<String, String> installMakePackageFlags = MutableMap.of(
               "onlyifmissing", "make",
               "yum", "make",
               "apt", "make",
               "zypper", "make",
               "port", null);
       MutableMap<String, String> installPackageFlags = MutableMap.of(
               "yum", "js-devel openssl-devel libicu-devel libcurl-devel erlang-erts erlang-public_key erlang-eunit erlang-sasl erlang-os_mon erlang-asn1 erlang-xmerl",
               "apt", "erlang-nox erlang-dev libicu-dev libmozjs185-dev libcurl4-openssl-dev",
               "zypper", "libopenssl-devel pcre-devel",
               "port", "icu erlang spidermonkey curl");

       List<String> cmds = Lists.newArrayList();

       cmds.add(BashCommands.INSTALL_TAR);
       cmds.add(BashCommands.alternatives(
               BashCommands.ifExecutableElse0("apt-get", BashCommands.installPackage("build-essential")),
               BashCommands.ifExecutableElse0("yum", BashCommands.sudo("yum -y --nogpgcheck groupinstall \"Development Tools\""))));
       cmds.add(BashCommands.installPackage(installGccPackageFlags, "couchdb-prerequisites-gcc"));
       cmds.add(BashCommands.installPackage(installMakePackageFlags, "couchdb-prerequisites-make"));
       cmds.add(BashCommands.installPackage(installPackageFlags, "couchdb-prerequisites"));
       cmds.addAll(BashCommands.commandsToDownloadUrlsAs(couchdbUrls, coudhdbSaveAs));

       cmds.add(format("tar xvzf %s", coudhdbSaveAs));
       cmds.add(format("cd %s", getExpandedInstallDir()));

       StringBuilder configureCommand = new StringBuilder("./configure")
               .append(format(" --prefix=%s", getRunDir()))
               .append(" --with-erlang=/usr/lib64/erlang/usr/include ");

       cmds.addAll(ImmutableList.of(
               configureCommand.toString(),
               "make install"));

       ScriptHelper script = newScript(INSTALLING)
               .body.append(cmds)
               .header.prepend("set -x")
               .gatherOutput()
               .failOnNonZeroResultCode(false);

       int result = script.execute();

       if (result != 0) {
           String notes = "likely an error building couchdb. consult the brooklyn log ssh output for further details.\n"+
                   "note that this Brooklyn couchdb driver compiles couchdb from source. " +
                   "it attempts to install common prerequisites but this does not always succeed.\n";
           OsDetails os = getMachine().getOsDetails();
           if (os.isMac()) {
               notes += "deploying to Mac OS X, you will require Xcode and Xcode command-line tools, and on " +
                       "some versions the pcre library (e.g. using macports, sudo port install pcre).\n";
           }
           if (os.isWindows()) {
               notes += "this couchdb driver is not designed for windows, unless cygwin is installed, and you are patient.\n";
           }

           if (!script.getResultStderr().isEmpty()) {
               notes += "\n" + "STDERR\n" + script.getResultStderr()+"\n";
               Streams.logStreamTail(log, "STDERR of problem in "+Tasks.current(), Streams.byteArrayOfString(script.getResultStderr()), 1024);
           }
           if (!script.getResultStdout().isEmpty()) {
               notes += "\n" + "STDOUT\n" + script.getResultStdout()+"\n";
               Streams.logStreamTail(log, "STDOUT of problem in "+Tasks.current(), Streams.byteArrayOfString(script.getResultStdout()), 1024);
           }

           Tasks.setExtraStatusDetails(notes.trim());

           throw new IllegalStateException("Installation of couchdb failed (shell returned non-zero result "+result+")");
       }

    }

    @Override
    public Set<Integer> getPortsUsed() {
        Set<Integer> result = Sets.newLinkedHashSet(super.getPortsUsed());
        result.addAll(getPortMap().values());
        return result;
    }

    private Map<String, Integer> getPortMap() {
        return ImmutableMap.<String, Integer>builder()
                .put("httpPort", getHttpPort())
                .build();
    }

    @Override
    public void customize() {
        log.info("Customizing {} (Cluster {})", entity, getClusterName());
        Networking.checkPortsValid(getPortMap());

        newScript(CUSTOMIZING).execute();

        // Copy the configuration files across
        String destinationConfigFile = Os.mergePathsUnix(getRunDir(), getCouchDBConfigFileName());
        copyTemplate(getCouchDBConfigTemplateUrl(), destinationConfigFile);
        String destinationUriFile = Os.mergePathsUnix(getRunDir(), "couch.uri");
        copyTemplate(getCouchDBUriTemplateUrl(), destinationUriFile);
    }

    @Override
    public void launch() {
        log.info("Launching  {}", entity);
        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append(String.format("nohup ./bin/couchdb -p %s -a %s -o couchdb-console.log -e couchdb-error.log -b &", getPidFile(), Os.mergePathsUnix(getRunDir(), getCouchDBConfigFileName())))
                .execute();
    }

    public String getPidFile() { return Os.mergePathsUnix(getRunDir(), "couchdb.pid"); }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, false), CHECK_RUNNING)
                .body.append(String.format("./bin/couchdb -p %s -s", getPidFile()))
                .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, false), STOPPING)
                .body.append(String.format("./bin/couchdb -p %s -k", getPidFile()))
                .failOnNonZeroResultCode()
                .execute();
    }
}
