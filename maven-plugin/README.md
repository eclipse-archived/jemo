# Eclipse Jemo Maven Plugin

This is the maven plugin that allows Jemo applications to be deployed.
Add the follwing plugin on the jemo app pom file under the 'build/plugins' element and provide values for parameters 'jemo.id', 'jemo.username', 'jemo.password', 'jemo.endpoint'.

Then run 'mvn deploy'. 

All the maven targets, including 'compile' and 'test', run up to 'deploy'. 
If all the previous targets are successful the 'deploy' target is triggered and this plugin sends an HTTP POST request to Jemo with a body including the "fat jar" of the application, i.e. the jar with all the depenencies included.
Jemo then deploys the app on the local instance and also notifies all other Jemo instances in the cluster that this app is deployed.

```
<plugin>
  <groupId>org.eclipse.jemo</groupId>
  <artifactId>eclipse-jemo-maven-plugin</artifactId>
  <version>1.1</version>
  <executions>
    <execution>
      <phase>deploy</phase>
      <goals>
        <goal>deploy</goal>
      </goals>
      <configuration>
        <outputJar>${project.build.finalName}-jar-with-dependencies</outputJar>
        <!-- Set the Jemo plugin id to upload here -->
        <id>${jemo.id}</id>
        <username>${jemo.username}</username>
        <password>${jemo.password}</password>
        <endpoint>${jemo.endpoint}</endpoint>
      </configuration>
    </execution>
  </executions>
</plugin>
```
