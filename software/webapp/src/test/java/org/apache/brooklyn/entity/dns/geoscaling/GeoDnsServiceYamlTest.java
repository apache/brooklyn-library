/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.entity.dns.geoscaling;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.entity.dns.AbstractGeoDnsService;
import org.apache.brooklyn.entity.group.DynamicFabric;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

public class GeoDnsServiceYamlTest extends AbstractYamlTest {

    @Test
    public void testTargetGroupCanBeSetInYaml() throws Exception {
        final String resourceName = "classpath:/" + getClass().getPackage().getName().replace('.', '/') + "/geodns.yaml";
        final String blueprint = loadYaml(resourceName);
        Application app = EntityManagementUtils.createUnstarted(mgmt(), blueprint);
        GeoscalingDnsService geodns = Iterables.getOnlyElement(Entities.descendantsAndSelf(app, GeoscalingDnsService.class));
        DynamicFabric fabric = Iterables.getOnlyElement(Entities.descendantsAndSelf(app, DynamicFabric.class));
        assertEquals(geodns.config().get(AbstractGeoDnsService.ENTITY_PROVIDER), fabric);
    }

}
