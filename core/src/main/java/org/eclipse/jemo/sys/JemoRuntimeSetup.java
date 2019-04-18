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
package org.eclipse.jemo.sys;

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.internal.model.CloudRuntime;
import org.eclipse.jemo.internal.model.ValidationResult;
import org.eclipse.jemo.sys.internal.TerraformJob;
import org.eclipse.jemo.sys.internal.TerraformJob.TerraformResult;
import org.eclipse.jemo.sys.internal.Util;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.eclipse.jemo.api.JemoParameter.CLOUD;
import static org.eclipse.jemo.sys.JemoPluginManager.PluginManagerModule.respondWithJson;
import static org.eclipse.jemo.sys.JemoRuntimeSetup.TerraformJobResponse.ClusterCreationStatus.*;
import static org.eclipse.jemo.sys.JemoRuntimeSetup.SetupError.Code.*;
import static org.eclipse.jemo.sys.internal.Util.readAllBytes;

public class JemoRuntimeSetup {

    public static final String JEMO_SETUP = "/jemo/setup";
    private static final String CSP_ENDPOINT = JEMO_SETUP + "/csp";
    private static final String INSTALL_PROPS_ENDPOINT = JEMO_SETUP + "/install/props";
    private static final String INIT_ENDPOINT = JEMO_SETUP + "/init";
    private static final String CREDENTIALS_ENDPOINT = JEMO_SETUP + "/credentials";
    private static final String PERMISSIONS_ENDPOINT = JEMO_SETUP + "/permissions";
    private static final String INSTALL_ENDPOINT = JEMO_SETUP + "/install";
    private static final String INSTALL_RESULT_ENDPOINT = INSTALL_ENDPOINT + "/result";
    private static final String JEMO_PARAMS_ENDPOINT = JEMO_SETUP + "/jemoparams";
    private static final String JEMO_PARAMSETS_ENDPOINT = JEMO_SETUP + "/jemoparams/paramsets";
    private static final String START_LOCAL_INSTANCE_ENDPOINT = JEMO_SETUP + "/start";
    private static final String CLUSTER_ENDPOINT = JEMO_SETUP + "/cluster";
    private static final String CREATE_CLUSTER_RESULT_ENDPOINT = CLUSTER_ENDPOINT + "/result";
    private static final String CREATE_CLUSTER_PARAMS = CLUSTER_ENDPOINT + "/params";
    private static final String NETWORKS_ENDPOINT = JEMO_SETUP + "/networks";
    private static final String POLICIES_ENDPOINT = JEMO_SETUP + "/policy";
    private static final String POLICIES_VALIDATE_ENDPOINT = JEMO_SETUP + "/policy/validate";
    private static final String DOWNLOAD_CLUSTER_TERRAFORM_TEMPLATES_ENDPOINT = CLUSTER_ENDPOINT + "/download";
    private static final String DOWNLOAD_INSTALL_TERRAFORM_TEMPLATES_ENDPOINT = JEMO_SETUP + "/install/download";

    public static final String TFVARS_FILE_NAME = "terraform.tfvars";

    private static ExecutorService EXECUTOR_SERVICE;
    private static Future<? extends TerraformJobResponse> TERAFORM_JOB_FUTURE;
    private static StringBuilder TERRAFORM_JOB_OUTPUT;

