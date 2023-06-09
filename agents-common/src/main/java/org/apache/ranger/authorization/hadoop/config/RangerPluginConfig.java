/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.authorization.hadoop.config;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;
import org.apache.ranger.authorization.utils.StringUtil;
import org.apache.ranger.plugin.policyengine.RangerPolicyEngineOptions;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;


public class RangerPluginConfig extends RangerConfiguration {
    private static final Logger LOG = Logger.getLogger(RangerPluginConfig.class);

    private static final char RANGER_TRUSTED_PROXY_IPADDRESSES_SEPARATOR_CHAR = ',';

    private final String                    serviceType;
    private final String                    serviceName;
    private final String                    appId;
    private final String                    clusterName;
    private final String                    clusterType;
    private final RangerPolicyEngineOptions policyEngineOptions;
    private final boolean                   useForwardedIPAddress;
    private final String[]                  trustedProxyAddresses;
    private final String                    propertyPrefix;
    private       Set<String>               auditExcludedUsers  = Collections.emptySet();
    private       Set<String>               auditExcludedGroups = Collections.emptySet();
    private       Set<String>               auditExcludedRoles  = Collections.emptySet();
    private       Set<String>               superUsers          = Collections.emptySet();
    private       Set<String>               superGroups         = Collections.emptySet();


