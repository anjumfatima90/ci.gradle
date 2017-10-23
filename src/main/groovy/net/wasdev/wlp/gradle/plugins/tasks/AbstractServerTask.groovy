/**
 * (C) Copyright IBM Corporation 2017.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.wasdev.wlp.gradle.plugins.tasks

import net.wasdev.wlp.gradle.plugins.extensions.DeployExtension
import net.wasdev.wlp.gradle.plugins.extensions.LibertyExtension
import net.wasdev.wlp.gradle.plugins.utils.ApplicationXmlDocument
import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import groovy.xml.MarkupBuilder
import org.gradle.api.tasks.bundling.War
import org.gradle.plugins.ear.Ear

abstract class AbstractServerTask extends AbstractTask {

    private final String HEADER = "# Generated by liberty-gradle-plugin"

    def server

    protected void executeServerCommand(Project project, String command, Map<String, String> params) {
        project.ant.taskdef(name: 'server',
                            classname: 'net.wasdev.wlp.ant.ServerTask',
                            classpath: project.buildscript.configurations.classpath.asPath)
        params.put('operation', command)
        project.ant.server(params)
    }

    protected Map<String, String> buildLibertyMap(Project project) {
        Map<String, String> result = new HashMap();
        result.put('serverName', server.name)

        def installDir = getInstallDir(project)
        result.put('installDir', installDir)

        def userDir = getUserDir(project, installDir)
        result.put('userDir', userDir)

        if (getServerOutputDir(project) != null) {
            result.put('outputDir', getServerOutputDir(project))
        }
        if (server.timeout != null && !server.timeout.isEmpty()) {
            result.put('timeout', server.timeout)
        }

        return result;
    }

    protected List<String> buildCommand (String operation) {
        List<String> command = new ArrayList<String>()
        boolean isWindows = System.properties['os.name'].toLowerCase().indexOf("windows") >= 0
        String installDir = getInstallDir(project).toString()

        if (isWindows) {
            command.add(installDir + "\\bin\\server.bat")
        } else {
            command.add(installDir + "/bin/server")
        }
        command.add(operation)
        command.add(server.name)

        return command
    }

    protected File getServerDir(Project project){
        return new File(getUserDir(project).toString() + "/servers/" + server.name)
    }

    protected String getServerOutputDir(Project project) {
        if (server.outputDir != null) {
            return server.outputDir
        } else {
            return project.liberty.outputDir
        }
    }

    protected void setDefaults(Project project) {
        if (server.configFile.toString().equals('default')) {
            server.configFile = new File(project.projectDir.toString() + '/src/main/liberty/config/server.xml')
        } else if (!server.configFile.exists()) {
            logger.warn("The server configFile was configured but was not found at: ${server.configFile}")
        }
        if (server.bootstrapPropertiesFile.toString().equals('default')) {
            server.bootstrapPropertiesFile = new File(project.projectDir.toString() + '/src/main/liberty/config/bootstrap.properties')
        } else if (!server.bootstrapPropertiesFile.exists()) {
            logger.warn("The bootstrapPropertiesFile was configured but was not found at: ${server.bootstrapPropertiesFile}")
        }
        if (server.jvmOptionsFile.toString().equals('default')) {
            server.jvmOptionsFile = new File(project.projectDir.toString() + '/src/main/liberty/config/jvm.options')
        } else if (!server.jvmOptionsFile.exists()) {
            logger.warn("The server jvmOptionsFile was configured but was not found at: ${server.jvmOptionsFile}")
        }
        if(server.serverEnv.toString().equals('default')) {
            server.serverEnv = new File(project.projectDir.toString() + '/src/main/liberty/config/server.env')
        } else if (!server.serverEnv.exists()) {
            logger.warn("The server serverEnv was configured but was not found at: ${server.serverEnv}")
        }
    }

    /**
     * @throws IOException
     * @throws FileNotFoundException
     */
    protected void copyConfigFiles() throws IOException {

        String serverDirectory = getServerDir(project).toString()
        String serverXMLPath = null
        String jvmOptionsPath = null
        String bootStrapPropertiesPath = null
        String serverEnvPath = null

        setDefaults(project)

        if (server.configDirectory != null) {
            if(server.configDirectory.exists()){
                // copy configuration files from configuration directory to server directory if end-user set it
                def copyAnt = new AntBuilder()
                copyAnt.copy(todir: serverDirectory) {
                    fileset(dir: server.configDirectory.getCanonicalPath())
                }

                File configDirServerXML = new File(server.configDirectory, "server.xml")
                if (configDirServerXML.exists()) {
                    serverXMLPath = configDirServerXML.getCanonicalPath()
                }

                File configDirJvmOptionsFile = new File(server.configDirectory, "jvm.options")
                if (configDirJvmOptionsFile.exists()) {
                    jvmOptionsPath = configDirJvmOptionsFile.getCanonicalPath()
                }

                File configDirBootstrapFile = new File(server.configDirectory, "bootstrap.properties")
                if (configDirBootstrapFile.exists()) {
                    bootStrapPropertiesPath = configDirBootstrapFile.getCanonicalPath()
                }

                File configDirServerEnv = new File(server.configDirectory, "server.env")
                if (configDirServerEnv.exists()) {
                    serverEnvPath = configDirServerEnv.getCanonicalPath()
                }
            }
            else{
                println('WARNING: The configDirectory attribute was configured but the directory is not found: ' + project.liberty.configDirectory.getCanonicalPath())
            }
        }

        // handle server.xml if not overwritten by server.xml from configDirectory
        if (serverXMLPath == null || serverXMLPath.isEmpty()) {
            // copy configuration file to server directory if end-user set it.
            if (server.configFile != null && server.configFile.exists()) {
                Files.copy(server.configFile.toPath(), new File(serverDirectory, "server.xml").toPath(), StandardCopyOption.REPLACE_EXISTING)
                serverXMLPath = server.configFile.getCanonicalPath()
            }
        }

        // handle jvm.options if not overwritten by jvm.options from configDirectory
        if (jvmOptionsPath == null || jvmOptionsPath.isEmpty()) {
            File optionsFile = new File(serverDirectory, "jvm.options")
            if(server.jvmOptions != null && !server.jvmOptions.isEmpty()){
                writeJvmOptions(optionsFile, server.jvmOptions)
                jvmOptionsPath = "inlined configuration"
            } else if (server.jvmOptionsFile != null && server.jvmOptionsFile.exists()) {
                Files.copy(server.jvmOptionsFile.toPath(), optionsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                jvmOptionsPath = server.jvmOptionsFile.getCanonicalPath()
            }
        }

        // handle bootstrap.properties if not overwritten by bootstrap.properties from configDirectory
        if (bootStrapPropertiesPath == null || bootStrapPropertiesPath.isEmpty()) {
            File bootstrapFile = new File(serverDirectory, "bootstrap.properties")
            if(server.bootstrapProperties != null && !server.bootstrapProperties.isEmpty()){
                writeBootstrapProperties(bootstrapFile, server.bootstrapProperties)
                bootStrapPropertiesPath = "inlined configuration"
            } else if (server.bootstrapPropertiesFile != null && server.bootstrapPropertiesFile.exists()) {
                Files.copy(server.bootstrapPropertiesFile.toPath(), bootstrapFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                bootStrapPropertiesPath = server.bootstrapPropertiesFile.getCanonicalPath()
            }
        }

        // handle server.env if not overwritten by server.env from configDirectory
        if (serverEnvPath == null || serverEnvPath.isEmpty()) {
            if (server.serverEnv != null && server.serverEnv.exists()) {
                Files.copy(server.serverEnv.toPath(), new File(serverDirectory, "server.env").toPath(), StandardCopyOption.REPLACE_EXISTING)
                serverEnvPath = server.serverEnv.getCanonicalPath()
            }
        }

        // log info on the configuration files that get used
        if (serverXMLPath != null && !serverXMLPath.isEmpty()) {
            logger.info("Update server configuration file server.xml from " + serverXMLPath)
        }
        if (jvmOptionsPath != null && !jvmOptionsPath.isEmpty()) {
            logger.info("Update server configuration file jvm.options from " + jvmOptionsPath)
        }
        if (bootStrapPropertiesPath != null && !bootStrapPropertiesPath.isEmpty()) {
            logger.info("Update server configuration file bootstrap.properties from " + bootStrapPropertiesPath)
        }
        if (serverEnvPath != null && !serverEnvPath.isEmpty()) {
            logger.info("Update server configuration file server.env from " + serverEnvPath)
        }
    }

    protected void outputProjectDirectoriesToXml(MarkupBuilder xmlDoc) {
        xmlDoc.installDirectory (getInstallDir(project).toString())
        xmlDoc.serverDirectory (getServerDir(project).toString())
        xmlDoc.userDirectory (getUserDir(project).toString())

        String serverOutputDir = getServerOutputDir(project)
        if (serverOutputDir != null && !serverOutputDir.isEmpty()) {
            xmlDoc.serverOutputDirectory (serverOutputDir)
        } else {
            xmlDoc.serverOutputDirectory (getServerDir(project).toString())
        }
    }

    protected void outputServerPropertiesToXml(MarkupBuilder xmlDoc) {
        xmlDoc.serverName (server.name)

        if (server.configDirectory != null && server.configDirectory.exists()) {
            xmlDoc.configDirectory (server.configDirectory.toString())
        }

        if (server.configFile != null && server.configFile.exists()) {
            xmlDoc.configFile (server.configFile.toString())
        }

        if (server.bootstrapProperties != null && !server.bootstrapProperties.isEmpty()) {
            xmlDoc.bootstrapProperties {
                server.bootstrapProperties.collect { k, v ->
                    "$k" (v.toString())
                }
            }
        } else if (server.bootstrapPropertiesFile != null && server.bootstrapPropertiesFile.exists()) {
            xmlDoc.bootstrapPropertiesFile (server.bootstrapPropertiesFile.toString())
        }

        if (server.jvmOptions != null && !server.jvmOptions.isEmpty()) {
            xmlDoc.jvmOptions {
                server.jvmOptions.collect { v ->
                    "params" (v.toString())
                }
            }
        } else if (server.jvmOptionsFile != null && server.jvmOptionsFile.exists()) {
            xmlDoc.jvmOptionsFile (server.jvmOptionsFile.toString())
        }

        if (server.serverEnv != null && server.serverEnv.exists()) {
            xmlDoc.serverEnv (server.serverEnv.toString())
        }

        xmlDoc.appsDirectory (server.appsDirectory)
        xmlDoc.looseApplication (server.looseApplication)
        xmlDoc.stripVersion (server.stripVersion)
    }

    protected void outputArchivePropertiesToXml(MarkupBuilder xmlDoc) {
        xmlDoc.installAppPackages ('project')

        if (project.plugins.hasPlugin("war")) {
            if (server.looseApplication) {
                xmlDoc.applicationFileName (project.war.archiveName + '.xml')
            } else {
                xmlDoc.applicationFileName (project.war.archiveName)
            }
            xmlDoc.warSourceDirectory (project.webAppDirName)
        }

        if (project.configurations.libertyRuntime != null) {
            project.configurations.libertyRuntime.dependencies.each { libertyArtifact ->
                xmlDoc.assemblyArtifact {
                    groupId (libertyArtifact.group)
                    artifactId (libertyArtifact.name)
                    version (libertyArtifact.version)
                    type ('zip')
                }

                xmlDoc.assemblyArchive (project.configurations.libertyRuntime.resolvedConfiguration.resolvedArtifacts.getAt(0).file.toString())
            }
        }

        xmlDoc.assemblyInstallDirectory (project.liberty.install.baseDir ?: project.buildDir)

        File installAppsConfigDropinsFile = ApplicationXmlDocument.getApplicationXmlFile(getServerDir(project))
        if (installAppsConfigDropinsFile.exists()) {
            xmlDoc.installAppsConfigDropins (installAppsConfigDropinsFile.toString())
        }

        if (project.plugins.hasPlugin("war") || project.plugins.hasPlugin("ear")) {
            xmlDoc.projectType (getPackagingType())
        }
    }

    protected void outputDependenciesToXml(MarkupBuilder xmlDoc) {
        Project parent = project.getParent()
        if (parent != null) {
            xmlDoc.aggregatorParentId (parent.getName())
            xmlDoc.aggregatorParentBaseDir (parent.getProjectDir())
        }

        if (project.configurations.findByName('compile') && !project.configurations.compile.dependencies.isEmpty()) {
            project.configurations.compile.dependencies.each { dependency ->
                xmlDoc.projectCompileDepenency (dependency.group + ':' + dependency.name + ':' + dependency.version)
            }
        }
    }

    protected void writePluginConfigXml(Project project) {
        new File(project.buildDir, 'liberty-plugin-config.xml').withWriter { writer ->
            def xmlDoc = new MarkupBuilder(writer)
            xmlDoc.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")
            xmlDoc.'liberty-plugin-config'() {
                outputProjectDirectoriesToXml(xmlDoc)
                outputServerPropertiesToXml(xmlDoc)
                outputArchivePropertiesToXml(xmlDoc)
                outputDependenciesToXml(xmlDoc)
            }
        }
    }

    private void writeBootstrapProperties(File file, Map<String, Object> properties) throws IOException {
        makeParentDirectory(file)
        PrintWriter writer = null
        try {
            writer = new PrintWriter(file, "UTF-8")
            writer.println(HEADER)
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                writer.print(entry.getKey())
                writer.print("=")
                writer.println((entry.getValue() != null) ? entry.getValue().toString().replace("\\", "/") : "")
            }
        } finally {
            if (writer != null) {
                writer.close()
            }
        }
    }

    private void writeJvmOptions(File file, List<String> options) throws IOException {
        makeParentDirectory(file)
        PrintWriter writer = null
        try {
            writer = new PrintWriter(file, "UTF-8")
            writer.println(HEADER)
            for (String option : options) {
                writer.println(option)
            }
        } finally {
            if (writer != null) {
                writer.close()
            }
        }
    }

    private void makeParentDirectory(File file) {
        File parentDir = file.getParentFile()
        if (parentDir != null) {
            parentDir.mkdirs()
        }
    }

    protected String getPackagingType() throws Exception{
      if (project.plugins.hasPlugin("war") || !project.tasks.withType(War).isEmpty()) {
          return "war"
      }
      else if (project.plugins.hasPlugin("ear") || !project.tasks.withType(Ear).isEmpty()) {
          return "ear"
      }
      else {
          throw new GradleException("Archive path not found. Supported formats are jar, war, and ear.")
      }
  }
}