    public static void processRequest(AbstractJemo jemoServer, HttpServletRequest request, HttpServletResponse response) throws IOException, NoSuchAlgorithmException, ExecutionException, InterruptedException {
        switch (request.getMethod()) {
            case "GET":
                if (request.getRequestURI().startsWith(INIT_ENDPOINT)) {
                    respondWithJson(200, response, CloudProvider.values());
                } else if (request.getRequestURI().startsWith(JEMO_PARAMSETS_ENDPOINT)) {
                    getParameterSets(request, response);
                } else if (request.getRequestURI().startsWith(PERMISSIONS_ENDPOINT)) {
                    validatePermissions(jemoServer, request, response);
                } else if (request.getRequestURI().startsWith(CREATE_CLUSTER_RESULT_ENDPOINT)) {
                    getCreateClusterResult(response);
                } else if (request.getRequestURI().startsWith(INSTALL_RESULT_ENDPOINT)) {
                    getInstallationResult(jemoServer, request, response);
                } else if (request.getRequestURI().startsWith(NETWORKS_ENDPOINT)) {
                    getExistingNetworks(request, response);
                } else if (request.getRequestURI().startsWith(POLICIES_ENDPOINT)) {
                    getExistingPolicies(request, response);
                } else if (request.getRequestURI().startsWith(CREATE_CLUSTER_PARAMS)) {
                    getClusterParameters(request, response);
                } else {
                    loadFile(request.getRequestURI().replaceAll(JEMO_SETUP, ""), response);
                }
                break;
            case "POST":
                switch (request.getRequestURI()) {
                    case INSTALL_PROPS_ENDPOINT:
                        setInstallProperties(request, response);
                        break;
                    case CREDENTIALS_ENDPOINT:
                        validateCredentials(request, response);
                        break;
                    case POLICIES_VALIDATE_ENDPOINT:
                        validatePolicy(request, response);
                        break;
                    case INSTALL_ENDPOINT:
                        install(request, response);
                        break;
                    case JEMO_PARAMS_ENDPOINT:
                        storeParameters(jemoServer, request, response);
                        break;
                    case START_LOCAL_INSTANCE_ENDPOINT:
                        startLocalInstance(jemoServer, request, response);
                        break;
                    case CLUSTER_ENDPOINT:
                        createCluster(request, response);
                        break;
                    case DOWNLOAD_INSTALL_TERRAFORM_TEMPLATES_ENDPOINT:
                        downloadInstallTerraformFiles(request, response);
                        break;
                    case DOWNLOAD_CLUSTER_TERRAFORM_TEMPLATES_ENDPOINT:
                        downloadClusterCreationTerraformFiles(request, response);
                        break;
                    default:
                        response.sendError(404, "No functionality is mapped to this endpoint yet" + request.getRequestURI());
                }
                break;
            case "OPTIONS":
                response.addHeader("Access-Control-Allow-Origin", "*");
                response.setHeader("Access-Control-Allow-Methods", "GET,HEAD,OPTIONS,POST,PUT,DELETE");
                response.setHeader("Access-Control-Allow-Headers", "Access-Control-Allow-Headers, Origin,Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
                break;
            case "DELETE":
                if (request.getRequestURI().startsWith(INSTALL_ENDPOINT)) {
                    deleteInstallResources(request, response);
                } else if (request.getRequestURI().startsWith(CLUSTER_ENDPOINT)) {
                    deleteClusterResources(request, response);
                } else {
                    response.sendError(404, "No functionality is mapped to this endpoint yet" + request.getRequestURI());
                }
                break;
            case "PUT":
                if (request.getRequestURI().startsWith(CSP_ENDPOINT)) {
                    setSelectedCsp(request, response);
                } else {
                    response.sendError(404, "No functionality is mapped to this endpoint yet" + request.getRequestURI());
                }
                break;
            default:
                response.sendError(400);
        }
    }

    private static void setInstallProperties(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(setupParams.csp);
        cloudRuntime.setInstallProperties(setupParams.parameters);
        respondWithJson(201, response, null);
    }

    /**
     * Forwards to setup/index.html
     *
     * @param requestUri
     * @param response   the http servlet response object
     * @throws IOException
     */
    private static void loadFile(String requestUri, HttpServletResponse response) throws IOException {
        final String fileName = requestUri.isEmpty() || requestUri.equals("/") ? "/index.html" : requestUri;
        final InputStream in = Jemo.class.getResourceAsStream("/setup" + fileName);
        Jemo.stream(response.getOutputStream(), new ByteArrayInputStream(readAllBytes(in)));
    }

    private static void validateCredentials(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        final CloudRuntime cloudRt = CloudProvider.getRuntimeByName(setupParams.csp);

        try {
            cloudRt.setRegion(setupParams.parameters.get("region"));
        } catch (Throwable t) {
            respondWithJson(400, response, "Failed to set the region to: " + setupParams.parameters.get("region") + ".\nServer message: " + t.getMessage());
        }

        final ValidationResult validationResult = cloudRt.validateCredentials(setupParams.parameters);
        if (validationResult.isSuccess()) {
            cloudRt.updateCredentials(setupParams.parameters);
            respondWithJson(201, response, null);
        } else {
            respondWithJson(403, response, validationResult.notAllowedActions());
        }
    }

    private static void validatePermissions(AbstractJemo jemoServer, HttpServletRequest request, HttpServletResponse response) throws IOException, NoSuchAlgorithmException {
        final String csp = request.getRequestURI().replaceAll(PERMISSIONS_ENDPOINT + "/", "");
        final CloudRuntime cloudRt = CloudProvider.getRuntimeByName(csp);

        final ValidationResult validationResult = cloudRt.validatePermissions();
        if (validationResult.isSuccess()) {
            cloudRt.start(jemoServer);
            respondWithJson(200, response, null);
        } else {
            respondWithJson(403, response, validationResult.notAllowedActions());
        }
    }

    /**
     * Creates the user with the needed permission.
     * First creates the terraform file with json, then it runs terraform with this file as an input.
     */
    private static void install(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // First Check if terraform is installed
        if (!TerraformJob.isTerraformInstalled()) {
            respondWithJson(400, response, new SetupError(TERRAFORM_NOT_INSTALLED, null));
            return;
        }

        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(setupParams.csp);
        final String region = setupParams.parameters.get("region");

        TERRAFORM_JOB_OUTPUT = new StringBuilder();
        EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
        TERAFORM_JOB_FUTURE = EXECUTOR_SERVICE.submit(() -> Util.F(null, x -> cloudRuntime.install(region, TERRAFORM_JOB_OUTPUT)));
        respondWithJson(201, response, "");
    }

    private static void deleteInstallResources(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String csp = request.getRequestURI().replaceAll(INSTALL_ENDPOINT + "/", "");
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(csp);

        TERRAFORM_JOB_OUTPUT = new StringBuilder();
        EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
        TERAFORM_JOB_FUTURE = EXECUTOR_SERVICE.submit(() -> Util.F(null, x -> cloudRuntime.deleteInstallResources(TERRAFORM_JOB_OUTPUT)));
        respondWithJson(201, response, "");
    }

    private static void deleteClusterResources(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String csp = request.getRequestURI().replaceAll(CLUSTER_ENDPOINT + "/", "");
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(csp);

        TERRAFORM_JOB_OUTPUT = new StringBuilder();
        EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
        TERAFORM_JOB_FUTURE = EXECUTOR_SERVICE.submit(() -> Util.F(null, x -> cloudRuntime.deleteClusterResources(TERRAFORM_JOB_OUTPUT)));
        respondWithJson(201, response, "");
    }

    private static void createCluster(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!TerraformJob.isTerraformInstalled()) {
            respondWithJson(400, response, new SetupError(TERRAFORM_NOT_INSTALLED, null));
            return;
        }

        if (!Util.isKubectlInstalled()) {
            respondWithJson(400, response, new SetupError(KUBECTL_NOT_INSTALLED, null));
            return;
        }

        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(setupParams.csp);
        TERRAFORM_JOB_OUTPUT = new StringBuilder();
        EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
        TERAFORM_JOB_FUTURE = EXECUTOR_SERVICE.submit(() -> Util.F(null, x -> cloudRuntime.createCluster(setupParams, TERRAFORM_JOB_OUTPUT)));
        respondWithJson(201, response, "");
    }

