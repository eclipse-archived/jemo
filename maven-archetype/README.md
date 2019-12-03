# Eclipse Jemo Maven Archetype

This is a maven archetype for creating [Eclipse Jemo](https://www.eclipse.org/jemo/) application projects.

Run:
```
mvn archetype:generate                              \
 -DarchetypeGroupId=org.eclipse.jemo                \
 -DarchetypeArtifactId=eclipse-jemo-maven-archetype \
 -DarchetypeVersion=1.2                             \
 -DgroupId=<JEMO_PROJECT_GROUP_ID>                  \
 -DartifactId=<JEMO_PROJECT_ARTIFACT_ID>            \
 -Dversion=<JEMO_PROJECT_VERSION>                   \
 -DpluginId=<APP_ID>                                \
 -DjemoEndpoint=<JEMO_SERVER_ENDPOINT>              \
 -Dusername=<JEMO_USERNAME>                         \
 -Dpassword=<JEMO_PASSWORD>          
```

e.g. 
```
mvn archetype:generate                                  \
 -DarchetypeGroupId=org.eclipse.jemo                    \
 -DarchetypeArtifactId=eclipse-jemo-maven-archetype     \
 -DarchetypeVersion=1.2                                 \
 -DgroupId=com.cloudreach                               \
 -DartifactId=jemo-app-demo                             \
 -Dversion=1.0                                          \
 -DpluginId=2                                           \
 -DjemoEndpoint=https://localhost                       \
 -Dusername=system.administrator@jemo.eclipse.org       \
 -Dpassword=88e56dea-faa2-46f1-9b5a-9b2241acb309                               
```
