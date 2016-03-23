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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.brooklyn.util.net.Networking;
import org.apache.http.client.methods.HttpGet;
import org.bouncycastle.util.Strings;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;

public class ElasticSearchNodeIntegrationTest extends BrooklynAppLiveTestSupport {
    
    protected Location testLocation;
    protected ElasticSearchNode elasticSearchNode;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.newLocalhostProvisioningLocation();
    }

    @Test(groups = {"Integration"})
    public void testStartupAndShutdown() {
        elasticSearchNode = app.createAndManageChild(EntitySpec.create(ElasticSearchNode.class));
        app.start(ImmutableList.of(testLocation));
        
        EntityAsserts.assertAttributeEqualsEventually(elasticSearchNode, Startable.SERVICE_UP, true);
        String url = elasticSearchNode.sensors().get(ElasticSearchNode.DATASTORE_URL);
        assertNotNull(url);
        assertTrue(Networking.isReachable(HostAndPort.fromParts(URI.create(url).getHost(), URI.create(url).getPort())));
        
        elasticSearchNode.stop();
        
        EntityAsserts.assertAttributeEqualsEventually(elasticSearchNode, Startable.SERVICE_UP, false);
    }
    
    @Test(groups = {"Integration"})
    public void testDocumentCount() throws URISyntaxException {
        elasticSearchNode = app.createAndManageChild(EntitySpec.create(ElasticSearchNode.class));
        app.start(ImmutableList.of(testLocation));
        
        EntityAsserts.assertAttributeEqualsEventually(elasticSearchNode, Startable.SERVICE_UP, true);
        
        EntityAsserts.assertAttributeEquals(elasticSearchNode, ElasticSearchNode.DOCUMENT_COUNT, 0);
        
        String baseUri = "http://" + elasticSearchNode.getAttribute(Attributes.HOSTNAME) + ":" + elasticSearchNode.getAttribute(Attributes.HTTP_PORT);
        
        HttpToolResponse pingResponse = HttpTool.execAndConsume(
                HttpTool.httpClientBuilder().build(),
                new HttpGet(baseUri));
        assertEquals(pingResponse.getResponseCode(), 200);
        
        String document = "{\"foo\" : \"bar\",\"baz\" : \"quux\"}";
        
        HttpToolResponse putResponse = HttpTool.httpPut(
                HttpTool.httpClientBuilder()
                    .port(elasticSearchNode.getAttribute(Attributes.HTTP_PORT))
                    .build(), 
                new URI(baseUri + "/mydocuments/docs/1"), 
                ImmutableMap.<String, String>of(), 
                Strings.toByteArray(document)); 
        assertEquals(putResponse.getResponseCode(), 201);
        
        HttpToolResponse getResponse = HttpTool.execAndConsume(
                HttpTool.httpClientBuilder().build(),
                new HttpGet(baseUri + "/mydocuments/docs/1/_source"));
        assertEquals(getResponse.getResponseCode(), 200);
        assertEquals(HttpValueFunctions.jsonContents("foo", String.class).apply(getResponse), "bar");
        
        EntityAsserts.assertAttributeEqualsEventually(elasticSearchNode, ElasticSearchNode.DOCUMENT_COUNT, 1);
    }
}
