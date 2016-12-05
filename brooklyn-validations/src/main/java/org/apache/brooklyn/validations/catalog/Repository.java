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
package org.apache.brooklyn.validations.catalog;

public class Repository {

    private final String repoUrl;
    private final String repoName;
    private final String author;
    private final String token;

    public Repository(String repoUrl, String repoName, String author) {
        this.repoUrl = repoUrl;
        this.repoName = repoName;
        this.author = author;
        this.token = author + "/" + repoName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getRepoName() {
        return repoName;
    }

    public String getAuthor() {
        return author;
    }

    public String getToken() {
        return token;
    }
}
