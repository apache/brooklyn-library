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

package org.apache.brooklyn.entity.messaging.zookeeper;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HostAndPort;

/**
 * Useful methods for writing to and reading from ZooKeeper nodes.
 */
public class ZooKeeperTestSupport implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperTestSupport.class);
    private final ZooKeeper zk;
    private final CountDownLatch connSignal = new CountDownLatch(1);

    public ZooKeeperTestSupport(final HostAndPort hostAndPort) throws Exception {
        final int sessionTimeout = 3000;
        zk = new ZooKeeper(hostAndPort.toString(), sessionTimeout, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    LOG.debug("Connected to ZooKeeper at {}", hostAndPort);
                    connSignal.countDown();
                } else {
                    LOG.info("WatchedEvent at {}: {}", hostAndPort, event.getState());
                }
            }
        });
        connSignal.await();
    }

    @Override
    public void close() {
        try {
            zk.close();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }

    public String create(String path, byte[] data) throws Exception {
        return zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    public Stat update(String path, byte[] data) throws Exception {
        return zk.setData(path, data, zk.exists(path, true).getVersion());
    }

    public void delete(String path) throws Exception {
        zk.delete(path, zk.exists(path, true).getVersion());
    }

    public byte[] get(String path) throws Exception {
        return zk.getData(path, false, zk.exists(path, false));
    }

}