    private static void downloadClusterCreationTerraformFiles(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(setupParams.csp);
        final Path terraformDirPath = cloudRuntime.createClusterTerraformTemplates(setupParams);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=cluster.zip");
        ZipDirectory.zip(terraformDirPath, response.getOutputStream());
    }

    private static void downloadInstallTerraformFiles(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(setupParams.csp);
        final Path terraformDirPath = cloudRuntime.createInstallTerraformTemplates(null);
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=install.zip");
        ZipDirectory.zip(terraformDirPath, response.getOutputStream());
    }

    private static void getCreateClusterResult(HttpServletResponse response) throws IOException {
        TerraformJobResponse terraformJobResponse;
        try {
            terraformJobResponse = generateTerraformJobStatusResponse();
        } catch (Exception e) {
            respondWithJson(500, response, e.getMessage());
            return;
        }
        respondWithJson(200, response, terraformJobResponse);
    }

    private static void getInstallationResult(AbstractJemo jemoServer, HttpServletRequest request, HttpServletResponse response) throws ExecutionException, InterruptedException, IOException {
        TerraformJobResponse installResponse;
        try {
            installResponse = generateTerraformJobStatusResponse();
        } catch (Exception e) {
            respondWithJson(500, response, e.getMessage());
            return;
        }

        final String[] paramsArray = request.getRequestURI().replaceAll(INSTALL_RESULT_ENDPOINT + "/", "").split("/");
        if (installResponse.status == FINISHED && installResponse.error == null && paramsArray.length > 1) {
            final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(paramsArray[0]);
            cloudRuntime.updateCredentials(cloudRuntime.getCredentialsFromTerraformResult(installResponse.terraformResult));
            cloudRuntime.setInstallProperties(installResponse.terraformResult.getOutputs());
            cloudRuntime.setRegion(paramsArray[1]);
            cloudRuntime.start(jemoServer);
        }
        respondWithJson(200, response, installResponse);
    }

