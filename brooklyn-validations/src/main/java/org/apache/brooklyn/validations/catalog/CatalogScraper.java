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

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import org.apache.brooklyn.util.yaml.Yamls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.Map;

public class CatalogScraper {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogScraper.class);

    public static Catalog scrapeCatalog(String repoUrl) {
        List<String> catalogProjectRepoUrls = parseDirectoryYaml(repoUrl);
        Map<String, CatalogProject> scrapedCatalogProjects = Maps.newHashMapWithExpectedSize(catalogProjectRepoUrls
                .size());

        for (String catalogProjectRepoUrl : catalogProjectRepoUrls) {
            Optional<CatalogProject> catalogProject = parseCatalogProject(catalogProjectRepoUrl);

            if (catalogProject.isPresent()) {
                scrapedCatalogProjects.put(catalogProject.get().getToken(), catalogProject.get());
            }
        }

        LOG.info("Scraping complete");

        return new Catalog(scrapedCatalogProjects);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseDirectoryYaml(String repoUrl) {
        Optional<String> directoryYamlString;
        try {
            directoryYamlString = CatalogScraperHelper.getGithubRawText(repoUrl, "directory.yaml", true);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load blueprint catalog.", e);
        }

        return (List) Yamls.parseAll(directoryYamlString.get()).iterator().next();
    }

    public static Optional<CatalogProject> parseCatalogProject(String repoUrl) {
        String[] urlTokens = repoUrl.split("/");
        String repoName = urlTokens[urlTokens.length - 1];
        String author = urlTokens[urlTokens.length - 2];

        try {
            Optional<String> description = CatalogScraperHelper.getGithubRawText(repoUrl, "README.md",
                    true);

            Optional<String> catalogBomString = CatalogScraperHelper.getGithubRawText(repoUrl,
                    "catalog.bom", true);
            @SuppressWarnings("unchecked")
            Map<String, Object> catalogBomYaml = (Map<String, Object>) Yamls.parseAll(catalogBomString.get())
                    .iterator().next();

            Optional<String> documentation = CatalogScraperHelper.getGithubRawText(repoUrl, "items.js",
                    false);

            Optional<String> masterCommitHash = Optional.absent();

            Optional<String> license = Optional.absent();
            String licenseUrl = CatalogScraperHelper.generateRawGithubUrl(repoUrl, "LICENSE.txt");

            if (urlExists(licenseUrl)) {
                license = CatalogScraperHelper.getGithubRawText(repoUrl, "LICENSE.txt", false);
            }

            Optional<String> changelog = Optional.absent();
            String changelogUrl = CatalogScraperHelper.generateRawGithubUrl(repoUrl, "CHANGELOG.md");

            if (urlExists(changelogUrl)) {
                changelog = CatalogScraperHelper.getGithubRawText(repoUrl, "CHANGELOG.md", false);
            }

            CatalogProject catalogProject = new CatalogProject(repoUrl, repoName, author, description.get(),
                    catalogBomString.get(), catalogBomYaml, documentation.orNull(), masterCommitHash.orNull(),
                    license.orNull(), changelog.orNull());

            return Optional.of(catalogProject);
        } catch (IllegalStateException e) {
            CatalogProject catalogProject = new CatalogProject(repoUrl, repoName, author);
            return Optional.of(catalogProject);
        } catch (Exception e) {
            LOG.warn("Failed to parse catalog item repository: '" + repoUrl + "'.", e);
            return Optional.absent();
        }
    }

    private static boolean urlExists(String url) {
        try {
            new URL(url).openConnection().connect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
