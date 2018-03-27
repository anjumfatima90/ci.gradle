package net.wasdev.wlp.gradle.plugins.tasks.extensions.thin;

import java.io.File
import java.io.IOException
import java.security.NoSuchAlgorithmException
import java.util.zip.ZipException

import net.wasdev.wlp.common.springboot.util.SpringBootThinUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import org.gradle.api.plugins.*;


public class ThinTask extends DefaultTask{

	/*
	 * Name of the generated archive.
	 */
	private String finalName;

	/*
	 * Extension of the generated archive.
	 */
	private String extension;


	@TaskAction
	public void doExecute() throws GradleException {
		try {
			File springAppFile = getTargetFile();
			thin(springAppFile);
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new GradleException(e);
		}
	}

	private void thin(File springAppFile) throws ZipException, IOException, NoSuchAlgorithmException{
		File thinSpringAppFile = new File(project.getBuildDir(), "libs/thin-" + finalName + "." + "spr");
		File libIndexCache = new File(project.getBuildDir(), "libs/lib.index.cache");
		logger.info("Thinning " + extension + ": "+ thinSpringAppFile.getAbsolutePath());
		logger.info("Lib index cache: "+ libIndexCache.getAbsolutePath());
		SpringBootThinUtil thinUtil = new SpringBootThinUtil(springAppFile, thinSpringAppFile, libIndexCache);
		thinUtil.execute();
	}

	private File getTargetFile() {
		File springAppFile;
		File buildLibsDir = new File(project.getBuildDir(), "libs");
		extension = getPackagingType();
		switch(extension) {
			case "jar":
				springAppFile= new File(buildLibsDir, project.jar.archiveName);
				break;
			case "war":
				springAppFile= new File(buildLibsDir, project.war.archiveName);
				break;
			default:
				break;
		}
		if(springAppFile.exists()) {
			finalName = springAppFile.getName().substring(0, springAppFile.getName().lastIndexOf("."));
		}
		return springAppFile;
	}


	private String getPackagingType() throws Exception{
		if (project.plugins.hasPlugin("org.springframework.boot") && (project.getTasks().findByPath(":jar") != null || project.getTasks().findByPath(":" + project.getName() + ":jar") != null)) {
			return "jar";
		} else if (project.plugins.hasPlugin("org.springframework.boot") && (project.getTasks().findByPath(":war") != null || project.getTasks().findByPath(":" + project.getName() + ":war") != null)) {
			return "war";
		} else {
			throw new GradleException("Archive path not found. Supported formats are jar and war.");
		}
	}
}
