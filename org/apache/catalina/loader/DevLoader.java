package org.apache.catalina.loader;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;

public class DevLoader extends WebappLoader {
	private static final String info = "org.apache.catalina.loader.DevLoader/1.0";
	private String webClassPathFile = ".#webclasspath";
	private String tomcatPluginFile = ".tomcatplugin";

	public DevLoader() {
	}

	public DevLoader(ClassLoader parent) {
		super(parent);
	}

	public void start() throws LifecycleException {
		log("Starting DevLoader...");

		super.start();

		ClassLoader cl = super.getClassLoader();
		if (!(cl instanceof WebappClassLoader)) {
			logError("Unable to install WebappClassLoader !");
			return;
		}
		WebappClassLoader devCl = (WebappClassLoader) cl;

		String webappWorkLoaderDir = buildWebappWorkLoaderDir(devCl.canonicalLoaderDir);

		List webClassPathEntries = readWebClassPathEntries();
		StringBuffer classpath = new StringBuffer();
		for (Iterator it = webClassPathEntries.iterator(); it.hasNext();) {
			String entry = (String) it.next();

			if (entry.startsWith("/")) {
				entry = buildDependencyClassPath(webappWorkLoaderDir, entry);
			}

			if (null == entry) {
				continue;
			}

			File f = new File(entry);

			if (f.exists()) {
				if ((f.isDirectory()) && (!entry.endsWith("/"))) {
					f = new File(entry + "/");
				}

				try {
					URL url = f.toURL();
					String fullPath = url.toString();

					if ((fullPath.indexOf("servlet-api") > -1) || (fullPath.indexOf("SERVLET-API") > -1)) {
						log(fullPath + " is filtered!");
						continue;
					}

					devCl.addRepository(fullPath);
					classpath.append(fullPath + File.pathSeparatorChar);
					log("loading " + fullPath);
				} catch (MalformedURLException e) {
					logError(entry + " invalid (MalformedURL)");
				}
			} else {
				logError(entry + " does not exist !");
			}
		}

		String cp = (String) getServletContext().getAttribute("org.apache.catalina.jsp_classpath");

		StringTokenizer tokenizer = new StringTokenizer(cp, File.pathSeparator);

		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();

			if ((token.charAt(0) == '/') && (token.charAt(2) == ':')) {
				token = token.substring(1);
				String s = token.substring(token.lastIndexOf(File.pathSeparatorChar) + 1);

				if ((s.indexOf("servlet-api") > -1) || (s.indexOf("SERVLET-API") > -1)) {
					log(s + " is filtered!");
					continue;
				}
			}
			classpath.append(token + File.pathSeparatorChar);
		}

		getServletContext().setAttribute("org.apache.catalina.jsp_classpath", classpath.toString());
	}

	private String buildWebappWorkLoaderDir(String canonicalLoaderDir) {
		String[] paths = canonicalLoaderDir.split("work\\\\loader");
		String[] sArr = paths[0].split("\\\\");

		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < sArr.length - 1; i++) {
			sb.append(sArr[i]);
			sb.append("/");
		}

		return sb.toString();
	}

	protected void log(String msg) {
		System.out.println("[DevLoader] - " + msg);
	}

	protected void logError(String msg) {
		System.err.println("[DevLoader] Error: " + msg);
	}

	private String buildDependencyClassPath(String webappWorkLoaderDir, String entry) {
		String path = "/target/classes";

		String dir = findDir(webappWorkLoaderDir, entry);

		if (null == dir) {
			logError("目录[" + dir + "]不存在，请检查配置!");
			return null;
		}

		return dir + path;
	}

	private static String findDir(String baseDir, String targetDir) {
		File baseDirFile = new File(baseDir);
		File[] childFiles = baseDirFile.listFiles();

		for (File childFile : childFiles) {
			String fileName = childFile.getName();
			fileName = "/" + fileName;

			if (fileName.indexOf("-") > -1) {
				if (fileName.equals(targetDir)) {
					return baseDir + targetDir;
				} else {
					String s = findDir(childFile.getAbsolutePath(), targetDir);
					if (null != s) {
						return s;
					}
				}
			}
		}

		return null;
	}

	protected List readWebClassPathEntries() {
		List rc = null;

		File prjDir = getProjectRootDir();
		if (prjDir == null) {
			return new ArrayList();
		}

		rc = loadWebClassPathFile(prjDir);

		if (rc == null)
			rc = new ArrayList();
		return rc;
	}

	protected File getProjectRootDir() {
		File rootDir = getWebappDir();
		FileFilter filter = new FileFilter() {
			public boolean accept(File file) {
				return (file.getName().equalsIgnoreCase(DevLoader.this.webClassPathFile))
						|| (file.getName().equalsIgnoreCase(DevLoader.this.tomcatPluginFile));
			}
		};
		while (rootDir != null) {
			File[] files = rootDir.listFiles(filter);
			if ((files != null) && (files.length >= 1)) {
				return files[0].getParentFile();
			}
			rootDir = rootDir.getParentFile();
		}
		return null;
	}

	protected List loadWebClassPathFile(File prjDir) {

		String projectdir = prjDir.getAbsolutePath().replace('\\', '/');

		log("projectdir=" + projectdir);
		// projectdir=E:/文档/2014/配电网协同作业/svn/01.服务器应用/trunk/pdimis/knet-pdimis-webapp/src/main/webapp

		File cpFile = new File(prjDir, this.webClassPathFile);
		if (cpFile.exists()) {
			FileReader reader = null;
			try {
				List rc = new ArrayList();
				reader = new FileReader(cpFile);
				LineNumberReader lr = new LineNumberReader(reader);
				String line = null;

				while ((line = lr.readLine()) != null) {
					line = line.replace('\\', '/');

					if (!line.startsWith("/")) {
						// 加载\xxxx-webapp\src\main\webapp下除了classes和lib之外的文件
						if (line.indexOf("target/classes") > -1) {
							line = projectdir;
						} else if (line.indexOf("target/test-classes") > -1) {
							continue;
						}
					}

					rc.add(line);
				}

				//加载maven webapp resources下的资源文件
				String resources = projectdir.substring(0, projectdir.lastIndexOf("/")) + "/resources/";
				
				rc.add(resources);

				return rc;
			} catch (IOException ioEx) {
				if (reader != null)
					try {
						reader.close();
					} catch (Exception localException) {
					}
				return null;
			}
		}

		return null;
	}

	protected ServletContext getServletContext() {
		return ((Context) getContainer()).getServletContext();
	}

	protected File getWebappDir() {
		File webAppDir = new File(getServletContext().getRealPath("/"));
		return webAppDir;
	}
}