    public RangerPluginConfig(String serviceType, String serviceName, String appId, String clusterName, String clusterType, RangerPolicyEngineOptions policyEngineOptions) {
        super();

        addResourcesForServiceType(serviceType);

        this.serviceType    = serviceType;
        this.appId          = StringUtils.isEmpty(appId) ? serviceType : appId;
        this.propertyPrefix = "ranger.plugin." + serviceType;
        this.serviceName    = StringUtils.isEmpty(serviceName) ? this.get(propertyPrefix + ".service.name") : serviceName;

        addResourcesForServiceName(this.serviceType, this.serviceName);

        String trustedProxyAddressString = this.get(propertyPrefix + ".trusted.proxy.ipaddresses");

        if (StringUtil.isEmpty(clusterName)) {
            clusterName = this.get(propertyPrefix + ".access.cluster.name", "");

            if (StringUtil.isEmpty(clusterName)) {
                clusterName = this.get(propertyPrefix + ".ambari.cluster.name", "");
            }
        }

        if (StringUtil.isEmpty(clusterType)) {
            clusterType = this.get(propertyPrefix + ".access.cluster.type", "");

            if (StringUtil.isEmpty(clusterType)) {
                clusterType = this.get(propertyPrefix + ".ambari.cluster.type", "");
            }
        }

        this.clusterName           = clusterName;
        this.clusterType           = clusterType;
        this.useForwardedIPAddress = this.getBoolean(propertyPrefix + ".use.x-forwarded-for.ipaddress", false);
        this.trustedProxyAddresses = StringUtils.split(trustedProxyAddressString, RANGER_TRUSTED_PROXY_IPADDRESSES_SEPARATOR_CHAR);

        if (trustedProxyAddresses != null) {
            for (int i = 0; i < trustedProxyAddresses.length; i++) {
                trustedProxyAddresses[i] = trustedProxyAddresses[i].trim();
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(propertyPrefix + ".use.x-forwarded-for.ipaddress:" + useForwardedIPAddress);
            LOG.debug(propertyPrefix + ".trusted.proxy.ipaddresses:[" + StringUtils.join(trustedProxyAddresses, ", ") + "]");
        }

        if (useForwardedIPAddress && StringUtils.isBlank(trustedProxyAddressString)) {
            LOG.warn("Property " + propertyPrefix + ".use.x-forwarded-for.ipaddress" + " is set to true, and Property " + propertyPrefix + ".trusted.proxy.ipaddresses" + " is not set");
            LOG.warn("Ranger plugin will trust RemoteIPAddress and treat first X-Forwarded-Address in the access-request as the clientIPAddress");
        }

        if (policyEngineOptions == null) {
            policyEngineOptions = new RangerPolicyEngineOptions();

            policyEngineOptions.configureForPlugin(this, propertyPrefix);
        }

        this.policyEngineOptions = policyEngineOptions;

        LOG.info(policyEngineOptions);
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getAppId() {
        return appId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getClusterType() {
        return clusterType;
    }

    public boolean isUseForwardedIPAddress() {
        return useForwardedIPAddress;
    }

    public String[] getTrustedProxyAddresses() {
        return trustedProxyAddresses;
    }

    public String getPropertyPrefix() {
        return propertyPrefix;
    }

    public RangerPolicyEngineOptions getPolicyEngineOptions() {
        return policyEngineOptions;
    }

    public void setAuditExcludedUsersGroupsRoles(Set<String> users, Set<String> groups, Set<String> roles) {
        auditExcludedUsers  = CollectionUtils.isEmpty(users) ? Collections.emptySet() : new HashSet<>(users);
        auditExcludedGroups = CollectionUtils.isEmpty(groups) ? Collections.emptySet() : new HashSet<>(groups);
        auditExcludedRoles  = CollectionUtils.isEmpty(groups) ? Collections.emptySet() : new HashSet<>(roles);

        if (LOG.isDebugEnabled()) {
            LOG.debug("auditExcludedUsers=" + auditExcludedUsers + ", auditExcludedGroups=" + auditExcludedGroups + ", auditExcludedRoles=" + auditExcludedRoles);
        }
    }

    public void setSuperUsersGroups(Set<String> users, Set<String> groups) {
        superUsers  = CollectionUtils.isEmpty(users) ? Collections.emptySet() : new HashSet<>(users);
        superGroups = CollectionUtils.isEmpty(groups) ? Collections.emptySet() : new HashSet<>(groups);

        if (LOG.isDebugEnabled()) {
            LOG.debug("superUsers=" + superUsers + ", superGroups=" + superGroups);
        }
    }

    public boolean isAuditExcludedUser(String userName) {
        return auditExcludedUsers.contains(userName);
    }

    public boolean hasAuditExcludedGroup(Set<String> userGroups) {
        return userGroups != null && userGroups.size() > 0 && auditExcludedGroups.size() > 0 && CollectionUtils.containsAny(userGroups, auditExcludedGroups);
    }

    public boolean hasAuditExcludedRole(Set<String> userRoles) {
        return userRoles != null && userRoles.size() > 0 && auditExcludedRoles.size() > 0 && CollectionUtils.containsAny(userRoles, auditExcludedRoles);
    }

    public boolean isSuperUser(String userName) {
        return superUsers.contains(userName);
    }

    public boolean hasSuperGroup(Set<String> userGroups) {
        return userGroups != null && userGroups.size() > 0 && superGroups.size() > 0 && CollectionUtils.containsAny(userGroups, superGroups);
    }
    
	// An highlighted block
	private void copyConfigFile(String serviceType) {
		// 这个方法用来适配CDH版本的组件，非CDH组件需要跳出
		if (serviceType.toUpperCase().equals("PRESTO")) {
			return;
		}
		// 环境变量
		Map map = System.getenv();
		Iterator it = map.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			LOG.info("env key: " + entry.getKey() + ", value: " + entry.getValue());
		}
		// 系统变量
		Properties properties = System.getProperties();
		Iterator itr = properties.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry entry = (Map.Entry) itr.next();
			LOG.info("system key: " + entry.getKey() + ", value: " + entry.getValue());
		}

		String serviceHome = "CDH_" + serviceType.toUpperCase() + "_HOME";
		if ("CDH_HDFS_HOME".equals(serviceHome)) {
			serviceHome = "CDH_HADOOP_HOME";
		}

		serviceHome = System.getenv(serviceHome);
		File serviceHomeDir = new File(serviceHome);
		String userDir = System.getenv("CONF_DIR");
		File destDir = new File(userDir);

		LOG.info("-----Service Home: " + serviceHome);
		LOG.info("-----User dir: " + userDir);
		LOG.info("-----Dest dir: " + destDir);

		IOFileFilter regexFileFilter = new RegexFileFilter("ranger-.+xml");
		Collection<File> configFileList = FileUtils.listFiles(serviceHomeDir, regexFileFilter, TrueFileFilter.INSTANCE);
		boolean flag = true;
		for (File rangerConfigFile : configFileList) {
			try {
				if ((serviceType.toUpperCase().equals("HIVE") || serviceType.toUpperCase().equals("HDFS")) && flag) {
					File file = new File(rangerConfigFile.getParentFile().getPath() + "/xasecure-audit.xml");
					FileUtils.copyFileToDirectory(file, destDir);
					flag = false;
				}
				FileUtils.copyFileToDirectory(rangerConfigFile, destDir);
			} catch (IOException e) {
				LOG.error("Copy ranger config file failed.", e);
			}
		}
	}


    private void addResourcesForServiceType(String serviceType) {
    	
    	copyConfigFile(serviceType);
    	
        String auditCfg    = "ranger-" + serviceType + "-audit.xml";
        String securityCfg = "ranger-" + serviceType + "-security.xml";
        String sslCfg 	   = "ranger-" + serviceType + "-policymgr-ssl.xml";

        if (!addResourceIfReadable(auditCfg)) {
            addAuditResource(serviceType);
        }

        if (!addResourceIfReadable(securityCfg)) {
            addSecurityResource(serviceType);
        }

        if (!addResourceIfReadable(sslCfg)) {
            addSslConfigResource(serviceType);
        }
    }

    // load service specific config overrides, if config files are available
    private void addResourcesForServiceName(String serviceType, String serviceName) {
        if (StringUtils.isNotBlank(serviceType) && StringUtils.isNotBlank(serviceName)) {
            String serviceAuditCfg    = "ranger-" + serviceType + "-" + serviceName + "-audit.xml";
            String serviceSecurityCfg = "ranger-" + serviceType + "-" + serviceName + "-security.xml";
            String serviceSslCfg      = "ranger-" + serviceType + "-" + serviceName + "-policymgr-ssl.xml";

            addResourceIfReadable(serviceAuditCfg);
            addResourceIfReadable(serviceSecurityCfg);
            addResourceIfReadable(serviceSslCfg);
        }
    }

    private void  addSecurityResource(String serviceType) {
        if(LOG.isDebugEnabled()) {
            LOG.debug("==> addSecurityResource(Service Type: " + serviceType );
        }

        Configuration rangerConf = RangerLegacyConfigBuilder.getSecurityConfig(serviceType);

        if (rangerConf != null ) {
            addResource(rangerConf);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to add the Security Config for " + serviceType + ". Plugin won't be enabled!");
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<= addSecurityResource(Service Type: " + serviceType );
        }
    }

    private void  addAuditResource(String serviceType) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("==> addAuditResource(Service Type: " + serviceType );
        }

        try {
            URL url = RangerLegacyConfigBuilder.getAuditConfig(serviceType);

            if (url != null) {
                addResource(url);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("==> addAuditResource() URL" + url.getPath());
                }
            }

        } catch (Throwable t) {
            LOG.warn("Unable to find Audit Config for "  + serviceType + " Auditing not enabled !" );

            if(LOG.isDebugEnabled()) {
                LOG.debug("Unable to find Audit Config for "  + serviceType + " Auditing not enabled !" + t);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== addAuditResource(Service Type: " + serviceType + ")");
        }
    }

    private void addSslConfigResource(String serviceType) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> addSslConfigResource(Service Type: " + serviceType);
        }

        try {
            String sslConfigFile = this.get(RangerLegacyConfigBuilder.getPropertyName(RangerConfigConstants.RANGER_PLUGIN_REST_SSL_CONFIG_FILE, serviceType));

            URL url = getSSLConfigResource(sslConfigFile);
            if (url != null) {
                addResource(url);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("SSL config file URL:" + url.getPath());
                }
            }
        } catch (Throwable t) {
            LOG.warn(" Unable to find SSL Configs");

            if (LOG.isDebugEnabled()) {
                LOG.debug(" Unable to find SSL Configs");
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== addSslConfigResource(Service Type: " + serviceType + ")");
        }
    }

    private URL getSSLConfigResource(String fileName) throws Throwable {
        URL ret = null;

        try {
            if (fileName != null) {
                File f = new File(fileName);
                if (f.exists() && f.canRead()) {
                    ret = f.toURI().toURL();
                }
            }
        } catch (Throwable t) {
            LOG.error("Unable to read SSL configuration file:" + fileName);

            throw t;
        }

        return ret;
    }
}
