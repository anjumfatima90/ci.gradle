package net.wasdev.wlp.gradle.plugins.tasks.extensions.thin;

import java.io.File
import java.io.IOException
import java.security.NoSuchAlgorithmException
import java.util.zip.ZipException

import com.ibm.ws.app.manager.springboot.util.SpringBootThinUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;


public class ThinTask extends DefaultTask{
	
	/**
	 * Library index cache as a Directory
	 */
	@Input
	public boolean putLibCacheInDirectory = false;
	
	/*
	 * Name of the generated archive.
	 */
	private String finalName;
	
	/*
	 * Extension of the generated archive.
	 */
	private String extension;
	
	@Input
	private String archiveName
	
    @TaskAction
    public void doExecute() throws GradleException {
		try {
			File sourceFatJar = getTargetFile();
			setFinalNameAndExtension(sourceFatJar);
			thin(sourceFatJar);
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new GradleException(e);
		}
    		
    }

	private void thin(File sourceFatJar) throws ZipException, IOException, NoSuchAlgorithmException{
		File targetThinJar = new File(project.getBuildDir(), "libs/thin-" + finalName + "." + extension);
		String libFile = "libs/libIndexCache";
		if(putLibCacheInDirectory) {
			libFile += "-" + finalName;
		} else {
			libFile += "-" + finalName + ".zip";
		}
		File libIndexCache = new File(project.getBuildDir(), libFile);
		logger.info("Thinning " + extension + ": "+ targetThinJar.getAbsolutePath());
		logger.info("Lib index cache: "+ libIndexCache.getAbsolutePath());
		SpringBootThinUtil thinUtil = new SpringBootThinUtil(sourceFatJar, targetThinJar, libIndexCache,
			putLibCacheInDirectory);
		thinUtil.execute();
		
	}
	
	private File getTargetFile() {
		File sourceFatJar;
		File buildLibsDir = new File(project.getBuildDir(), "libs");
		File[] files = buildLibsDir.listFiles();	
		for(File file: files) {
			if (file.getName().startsWith(project.getName()) && !file.getName().endsWith(".original")) {
				sourceFatJar = file;
			}
		}
		return sourceFatJar;
	}
	
	
	private void setFinalNameAndExtension(File sourceFatJar) {
		int endIndex = sourceFatJar.getName().lastIndexOf('.');
		this.finalName = sourceFatJar.getName().substring(0, endIndex);
		this.extension = sourceFatJar.getName().substring(endIndex+1);
	}


}
