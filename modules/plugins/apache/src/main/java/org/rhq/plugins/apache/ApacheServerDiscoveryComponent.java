/*
 * RHQ Management Platform
 * Copyright (C) 2005-2009 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.plugins.apache;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rhq.augeas.node.AugeasNode;
import org.rhq.augeas.tree.AugeasTree;
import org.rhq.augeas.util.Glob;
import org.rhq.augeas.util.GlobFilter;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.Property;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.pluginapi.inventory.DiscoveredResourceDetails;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ManualAddFacet;
import org.rhq.core.pluginapi.inventory.ProcessScanResult;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext;
import org.rhq.core.pluginapi.util.FileUtils;
import org.rhq.core.system.ProcessInfo;
import org.rhq.plugins.apache.parser.ApacheConfigReader;
import org.rhq.plugins.apache.parser.ApacheDirective;
import org.rhq.plugins.apache.parser.ApacheDirectiveTree;
import org.rhq.plugins.apache.parser.ApacheParser;
import org.rhq.plugins.apache.parser.ApacheParserImpl;
import org.rhq.plugins.apache.util.ApacheBinaryInfo;
import org.rhq.plugins.apache.util.AugeasNodeValueUtil;
import org.rhq.plugins.apache.util.HttpdAddressUtility;
import org.rhq.plugins.apache.util.OsProcessUtility;
import org.rhq.plugins.apache.util.HttpdAddressUtility.Address;
import org.rhq.plugins.apache.util.RuntimeApacheConfiguration;
import org.rhq.plugins.platform.PlatformComponent;
import org.rhq.rhqtransform.impl.PluginDescriptorBasedAugeasConfiguration;

/**
 * The discovery component for Apache 2.x servers.
 *
 * @author Ian Springer
 * @author Lukas Krejci
 */
public class ApacheServerDiscoveryComponent implements ResourceDiscoveryComponent<PlatformComponent>, ManualAddFacet<PlatformComponent> {
    private static final String PRODUCT_DESCRIPTION = "Apache Web Server";

    private static final Log log = LogFactory.getLog(ApacheServerDiscoveryComponent.class);

    public static final Map<String, String> MODULE_SOURCE_FILE_TO_MODULE_NAME_20;
    public static final Map<String, String> MODULE_SOURCE_FILE_TO_MODULE_NAME_13;
    