    @NotNull
    private static TerraformJobResponse generateTerraformJobStatusResponse() throws InterruptedException, ExecutionException {
        if (TERAFORM_JOB_FUTURE == null) {
            return new TerraformJobResponse(NOT_RUN);
        } else if (!TERAFORM_JOB_FUTURE.isDone()) {
            return new TerraformJobResponse(PENDING, TERRAFORM_JOB_OUTPUT.toString());
        } else {
            final TerraformJobResponse terraformJobResponse = TERAFORM_JOB_FUTURE.get();
            terraformJobResponse.status = FINISHED;
            shutdownExecutor();
            terraformJobResponse.output = TERRAFORM_JOB_OUTPUT.toString();
            return terraformJobResponse;
        }
    }

    private static void shutdownExecutor() {
        if (!EXECUTOR_SERVICE.isShutdown()) {
            EXECUTOR_SERVICE.shutdown();
            try {
                if (!EXECUTOR_SERVICE.awaitTermination(1_000, TimeUnit.MILLISECONDS)) {
                    EXECUTOR_SERVICE.shutdownNow();
                }
            } catch (InterruptedException e) {
                EXECUTOR_SERVICE.shutdownNow();
            }
        }
    }

    private static void getParameterSets(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String csp = request.getRequestURI().replaceAll(JEMO_PARAMSETS_ENDPOINT + "/", "");
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(csp);
        List<Map> parameterSets;
        try {
            parameterSets = cloudRuntime.readAll(Map.class, "param_set").collect(Collectors.toList());
        } catch (Throwable t) {
            parameterSets = new ArrayList<>();
        }
        respondWithJson(200, response, parameterSets);
    }

    private static void storeParameters(AbstractJemo jemoServer, HttpServletRequest request, HttpServletResponse response) throws IOException, NoSuchAlgorithmException {
        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(setupParams.csp);
        cloudRuntime.write("param_set", setupParams.parameters.get("name"), setupParams.parameters);
        jemoServer.storeParamSet(setupParams.parameters.get("name"));
        respondWithJson(201, response, null);
    }

