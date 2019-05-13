/*
 ********************************************************************************
 * Copyright (c) 9th November 2018 Cloudreach Limited Europe
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 ********************************************************************************/
package org.eclipse.jemo.internal.model;

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.sys.ClusterParams;
import org.eclipse.jemo.sys.internal.TerraformJob;
import org.eclipse.jemo.sys.internal.TerraformJob.TerraformResult;
import org.eclipse.jemo.sys.internal.Util;
import io.kubernetes.client.ApiException;
import org.eclipse.jemo.sys.JemoRuntimeSetup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public interface CloudRuntime {

    String defineQueue(String queueName);

    void storeModuleList(String moduleJar, List<String> moduleList) throws Throwable;

    List<String> getModuleList(String moduleJar) throws Throwable;

    CloudBlob getModule(String moduleJar) throws IOException;

    Long getModuleInstallDate(String moduleJar) throws IOException;

    void setModuleInstallDate(String moduleJar, long installDate) throws IOException;

    void log(List<CloudLogEvent> eventList);

    Set<String> listPlugins();

    void uploadModule(String pluginFile, byte[] pluginBytes);

    void uploadModule(String pluginFile, InputStream in, long moduleSize);

    default void removeModule(String pluginFile) {
        remove(null, pluginFile);
    }

    default String createInstanceQueue(final String location, final String instanceId) {
        return defineQueue("JEMO-" + location + "-" + instanceId);
    }

    void deleteQueue(String queueId);

    String sendMessage(String queueId, String jsonMessage);

    /**
     * this method will return the corresponding id for the queueName which was passed,
     * this method does not guarantee that the queue actually exists but will return an id that can be used for the other methods.
     * for cloud vendors who cannot generate the id of the queue but instead need to look it up using the api's this method
     * can return null.
     *
     * @param queueName the name of the queue to fetch the id for.
     * @return the unique id representing the queue.
     */
    String getQueueId(String queueName);

    default List<String> listQueueIds(String location) {
        return listQueueIds(location, false);
    }

    /**
     * this method will return the name of a queue given it's cloud native identifier
     *
     * @param queueId the id of the queue as identified by the cloud provider
     * @return the name of the queue
     */
    String getQueueName(String queueId);

    List<String> listQueueIds(String location, boolean includeWorkQueues);

    int pollQueue(String queueId, CloudQueueProcessor processor) throws QueueDoesNotExistException;

    boolean hasNoSQLTable(String tableName);

    void createNoSQLTable(String tableName);

    void dropNoSQLTable(String tableName);

    <T extends Object> List<T> listNoSQL(String tableName, Class<T> objectType);

    <T extends Object> List<T> queryNoSQL(String tableName, Class<T> objectType, String... pkList);

    <T extends Object> T getNoSQL(String tableName, String id, Class<T> objectType) throws IOException;

    void saveNoSQL(String tableName, SystemDBObject... data);

    void deleteNoSQL(String tableName, SystemDBObject... data);

    void watchdog(final String location, final String instanceId, final String instanceQueueUrl);

    void setModuleConfiguration(int pluginId, ModuleConfiguration config);

    Map<String, String> getModuleConfiguration(int pluginId);

    void store(String key, Object data);

    <T extends Object> T retrieve(String key, Class<T> objType);

    void store(String category, String key, Object data);

    <T extends Object> T retrieve(String category, String key, Class<T> objType);

    void delete(String category, String key);

    String getDefaultCategory();

    //we added support to the runtime for data streaming to big data repositories.
    default InputStream read(String path, String key) {
        return read(getDefaultCategory(), path, key);
    }

    default <T extends Object> T read(Class<T> returnType, String path, String key) {
        return read(returnType, getDefaultCategory(), path, key);
    }

    default <T extends Object> T read(Class<T> returnType, String category, String path, String key) {
        return Util.F(read(category, path, key), input -> input == null ? null : Util.fromJSONString(returnType, Util.toString(input)));
    }

    default <T extends Object> Stream<T> readAll(Class<T> type, String path) {
        return readAll(type, getDefaultCategory(), path);
    }

    default <T extends Object> Stream<T> readAll(Class<T> type, String category, String path) {
        return readAll(category, path).map(input -> Util.F(null, x -> Util.fromJSONString(type, Util.toString(input))));
    }

    default void write(String path, String key, Object data) {
        write(getDefaultCategory(), path, key, data);
    }

    default void write(String category, String path, String key, Object data) {
        Util.B(null, x -> write(category, path, key, new ByteArrayInputStream(Util.toJSONString(data).getBytes("UTF-8"))));
    }

    default void write(String path, String key, InputStream dataStream) {
        write(getDefaultCategory(), path, key, dataStream);
    }

    default void remove(String path, String key) {
        remove(getDefaultCategory(), path, key);
    }

    void write(String category, String path, String key, InputStream dataStream);

    InputStream read(String category, String path, String key);

    Stream<InputStream> readAll(String category, String path);

    void remove(String category, String path, String key);

    /**
     * this method should validate that this provider is active and has the correct security settings for the application to function correctly.
     *
     * @return a validation result object listing all the not permitted actions if any
     */
    ValidationResult validatePermissions();

    /**
     * Validates the credentials.
     *
     * @param credentials
     * @return a validation result object listing all the not permitted actions if any
     */
    ValidationResult validateCredentials(Map<String, String> credentials);

    /**
     * Writes the credentials from the specified map to common location for cloud cli tooling.
     *
     * @param credentials a map containing the credentials needed by this runtime in the form of (credential_name, credential_value) pairs.
     * @throws IOException if creating/writing the file fails.
     */
    void updateCredentials(Map<String, String> credentials) throws IOException;

    /**
     * Starts the runtime.
     * This method is called after the runtime is validated
     *
     * @param jemoServer
     */
    void start(AbstractJemo jemoServer);

    /**
     * Sets the region to the region matching the specified code
     *
     * @param regionCode the region code
     */
    void setRegion(String regionCode) throws IOException;

    String readInstanceTag(String key);

    /**
     * Returns the regions available on this runtime
     *
     * @return the available regions
     */
    List<RegionInfo> getRegions();

    /**
     * Resets the log console handler.
     * Called when the jemo logging parameters are changed in the setup.
     *
     * @param handler the log console handler to set
     */
    void resetLogConsoleHandler(Handler handler);

    /**
     * Returns a list of the existing vpcs
     *
     * @return list of strings including the vpc name and vpc id
     */
    List<String> getExistingNetworks();

    /**
     * Returns the list of all customer managed policy names
     *
     * @return the list of all customer managed policy names
     */
    List<String> getCustomerManagedPolicies();

    /**
     * Checks if the policy with the specified name has the permissions required by Jemo
     *
     * @param policyName the policy name
     * @return the validation result
     */
    ValidationResult validatePolicy(String policyName);

    /**
     * Creates the cluster by running terraform.
     * Also intantiate the kubernetes pods to run in the worker nodes.
     *
     * @param setupParams the setup parameters
     * @param builder     a string builder object used by the UI to monitor the progress of terraform commands
     * @return the resulting terraform job
     * @throws IOException
     * @throws ApiException
     */
    JemoRuntimeSetup.ClusterCreationResponse createCluster(JemoRuntimeSetup.SetupParams setupParams, StringBuilder builder) throws IOException, ApiException;

    /**
     * Generates the terraform templates needed for the cluster creation and the kubernetes yaml files
     *
     * @param setupParams the setup parameters
     * @return the path of the directory containing the files
     * @throws IOException
     */
    Path createClusterTerraformTemplates(JemoRuntimeSetup.SetupParams setupParams) throws IOException;

    List<InstallProperty> getInstallProperties();

    void setInstallProperties(Map<String, String> properties);

    /**
     * Creates all the CSP resources needed to setup jemo, e.g. user, policy, role.
     *
     * @param region  the CSP region the cluster will be deployed to
     * @param builder a string builder object used by the UI to monitor the progress of terraform commands
     * @return the resulting terraform job
     * @throws IOException
     */
    JemoRuntimeSetup.TerraformJobResponse install(String region, StringBuilder builder) throws IOException;

    /**
     * Generates the terraform templates needed to create the CSP resources of the installation phase.
     *
     * @param region the CSP region the cluster will be deployed to
     * @return the path of the directory containing the files
     * @throws IOException
     */
    Path createInstallTerraformTemplates(String region) throws IOException;

    /**
     * Generates a map with the credentials required by the CSP
     *
     * @param terraformResult the terraform result including created resources and outputs
     * @return the credentials map
     */
    Map<String, String> getCredentialsFromTerraformResult(TerraformResult terraformResult);

    /**
     * Provides the information that the UI needs to present to users that don't have the admin user (a.k.a. terraform user) credentials
     *
     * @return the AdminUserCreationInstructions object
     */
    AdminUserCreationInstructions getAdminUserCreationInstructions();

    default void prepareClusterCreation(JemoRuntimeSetup.SetupParams setupParams) {
        final Map<String, String> containerIdToParamSet = new HashMap<>();
        final Map<String, Integer> containersPerParamSet = Stream.of(setupParams.parameters().get("containersPerParamSet").split(","))
                .map(pair -> pair.split(":"))
                .collect(Collectors.toMap(pair -> pair[0], pair -> Integer.valueOf(pair[1])));

        int i = 0;
        for (Map.Entry<String, Integer> entry : containersPerParamSet.entrySet()) {
            for (int j = 0; j < entry.getValue(); j++) {
                containerIdToParamSet.put(String.valueOf(i++), entry.getKey());
            }
        }

        write("containers_per_paramset", "container_id_to_paramset", containerIdToParamSet);
    }

    ClusterParams getClusterParameters();

    default JemoRuntimeSetup.TerraformJobResponse deleteInstallResources(StringBuilder builder) throws IOException {
        Files.deleteIfExists(Paths.get("terraform.tfstate"));
        Files.deleteIfExists(Paths.get("terraform.tfstate.backup"));
        final String terraformDirPath = getTerraformInstallDir();
        Files.copy(Paths.get(terraformDirPath + "terraform.tfstate"), Paths.get("terraform.tfstate"), REPLACE_EXISTING);
        final TerraformJob terraformJob = new TerraformJob(terraformDirPath, terraformDirPath + "/" + JemoRuntimeSetup.TFVARS_FILE_NAME).destroy(builder);
        return JemoRuntimeSetup.TerraformJobResponse.fromTerraformJob(terraformJob);
    }

    default JemoRuntimeSetup.TerraformJobResponse deleteClusterResources(StringBuilder builder) throws IOException {
        deleteKubernetesResources(builder);
        Files.deleteIfExists(Paths.get("terraform.tfstate"));
        Files.deleteIfExists(Paths.get("terraform.tfstate.backup"));
        final String terraformDirPath = getTerraformClusterDir();
        Files.copy(Paths.get(terraformDirPath + "terraform.tfstate"), Paths.get("terraform.tfstate"), REPLACE_EXISTING);
        final TerraformJob terraformJob = new TerraformJob(terraformDirPath, terraformDirPath + "/" + JemoRuntimeSetup.TFVARS_FILE_NAME).destroy(builder);
        return JemoRuntimeSetup.TerraformJobResponse.fromTerraformJob(terraformJob);
    }

    void deleteKubernetesResources(StringBuilder builder) throws IOException;

    default String getTerraformInstallDir() {
        return getCspLabel() + "/install/";
    }

    default String getTerraformClusterDir() {
        return getCspLabel() + "/cluster/";
    }

    /**
     * A label used to created a directory to store terraform templates in.
     * Use a single world that uniquely identifies the CSP.
     *
     * @return the CSP label
     */
    String getCspLabel();

    /**
     * Removes from the cloud storage all files related to a plugin version identified by the jar file name
     *
     * @param pluginJarFileName the jar file name of the plugin under deletion
     */
    void removePluginFiles(String pluginJarFileName);

    /**
     * Checks if the CLI tools of this CSP is installed and in the Path.
     *
     * @return null if the cli is installed and in the path, otherwise a message instructing the user to install it
     */
    String isCliInstalled();

}