    static {
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20 = new LinkedHashMap<String, String>();
        
        //these are extracted from http://httpd.apache.org/docs/current/mod/
        //and linked pages
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("beos.c", "mpm_beos_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("event.c", "mpm_event_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mpm_netware.c", "mpm_netware_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mpmt_os2.c", "mpm_mpmt_os2_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("prefork.c", "mpm_prefork_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mpm_winnt.c", "mpm_winnt_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("worker.c", "mpm_worker_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_actions.c", "actions_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_alias.c", "alias_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_asis.c", "asis_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_auth_basic.c", "auth_basic_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_auth_digest.c", "auth_digest_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authn_alias.c", "authn_alias_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authn_anon.c", "authn_anon_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authn_dbd.c", "authn_dbd_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authn_dbm.c", "authn_dbm_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authn_default.c", "authn_default_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authn_file.c", "authn_file_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authnz_ldap.c", "authnz_ldap_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authz_dbm.c", "authz_dbm_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authz_default.c", "authz_default_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authz_groupfile.c", "authz_groupfile_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authz_host.c", "authz_host_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authz_owner.c", "authz_owner_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_authz_user.c", "authz_user_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_autoindex.c", "autoindex_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_cache.c", "cache_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_cern_meta.c", "cern_meta_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_cgi.c", "cgi_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_cgid.c", "cgid_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_charset_lite.c", "charset_lite_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_dav.c", "dav_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_dav_fs.c", "dav_fs_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_dav_lock.c", "dav_lock_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_dbd.c", "dbd_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_deflate.c", "deflate_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_dir.c", "dir_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_disk_cache.c", "disk_cache_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_dumpio.c", "dumpio_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_echo.c", "echo_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_env.c", "env_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_example.c", "example_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_expires.c", "expires_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_ext_filter.c", "ext_filter_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_file_cache.c", "file_cache_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_filter.c", "filter_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_headers.c", "headers_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_ident.c", "ident_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_imagemap.c", "imagemap_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_include.c", "include_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_info.c", "info_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_isapi.c", "isapi_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("util_ldap.c", "ldap_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_log_config.c", "log_config_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_log_forensic.c", "log_forensic_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_logio.c", "logio_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_mem_cache.c", "mem_cache_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_mime.c", "mime_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_mime_magic.c", "mime_magic_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_negotiation.c", "negotiation_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_nw_ssl.c", "nwssl_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_proxy.c", "proxy_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_proxy_ajp.c", "proxy_ajp_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_proxy_balancer.c", "proxy_balancer_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_proxy_connect.c", "proxy_connect_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_proxy_ftp.c", "proxy_ftp_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_proxy_http.c", "proxy_http_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_proxy_scgi.c", "proxy_scgi_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_reqtimeout.c", "reqtimeout_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_rewrite.c", "rewrite_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_setenvif.c", "setenvif_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_so.c", "so_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_speling.c", "speling_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_ssl.c", "ssl_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_status.c", "status_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_substitute.c", "substitute_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_suexec.c", "suexec_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_unique_id.c", "unique_id_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_userdir.c", "userdir_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_usertrack.c", "usertrack_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_version.c", "version_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_vhost_alias.c", "vhost_alias_module");
        
        //some hand picked modules
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod_jk.c", "jk_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod-snmpcommon.c", "snmpcommon_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("mod-snmpagt.c", "snmpagt_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_20.put("covalent-snmp-v20.c", "snmp_agt_module");
        
        //this list is for apache 1.3
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13 = new LinkedHashMap<String, String>();
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_access.c", "access_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_actions.c", "action_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_alias.c", "alias_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_asis.c", "asis_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_auth.c", "auth_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_auth_anon.c", "anon_auth_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_auth_db.c", "db_auth_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_auth_dbm.c", "dbm_auth_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_auth_digest.c", "digest_auth_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_autoindex.c", "autoindex_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_cern_meta.c", "cern_meta_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_cgi.c", "cgi_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_digest.c", "digest_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_dir.c", "dir_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_env.c", "env_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_example.c", "example_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_expires.c", "expires_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_headers.c", "headers_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_imap.c", "imap_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_include.c", "includes_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_info.c", "info_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_isapi.c", "isapi_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_log_agent.c", "agent_log_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_log_config.c", "config_log_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_log_forensic.c", "log_forensic_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_log_referer.c", "referer_log_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_mime.c", "mime_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_mime_magic.c", "mime_magic_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_mmap_static.c", "mmap_static_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_negotiation.c", "negotiation_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_proxy.c", "proxy_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_rewrite.c", "rewrite_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_setenvif.c", "setenvif_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_so.c", "so_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_speling.c", "speling_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_status.c", "status_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_unique_id.c", "unique_id_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_userdir.c", "userdir_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_usertrack.c", "usertrack_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_vhost_alias.c", "vhost_alias_module");

        //and some hand-picks
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("mod_jk.c", "jk_module");
        MODULE_SOURCE_FILE_TO_MODULE_NAME_13.put("covalent-snmp-v13.c", "snmp_agt_module");        
    }
        
    private static class DiscoveryFailureException extends Exception {
        private static final long serialVersionUID = 1L;

        public DiscoveryFailureException(String message) {
            super(message);
        }
    }
    
    public Set<DiscoveredResourceDetails>
        discoverResources(ResourceDiscoveryContext<PlatformComponent> discoveryContext) throws Exception {
        Set<DiscoveredResourceDetails> discoveredResources = new HashSet<DiscoveredResourceDetails>();

        // Process any PC-discovered OS processes...
        List<ProcessScanResult> processes = discoveryContext.getAutoDiscoveredProcesses();
        for (ProcessScanResult process : processes) {
            try {
                DiscoveredResourceDetails apache = discoverSingleProcess(discoveryContext, process);
                discoveredResources.add(apache);
            } catch (DiscoveryFailureException e) {
                log.warn("Discovery of Apache process [" + process.getProcessInfo() + "] failed: " + e.getMessage());
            } catch (Exception e) {
                log.error("Discovery of Apache process [" + process.getProcessInfo() + "] failed with an exception.", e);
            }
        }

        return discoveredResources;
    }

    /**
     * Performs discovery on the single process scan result.
     * 
     * @param discoveryContext the discovery context
     * @param process the process discovered by the scan
     * @return resource details
     * @throws DiscoveryFailureException if the discovery failed due to inability to detect necessary data from
     * the process info.
     * @throws Exception other unhandled exception
     */
    private DiscoveredResourceDetails discoverSingleProcess(ResourceDiscoveryContext<PlatformComponent> discoveryContext,
        ProcessScanResult process) throws DiscoveryFailureException, Exception {
        //String executablePath = process.getProcessInfo().getName();
        String executableName = getExecutableName(process);
        File executablePath = OsProcessUtility.getProcExe(process.getProcessInfo().getPid(), executableName);
        if (executablePath == null) {
            throw new DiscoveryFailureException("Executable path could not be determined.");
        }
        if (!executablePath.isAbsolute()) {
            throw new DiscoveryFailureException("Executable path (" + executablePath + ") is not absolute."
                + "Please restart Apache specifying an absolute path for the executable.");
        }
        log.debug("Apache executable path: " + executablePath);
        ApacheBinaryInfo binaryInfo;
        try {
            binaryInfo = ApacheBinaryInfo
                .getInfo(executablePath.getPath(), discoveryContext.getSystemInformation());
        } catch (Exception e) {
            throw new DiscoveryFailureException("'" + executablePath + "' is not a valid Apache executable (" + e + ").");
        }

        if (!isSupportedVersion(binaryInfo.getVersion())) {
            throw new DiscoveryFailureException("Apache " + binaryInfo.getVersion() + " is not suppported.");
        }
        
        String serverRoot = getServerRoot(binaryInfo, process.getProcessInfo());
        if (serverRoot == null) {
            throw new DiscoveryFailureException("Unable to determine server root.");
        }

        File serverConfigFile = getServerConfigFile(binaryInfo, process.getProcessInfo(), serverRoot);
        if (serverConfigFile == null) {
            throw new DiscoveryFailureException("Unable to determine server config file.");
        }

        Configuration pluginConfig = discoveryContext.getDefaultPluginConfiguration();

        PropertySimple executablePathProp = new PropertySimple(
            ApacheServerComponent.PLUGIN_CONFIG_PROP_EXECUTABLE_PATH, executablePath);
        pluginConfig.put(executablePathProp);

        PropertySimple serverRootProp = new PropertySimple(
            ApacheServerComponent.PLUGIN_CONFIG_PROP_SERVER_ROOT, serverRoot);
        pluginConfig.put(serverRootProp);

        PropertySimple configFile = new PropertySimple(ApacheServerComponent.PLUGIN_CONFIG_PROP_HTTPD_CONF,
            serverConfigFile);
        pluginConfig.put(configFile);

        PropertySimple inclusionGlobs = new PropertySimple(
            PluginDescriptorBasedAugeasConfiguration.INCLUDE_GLOBS_PROP, serverConfigFile);
        pluginConfig.put(inclusionGlobs);

        ApacheDirectiveTree serverConfig = loadParser(serverConfigFile.getAbsolutePath(), serverRoot);
        serverConfig = RuntimeApacheConfiguration.extract(serverConfig, process.getProcessInfo(), binaryInfo, getDefaultModuleNames(binaryInfo.getVersion()));
        
        String serverUrl = null;
        String vhostsGlobInclude = null;

        //now check if the httpd.conf doesn't redefine the ServerRoot
        List<ApacheDirective> serverRoots = serverConfig.search("/ServerRoot");
        if (!serverRoots.isEmpty()) {
            serverRoot = AugeasNodeValueUtil.unescape(serverRoots.get(0).getValuesAsString());
            serverRootProp.setValue(serverRoot);
            //reparse the configuration with the new ServerRoot
            serverConfig = loadParser(serverConfigFile.getAbsolutePath(), serverRoot);
            serverConfig = RuntimeApacheConfiguration.extract(serverConfig, process.getProcessInfo(), binaryInfo, getDefaultModuleNames(binaryInfo.getVersion()));
        }

        serverUrl = getUrl(serverConfig, binaryInfo.getVersion());
        vhostsGlobInclude = scanForGlobInclude(serverConfig);

        if (serverUrl != null) {
            Property urlProp = new PropertySimple(ApacheServerComponent.PLUGIN_CONFIG_PROP_URL, serverUrl);
            pluginConfig.put(urlProp);
        }

        if (vhostsGlobInclude != null) {
            pluginConfig.put(new PropertySimple(ApacheServerComponent.PLUGIN_CONFIG_PROP_VHOST_FILES_MASK,
                vhostsGlobInclude));
        } else {
            if (serverConfigFile.exists())
                pluginConfig.put(new PropertySimple(ApacheServerComponent.PLUGIN_CONFIG_PROP_VHOST_FILES_MASK,
                    serverConfigFile.getParent() + File.separator + "*"));
        }
       
        return createResourceDetails(discoveryContext, pluginConfig, process.getProcessInfo(),
            binaryInfo);
    }



    public DiscoveredResourceDetails discoverResource(Configuration pluginConfig,
                                                      ResourceDiscoveryContext<PlatformComponent> discoveryContext)
            throws InvalidPluginConfigurationException {
        validateServerRootAndServerConfigFile(pluginConfig);

        String executablePath = pluginConfig
            .getSimpleValue(ApacheServerComponent.PLUGIN_CONFIG_PROP_EXECUTABLE_PATH,
                ApacheServerComponent.DEFAULT_EXECUTABLE_PATH);
        String absoluteExecutablePath = ApacheServerComponent.resolvePathRelativeToServerRoot(pluginConfig,
            executablePath).getPath();
        ApacheBinaryInfo binaryInfo;
        try {
            binaryInfo = ApacheBinaryInfo.getInfo(absoluteExecutablePath, discoveryContext.getSystemInformation());
        } catch (Exception e) {
            throw new InvalidPluginConfigurationException("'" + absoluteExecutablePath
                + "' is not a valid Apache executable (" + e + "). Please make sure the '"
                + ApacheServerComponent.PLUGIN_CONFIG_PROP_EXECUTABLE_PATH
                + "' connection property is set correctly.");
        }

        if (!isSupportedVersion(binaryInfo.getVersion())) {
            throw new InvalidPluginConfigurationException("Version of Apache executable ("
                + binaryInfo.getVersion() + ") is not a supported version; supported versions are 1.3.x and 2.x.");
        }

        ProcessInfo processInfo = null;
        try {
            DiscoveredResourceDetails resourceDetails = createResourceDetails(discoveryContext, pluginConfig,
                processInfo, binaryInfo);
            return resourceDetails;
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create resource details during manual add.");
        }
    }

    private boolean isSupportedVersion(String version) {
        // TODO: Compare against a version range defined in the plugin descriptor.
        return (version != null) && (version.startsWith("1.3") || version.startsWith("2."));
    }

    private DiscoveredResourceDetails createResourceDetails(ResourceDiscoveryContext<PlatformComponent> discoveryContext,
        Configuration pluginConfig, ProcessInfo processInfo, ApacheBinaryInfo binaryInfo) throws Exception {
        String httpdConf = pluginConfig.getSimple(ApacheServerComponent.PLUGIN_CONFIG_PROP_HTTPD_CONF).getStringValue();
        String version = binaryInfo.getVersion();
        String serverUrl = pluginConfig.getSimpleValue(ApacheServerComponent.PLUGIN_CONFIG_PROP_URL, null);
        //use the server url if we could detect it, otherwise use something unique
        String name;
        if (serverUrl == null) {
            name = httpdConf;
        } else {
            URI uri = new URI(serverUrl);
            name = uri.getHost() + ":" + uri.getPort(); 
        }

        String serverRoot = pluginConfig.getSimple(ApacheServerComponent.PLUGIN_CONFIG_PROP_SERVER_ROOT)
            .getStringValue();
        
        String key = FileUtils.getCanonicalPath(serverRoot);

        //BZ 612189 - reverting to the serverRoot as the resource key temporarily until we have the resource
        //upgrade functionality ready (BZ 592038). Uncommenting the line below will make the resource key totally unique
        //(see BZ 593270).
        //key += "|" + httpdConf;
        
        DiscoveredResourceDetails resourceDetails = new DiscoveredResourceDetails(discoveryContext.getResourceType(),
            key, name, version, PRODUCT_DESCRIPTION, pluginConfig, processInfo);
        log.debug("Apache Server resource details created: " + resourceDetails);
        return resourceDetails;
    }

    /**
     * Return the root URL as determined from the Httpd configuration loaded by Augeas.
     * he URL's protocol is assumed to be "http" and its path is assumed to be "/".
     *  
     * @return
     *
     * @throws Exception
     */
    private static String getUrl(ApacheDirectiveTree serverConfig, String version) throws Exception {
        Address addr = HttpdAddressUtility.get(version).getMainServerSampleAddress(serverConfig, null, 0);
        return addr == null ? null : addr.toString();
    }

   
    @Nullable
    public static String getServerRoot(@NotNull ApacheBinaryInfo binaryInfo, @NotNull ProcessInfo processInfo) {
        // First see if -d was specified on the httpd command line.
        String[] cmdLine = processInfo.getCommandLine();
        String root = getCommandLineOption(cmdLine, "-d");

        // If not, extract the path from the httpd binary.
        if (root == null) {
            root = binaryInfo.getRoot();
        }

        if (root == null) {
            // We have failed to determine the server root  :(
            return null;
        }

        // If the path is relative, convert it to an absolute path, resolving it relative to the cwd of the httpd process.
        File rootFile = new File(root);
        if (!rootFile.isAbsolute()) {
            String currentWorkingDir;
            try {
                currentWorkingDir = processInfo.getCurrentWorkingDirectory();
            } catch (Exception e) {
                log.error("Unable to determine current working directory of Apache process [" + processInfo
                        + "], which is needed to determine the server root of the Apache instance.", e);
                return null;
            }
            if (currentWorkingDir == null) {
                log.error("Unable to determine current working directory of Apache process [" + processInfo
                        + "], which is needed to determine the server root of the Apache instance.");
                return null;
            } else {
                rootFile = new File(currentWorkingDir, root);
                root = rootFile.getPath();
            }
        }

        // And finally canonicalize the path, but using our own getCanonicalPath() method, which preserves symlinks.
        root = FileUtils.getCanonicalPath(root);

        return root;
    }

    @Nullable
    public static File getServerConfigFile(ApacheBinaryInfo binaryInfo, ProcessInfo processInfo, String serverRoot) {
        // First see if -f was specified on the httpd command line.
        String[] cmdLine = processInfo.getCommandLine();
        String serverConfigFile = getCommandLineOption(cmdLine, "-f");

        // If not, extract the path from the httpd binary.
        if (serverConfigFile == null) {
            serverConfigFile = binaryInfo.getCtl();
        }

        if (serverConfigFile == null) {
            // We have failed to determine the config file path  :(
            return null;
        }

        // If the path is relative, convert it to an absolute path, resolving it relative to the server root dir.
        File file = new File(serverConfigFile);
        if (!file.isAbsolute()) {
            file = new File(serverRoot, serverConfigFile);
            serverConfigFile = file.getPath();
        }

        // And finally canonicalize the path, but using our own getCanonicalPath() method, which preserves symlinks.
        serverConfigFile = FileUtils.getCanonicalPath(serverConfigFile);

        return new File(serverConfigFile);
    }

    private static String getCommandLineOption(String[] cmdLine, String option) {
        String root = null;
        for (int i = 1; i < cmdLine.length; i++) {
            String arg = cmdLine[i];
            if (arg.startsWith(option)) {
                root = arg.substring(2, arg.length());
                if (root.length() == 0) {
                    root = cmdLine[i + 1];
                }
                break;
            }
        }
        return root;
    }

    private static String getExecutableName(ProcessScanResult processScanResult) {
        String query = processScanResult.getProcessScan().getQuery().toLowerCase();
        String executableName;
        if (query.contains("apache.exe")) {
            executableName = "apache.exe";
        } else if (query.contains("httpd.exe")) {
            executableName = "httpd.exe";
        } else if (query.contains("apache2")) {
            executableName = "apache2";
        } else if (query.contains("httpd")) {
            executableName = "httpd";
        } else {
            executableName = null;
        }
        return executableName;
    }

    private static void validateServerRootAndServerConfigFile(Configuration pluginConfig) {
        String serverRoot = pluginConfig.getSimple(ApacheServerComponent.PLUGIN_CONFIG_PROP_SERVER_ROOT).getStringValue();
        File serverRootFile;
        try {
            serverRootFile = new File(serverRoot).getCanonicalFile(); // this will resolve symlinks
        }
        catch (IOException e) {
            serverRootFile = null;
        }
        if (serverRootFile == null || !serverRootFile.isDirectory()) {
            throw new InvalidPluginConfigurationException("'" + serverRoot
                + "' does not exist or is not a directory. Please make sure the '"
                + ApacheServerComponent.PLUGIN_CONFIG_PROP_SERVER_ROOT + "' connection property is set correctly.");
        }
        String httpdConf = pluginConfig.getSimple(ApacheServerComponent.PLUGIN_CONFIG_PROP_HTTPD_CONF).getStringValue();
        File httpdConfFile;
        try {
            httpdConfFile = new File(httpdConf).getCanonicalFile(); // this will resolve symlinks
        }
        catch (IOException e) {
            httpdConfFile = null;
        }
        if (httpdConfFile == null || !httpdConfFile.isFile()) {
            throw new InvalidPluginConfigurationException("'" + httpdConf
                + "' does not exist or is not a regular file. Please make sure the '"
                + ApacheServerComponent.PLUGIN_CONFIG_PROP_HTTPD_CONF + "' connection property is set correctly.");
        }
    }
    
    private static ApacheDirectiveTree loadParser(String path,String serverRoot) throws Exception{

        ApacheDirectiveTree tree = new ApacheDirectiveTree();
        ApacheParser parser = new ApacheParserImpl(tree,serverRoot);
        ApacheConfigReader.buildTree(path, parser);
        return tree;
    }
    
    public static String scanForGlobInclude(ApacheDirectiveTree tree) {
        try {
            List<ApacheDirective> includes = tree.search("/Include");
            for (ApacheDirective n : includes) {
                String include = n.getValuesAsString();
                if (Glob.isWildcard(include)) {
                    //we only take the '*.something' into account here
                    //so that we have a useful mask to base the file names on.
                    
                    //the only special glob character allowed is *.
                    for(char specialChar : GlobFilter.WILDCARD_CHARS) {
                        if (specialChar == '*') {
                            if (include.indexOf(specialChar) != include.lastIndexOf(specialChar)) {
                                //more than 1 star... that's too much
                                break;
                            }
                            //we found what we're looking for...
                            return include;
                        }
                        if (include.indexOf(specialChar) >= 0) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to detect glob includes in httpd.conf.", e);
        }
        return null;
    }
    
    public static Map<String, String> getDefaultModuleNames(String version) {
        switch (HttpdAddressUtility.get(version)) {
        case APACHE_1_3 : 
            return MODULE_SOURCE_FILE_TO_MODULE_NAME_13;
        case APACHE_2_x:
            return MODULE_SOURCE_FILE_TO_MODULE_NAME_20;
        default: throw new IllegalStateException("Unknown HttpdAddressUtility instance.");
        }        
    }    
}