    private static void startLocalInstance(AbstractJemo jemoServer, HttpServletRequest request, HttpServletResponse response) throws IOException, NoSuchAlgorithmException {
        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(setupParams.csp);
        jemoServer.onSuccessfulValidation(cloudRuntime);
        respondWithJson(201, response, null);
    }

    private static void getExistingNetworks(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String csp = request.getRequestURI().replaceAll(NETWORKS_ENDPOINT + "/", "");
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(csp);
        final List<String> existingNetworks = cloudRuntime.getExistingNetworks();
        respondWithJson(200, response, existingNetworks);
    }

    private static void getExistingPolicies(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String csp = request.getRequestURI().replaceAll(POLICIES_ENDPOINT + "/", "");
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(csp);
        final List<String> policies = cloudRuntime.getCustomerManagedPolicies();
        respondWithJson(200, response, policies);
    }

    private static void validatePolicy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(setupParams.csp);
        final ValidationResult result = cloudRuntime.validatePolicy(setupParams.parameters.get("policy"));
        respondWithJson(201, response, result);
    }

    private static void getClusterParameters(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String csp = request.getRequestURI().replaceAll(CREATE_CLUSTER_PARAMS + "/", "");
        final CloudRuntime cloudRuntime = CloudProvider.getRuntimeByName(csp);
        final ClusterParams clusterParams = cloudRuntime.getClusterParameters();
        respondWithJson(200, response, clusterParams);
    }

    private static void setSelectedCsp(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final SetupParams setupParams = Jemo.fromJSONString(SetupParams.class, Util.toString(request.getInputStream()));
        System.setProperty(CLOUD.label(), setupParams.csp);
        respondWithJson(204, response, null);
    }

    public static class SetupParams {
        @JsonProperty
        private String csp;

        @JsonProperty
        private Map<String, String> parameters;

        public Map<String, String> parameters() {
            return parameters;
        }
    }

    public static class SetupError {
        @JsonProperty
        private Code code;

        @JsonProperty
        private String message;

        public SetupError(Code code, String message) {
            this.code = code;
            this.message = message;
        }

        public enum Code {
            TERRAFORM_NOT_INSTALLED, TERRAFORM_INIT_ERROR, TERRAFORM_PLAN_ERROR, TERRAFORM_APPLY_ERROR, TERRAFORM_DESTROY_ERROR, KUBECTL_NOT_INSTALLED, OTHER
        }
    }

    public static class TerraformJobResponse {
        @JsonProperty
        protected TerraformResult terraformResult;

        @JsonProperty
        protected SetupError error;

        @JsonProperty
        protected ClusterCreationStatus status;

        @JsonProperty
        protected String output;

        public TerraformJobResponse() {
        }

        public TerraformJobResponse(ClusterCreationStatus status) {
            this.status = status;
        }

        public TerraformJobResponse(ClusterCreationStatus status, String output) {
            this.status = status;
            this.output = output;
        }

        public static TerraformJobResponse fromTerraformJob(TerraformJob terraformJob) {
            final TerraformJobResponse terraformJobResponse = new TerraformJobResponse();
            terraformJobResponse.terraformResult = terraformJob.getResult();
            terraformJobResponse.error = terraformJob.getError();
            return terraformJobResponse;
        }

        enum ClusterCreationStatus {
            NOT_RUN, PENDING, FINISHED
        }
    }

    public static class ClusterCreationResponse extends TerraformJobResponse {
        @JsonProperty
        private String loadBalancerUrl;

        public static ClusterCreationResponse fromTerraformJob(TerraformJob terraformJob) {
            final ClusterCreationResponse clusterCreationResponse = new ClusterCreationResponse();
            clusterCreationResponse.terraformResult = terraformJob.getResult();
            clusterCreationResponse.error = terraformJob.getError();
            return clusterCreationResponse;
        }

        public ClusterCreationResponse setLoadBalancerUrl(String loadBalancerUrl) {
            this.loadBalancerUrl = loadBalancerUrl;
            return this;
        }
    }
}
