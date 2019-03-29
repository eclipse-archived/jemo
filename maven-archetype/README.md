# Eclipse Jemo Maven Archetype

This is a maven archetype for creating [Eclipse Jemo](https://www.eclipse.org/jemo/) application projects.

Run:
```
mvn archetype:generate                      \
 -DarchetypeGroupId=org.eclipse.jemo        \
 -DarchetypeArtifactId=jemo-maven-archetype \
 -DarchetypeVersion=1.0                     \
 -DgroupId=<JEMO_PROJECT_GROUP_ID>          \
 -DartifactId=<JEMO_PROJECT_ARTIFACT_ID>    \
 -Dversion=<JEMO_PROJECT_VERSION>           \
 -DpluginId=<APP_ID>                        \
 -Dusername=<JEMO_USERNAME>                 \
 -Dpassword=<JEMO_PASSWORD>          
```