package org.eclipse.jemo.sys.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.jemo.sys.JemoRuntimeSetup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.Boolean.FALSE;
import static java.util.stream.Collectors.toMap;

/**
 * @author Yannis Theocharis
 */
public class TerraformJob {

    private final String dirPath;
    private final String varsFilePath;
    private JemoRuntimeSetup.SetupError error;
    private TerraformResult result;
    private TimeUnit delayTimeUnit = TimeUnit.MINUTES;
    private long delayTime = 0;

    /**
     * @param dirPath the directory under which the terraform templates are stored.
     */
    public TerraformJob(String dirPath) {
        this(dirPath, null);
    }

    public TerraformJob(String dirPath, String varsFilePath) {
        this.dirPath = dirPath;
        this.varsFilePath = varsFilePath;
    }

    public TerraformJob run(StringBuilder builder) throws IOException {
        deleteIfExists("terraform.tfstate");
        deleteIfExists("terraform.tfstate.backup");
        deleteIfExists(".terraform.tfstate.lock.info");

        final String varsPart = varsFilePath == null ? "" : "-var-file=" + varsFilePath + " ";

        final String[] initResult = Util.runProcess(builder, "terraform init -no-color " + varsPart + dirPath);
        if (!initResult[1].isEmpty()) {
            error = new JemoRuntimeSetup.SetupError(JemoRuntimeSetup.SetupError.Code.TERRAFORM_INIT_ERROR, initResult[1]);
            return this;
        }

        final String[] planResult = Util.runProcess(builder, "terraform plan -no-color " + varsPart + dirPath);
        if (!planResult[1].isEmpty()) {
            error = new JemoRuntimeSetup.SetupError(JemoRuntimeSetup.SetupError.Code.TERRAFORM_PLAN_ERROR, planResult[1]);
            return this;
        }
        final String[] applyResult = Util.runProcess(builder, "terraform apply -no-color -auto-approve " + varsPart + dirPath);
        if (!applyResult[1].isEmpty()) {
            error = new JemoRuntimeSetup.SetupError(JemoRuntimeSetup.SetupError.Code.TERRAFORM_APPLY_ERROR, applyResult[1]);
            return this;
        } else if (delayTime > 0) {
            Util.B(null, x -> delayTimeUnit.sleep(delayTime));
        }

        result = TerraformResult.fromOutput(applyResult[0]);
        return this;
    }

    public TerraformJob destroy(StringBuilder builder) throws IOException {
        final String varsPart = varsFilePath == null ? "" : "-var-file=" + varsFilePath + " ";

        final String[] initResult = Util.runProcess(builder, "terraform init -no-color " + varsPart + dirPath);
        if (!initResult[1].isEmpty()) {
            error = new JemoRuntimeSetup.SetupError(JemoRuntimeSetup.SetupError.Code.TERRAFORM_INIT_ERROR, initResult[1]);
            return this;
        }

        final String[] destroyResult = Util.runProcess(builder, "terraform destroy -no-color -auto-approve " + varsPart + dirPath);
        if (!destroyResult[1].isEmpty()) {
            error = new JemoRuntimeSetup.SetupError(JemoRuntimeSetup.SetupError.Code.TERRAFORM_INIT_ERROR, destroyResult[1]);
            return this;
        }

        result = TerraformResult.fromOutput(destroyResult[0]);
        return this;
    }

    public boolean isSuccessful() {
        return error == null;
    }

    public JemoRuntimeSetup.SetupError getError() {
        return error;
    }

    public TerraformResult getResult() {
        return result;
    }

    public TerraformJob withDelay(TimeUnit delayTimeUnit, long delayTime) {
        this.delayTimeUnit = delayTimeUnit;
        this.delayTime = delayTime;
        return this;
    }

    private static String parseId(String line) {
        final String startTag = "(ID: ";
        final int start = line.indexOf(startTag);
        final int end = line.indexOf(")", start);
        return line.substring(start + startTag.length(), end);
    }

    public static boolean isTerraformInstalled() {
        try {
            Util.runProcess(null, "terraform -version");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteIfExists(String fileName) throws IOException {
        final Path path = Paths.get(fileName);
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    public static class TerraformResult {

        // The original terraform output, available for further parsing to the client code.
        @JsonIgnore
        private String terraformOutput;

        @JsonProperty
        private Map<String, String> createdResources;

        @JsonProperty
        private Map<String, String> outputs;

        public static TerraformResult fromOutput(String terraformOutput) {
            TerraformResult terraformResult = new TerraformResult();
            terraformResult.terraformOutput = terraformOutput;

            terraformResult.createdResources = Stream.of(terraformOutput.split("\n"))
                    .filter(line -> line.contains(": Creation complete"))
                    .collect(toMap(line -> line.split(": Creation complete")[0], TerraformJob::parseId));

            final Boolean isOutputsStartFound[] = {FALSE};
            terraformResult.outputs = Stream.of(terraformOutput.split("\n"))
                    .filter(line -> isOutputsStartFound[0] = isOutputsStartFound[0] || line.contains("Outputs:"))
                    .filter(line -> line.contains(" = "))
                    .map(line -> line.split(" = "))
                    .filter(lineParts -> lineParts.length > 1)
                    .collect(toMap(lineParts -> lineParts[0], lineParts -> lineParts[1]));

            return terraformResult;
        }

        @JsonIgnore
        public Map<String, String> getCreatedResources() {
            return createdResources;
        }

        @JsonIgnore
        public String getCreatedResource(String key) {
            return createdResources.get(key);
        }

        @JsonIgnore
        public Map<String, String> getOutputs() {
            return outputs;
        }

        @JsonIgnore
        public String getOutput(String key) {
            return outputs.get(key);
        }

        @JsonIgnore
        public String getTerraformOutput() {
            return terraformOutput;
        }
    }

}
