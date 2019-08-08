
import java.io.File;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;


/**
 * @author christopher stura
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class JemoDeploymentMojo extends AbstractMojo {
    private static final Pattern PATTERN = Pattern.compile("(.*)-([0-9]+\\.[0-9]+)-jar-with-dependencies");

    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
    private String outputDir;

    @Parameter(defaultValue = "${project.build.finalName}", property = "outputFile", required = true)
    private String outputJar;

    @Parameter(defaultValue = "${project.version}", property = "version", required = true)
    private double version;

    @Parameter(property = "jemo.endpoint", defaultValue = "https://localhost", required = true)
    private String endpoint;

    @Parameter(property = "jemo.username", required = true)
    private String username;

    @Parameter(property = "jemo.password", required = true)
    private String password;

    @Parameter(property = "jemo.id", required = true)
    private int id;

    public void execute() throws MojoExecutionException, MojoFailureException {
        //we will derive the plugin name from the name of the file, we will derive the plugin version from the version of defined on maven.
        try {
            getLog().info("deploying plugin [" + id + "] {" + outputJar + ".jar} to environment: " + endpoint);
            HttpClient httpClient = HttpClients.custom().setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setSSLContext(new SSLContextBuilder().loadTrustMaterial(new TrustStrategy() {
                        public boolean isTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                            return true;
                        }
                    }).build()).build();
            HttpPost postRequest = new HttpPost(endpoint + "/jemo");
            postRequest.setHeader("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes("UTF-8")));

            Matcher matcher = PATTERN.matcher(outputJar);
            matcher.find();

            postRequest.setEntity(MultipartEntityBuilder.create().addBinaryBody("PLUGIN", new File(outputDir, outputJar + ".jar"), ContentType.create("application/jar"), outputJar + ".jar")
                    .addTextBody("NAME", matcher.group(1))
                    .addTextBody("ID", String.valueOf(id))
                    .addTextBody("VERSION", String.valueOf(version)).build());
            HttpResponse response = httpClient.execute(postRequest);
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    getLog().info("deployment plugin [" + id + "] {" + outputJar + ".jar} to environment: " + endpoint + " successfull");
                    break;
                case 401:
                    getLog().info("deployment plugin [" + id + "] {" + outputJar + ".jar} to environment: " + endpoint + " access denied");
                    break;
                default:
                    getLog().info("deployment plugin [" + id + "] {" + outputJar + ".jar} to environment: " + endpoint + " failed");
                    break;
            }
            EntityUtils.consume(response.getEntity());
        } catch (Exception ex) {
            throw new MojoFailureException(ex.getMessage());
        }
    }
}
