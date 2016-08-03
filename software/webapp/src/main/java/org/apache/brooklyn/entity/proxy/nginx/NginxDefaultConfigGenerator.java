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
package org.apache.brooklyn.entity.proxy.nginx;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import org.apache.brooklyn.entity.proxy.ProxySslConfig;
import org.apache.brooklyn.util.text.Strings;

/**
 * Generates the {@code server.conf} configuration file using sensors on an {@link NginxController}.
 */
public class NginxDefaultConfigGenerator implements NginxConfigFileGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(NginxDefaultConfigGenerator.class);

    public NginxDefaultConfigGenerator() { }

    @Override
    public String generateConfigFile(NginxDriver driver, NginxController nginx) {
        StringBuilder config = new StringBuilder();
        config.append("\n")
                .append("pid ").append(driver.getPidFile()).append(";\n")
                .append("events {\n")
                .append("  worker_connections 8196;\n")
                .append("}\n")
                .append("http {\n")
                .append("  map $http_upgrade $connection_upgrade {\n")
                .append("    default Upgrade;\n")
                .append("    ''      close;\n")
                .append("  }\n");

        ProxySslConfig globalSslConfig = nginx.getSslConfig();

        if (nginx.isSsl()) {
            verifyConfig(globalSslConfig);
            appendSslConfig("global", config, "  ", globalSslConfig, true, true);
        }

        // If no servers, then defaults to returning 404
        // TODO Give nicer page back
        if (Strings.isNonEmpty(nginx.getDomain()) || nginx.getServerPoolAddresses() == null || nginx.getServerPoolAddresses().isEmpty()) {
            config.append("  server {\n")
                    .append(getCodeForServerConfig())
                    .append("    listen ").append(nginx.getPort()).append(";\n")
                    .append(getCodeFor404())
                    .append("  }\n");
        }

        // For basic round-robin across the server-pool
        if (nginx.getServerPoolAddresses() != null && nginx.getServerPoolAddresses().size() > 0) {
            config.append("  upstream ").append(nginx.getId()).append(" {\n");
            if (nginx.isSticky()){
                config.append("    sticky;\n");
            }
            for (String address : nginx.getServerPoolAddresses()) {
                config.append("    server "+address+";\n");
            }
            config.append("  }\n");
            config.append("  server {\n");
            config.append(getCodeForServerConfig());
            if (globalSslConfig != null) {
                appendCodeForProxySSLConfig(nginx.getId(), config, "    ", globalSslConfig);
            }
            config.append("    listen ").append(nginx.getPort()).append(";\n");
            if (Strings.isNonEmpty(nginx.getDomain())) {
                config.append("    server_name ").append(nginx.getDomain()).append(";\n");
            }
            config.append("    location / {\n");
            config.append("      proxy_pass ");
            if (globalSslConfig != null && globalSslConfig.getTargetIsSsl()) {
                config.append("https");
            } else {
                config.append("http");
            }
            config.append("://").append(nginx.getId()).append(";\n");
            config.append("    }\n");
            config.append("  }\n");
        }

        // For mapping by URL
        Iterable<UrlMapping> mappings = nginx.getUrlMappings();
        Multimap<String, UrlMapping> mappingsByDomain = LinkedHashMultimap.create();
        for (UrlMapping mapping : mappings) {
            Collection<String> addrs = mapping.getAttribute(UrlMapping.TARGET_ADDRESSES);
            if (addrs != null && addrs.size() > 0) {
                mappingsByDomain.put(mapping.getDomain(), mapping);
            }
        }

        for (UrlMapping um : mappings) {
            Collection<String> addrs = um.getAttribute(UrlMapping.TARGET_ADDRESSES);
            if (addrs != null && addrs.size() > 0) {
                config.append("  upstream ").append(um.getUniqueLabel()).append(" {\n");
                if (nginx.isSticky()){
                    config.append("    sticky;\n");
                }
                for (String address: addrs) {
                    config.append("    server ").append(address).append(";\n");
                }
                config.append("  }\n");
            }
        }

        for (String domain : mappingsByDomain.keySet()) {
            config.append("  server {\n")
                    .append(getCodeForServerConfig())
                    .append("    listen ").append(nginx.getPort()).append(";\n")
                    .append("    server_name ").append(domain).append(";\n");

            // set up SSL
            ProxySslConfig localSslConfig = null;
            for (UrlMapping mappingInDomain : mappingsByDomain.get(domain)) {
                ProxySslConfig sslConfig = mappingInDomain.getConfig(UrlMapping.SSL_CONFIG);
                if (sslConfig != null) {
                    verifyConfig(sslConfig);
                    if (localSslConfig != null) {
                        if (localSslConfig.equals(sslConfig)) {
                            //ignore identical config specified on multiple mappings
                        } else {
                            LOG.warn("{} mapping {} provides SSL config for {} when a different config had already been provided by another mapping, ignoring this one",
                                    new Object[] { this, mappingInDomain, domain });
                        }
                    } else if (globalSslConfig != null) {
                        if (globalSslConfig.equals(sslConfig)) {
                            //ignore identical config specified on multiple mappings
                        } else {
                            LOG.warn("{} mapping {} provides SSL config for {} when a different config had been provided at root nginx scope, ignoring this one",
                                    new Object[] { this, mappingInDomain, domain });
                        }
                    } else {
                        //new config, is okay
                        localSslConfig = sslConfig;
                    }
                }
            }
            if (localSslConfig != null) {
                appendSslConfig(domain, config, "    ", localSslConfig, true, true);
                appendCodeForProxySSLConfig(domain, config, "    ", localSslConfig);
            }

            boolean hasRoot = false;
            for (UrlMapping mappingInDomain : mappingsByDomain.get(domain)) {
                // TODO Currently only supports "~" for regex. Could add support for other options,
                // such as "~*", "^~", literals, etc.
                boolean isRoot = Strings.isEmpty(mappingInDomain.getPath()) || mappingInDomain.getPath().equals("/");
                if (isRoot && hasRoot) {
                    LOG.warn("{} mapping {} provides a duplicate / proxy, ignoring", this, mappingInDomain);
                } else {
                    hasRoot |= isRoot;
                    String location = isRoot ? "/" : "~ " + mappingInDomain.getPath();
                    config.append("    location ").append(location).append(" {\n");
                    Collection<UrlRewriteRule> rewrites = mappingInDomain.getConfig(UrlMapping.REWRITES);
                    if (rewrites != null && rewrites.size() > 0) {
                        for (UrlRewriteRule rule: rewrites) {
                            config.append("      rewrite \"^").append(rule.getFrom()).append("$\" \"").append(rule.getTo()).append("\"");
                            if (rule.isBreak()) config.append(" break");
                            config.append(" ;\n");
                        }
                    }
                    config.append("      proxy_pass ");
                    if (localSslConfig != null && localSslConfig.getTargetIsSsl()) {
                        config.append("https");
                    } else if (localSslConfig == null && globalSslConfig != null && globalSslConfig.getTargetIsSsl()) {
                        config.append("https");
                    } else {
                        config.append("http");
                    }
                    config.append("://").append(mappingInDomain.getUniqueLabel()).append(" ;\n");
                    config.append("    }\n");
                }
            }
            if (!hasRoot) {
                //provide a root block giving 404 if there isn't one for this server
                config.append("    location / { \n")
                        .append(getCodeFor404())
                        .append("    }\n");
            }
            config.append("  }\n");
        }

        config.append("}\n");

        return config.toString();
    }

    protected String getCodeForServerConfig() {
        // See http://wiki.nginx.org/HttpProxyModule
        return
            // this prevents nginx from reporting version number on error pages
            "    server_tokens off;\n"+

            // this prevents nginx from using the internal proxy_pass codename as Host header passed upstream.
            // Not using $host, as that causes integration test to fail with a "connection refused" testing
            // url-mappings, at URL "http://localhost:${port}/atC0" (with a trailing slash it does work).
            "    proxy_set_header Host $http_host;\n"+

            // Sets the HTTP protocol version for proxying and include connection upgrade headers
            "    proxy_http_version 1.1;\n"+
            "    proxy_set_header Upgrade $http_upgrade;\n"+
            "    proxy_set_header Connection $connection_upgrade;\n"+

            // following added, as recommended for wordpress in:
            // http://zeroturnaround.com/labs/wordpress-protips-go-with-a-clustered-approach/#!/
            "    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n"+
            "    proxy_set_header X-Real-IP $remote_addr;\n";
    }

    protected void appendCodeForProxySSLConfig(String id, StringBuilder out, String prefix, ProxySslConfig ssl) {
        if (ssl.getTargetIsSsl() && ssl.getVerifyClient()) {
            // Send 403 if not verified, otherwise pass on the client certificate we received
            out.append(prefix).append("if ($ssl_client_verify != SUCCESS) { return 403; }\n");
            out.append(prefix).append("proxy_set_header ssl.client_cert $ssl_client_cert;\n");

            // Use the configured SSL certificate and key for the proxied server
            String cert;
            if (Strings.isEmpty(ssl.getCertificateDestination())) {
                cert = id + ".crt";
            } else {
                cert = ssl.getCertificateDestination();
            }
            out.append(prefix).append("proxy_ssl_certificate ").append(cert).append(";\n");

            String key;
            if (Strings.isNonEmpty(ssl.getKeyDestination())) {
                key = ssl.getKeyDestination();
            } else if (Strings.isNonEmpty(ssl.getKeySourceUrl())) {
                key = id + ".key";
            } else {
                key = null;
            }
            if (key != null) {
                out.append(prefix).append("proxy_ssl_certificate_key ").append(key).append(";\n");
            }
        }
    }

    protected String getCodeFor404() {
        return "    return 404;\n";
    }

    protected void verifyConfig(ProxySslConfig proxySslConfig) {
          if(Strings.isEmpty(proxySslConfig.getCertificateDestination()) && Strings.isEmpty(proxySslConfig.getCertificateSourceUrl())){
            throw new IllegalStateException("ProxySslConfig can't have a null certificateDestination and null certificateSourceUrl. One or both need to be set");
        }
    }

    protected boolean appendSslConfig(String id, StringBuilder out, String prefix, ProxySslConfig ssl,
                                   boolean sslBlock, boolean certificateBlock) {
        if (ssl == null) return false;
        if (sslBlock) {
            out.append(prefix).append("ssl on;\n");
        }
        if (ssl.getReuseSessions()) {
            out.append(prefix).append("proxy_ssl_session_reuse on;\n");
        } else {
            out.append(prefix).append("proxy_ssl_session_reuse off;\n");
        }
        if (certificateBlock) {
            String cert;
            if (Strings.isEmpty(ssl.getCertificateDestination())) {
                cert = id + ".crt";
            } else {
                cert = ssl.getCertificateDestination();
            }
            out.append(prefix).append("ssl_certificate ").append(cert).append(";\n");

            String key;
            if (!Strings.isEmpty(ssl.getKeyDestination())) {
                key = ssl.getKeyDestination();
            } else if (!Strings.isEmpty(ssl.getKeySourceUrl())) {
                key = id + ".key";
            } else {
                key = null;
            }
            if (key != null) {
                out.append(prefix).append("ssl_certificate_key ").append(key).append(";\n");
            }

            if (ssl.getVerifyClient()) {
                out.append(prefix).append("ssl_verify_client on;\n");

                String client;
                if (Strings.isEmpty(ssl.getClientCertificateDestination())) {
                    client = id + ".cli";
                } else {
                    client = ssl.getClientCertificateDestination();
                }
                if (client != null) {
                    out.append(prefix).append("ssl_client_certificate ").append(client).append(";\n");
                }
            }

            out.append(prefix).append("ssl_protocols TLSv1 TLSv1.1 TLSv1.2;\n");
        }
        return true;
    }

    @Override
    public String toString(){
        return getClass().getName();
    }

}
