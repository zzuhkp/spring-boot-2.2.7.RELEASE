/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;

import org.springframework.boot.loader.tools.DefaultLaunchScript;
import org.springframework.boot.loader.tools.LaunchScript;
import org.springframework.boot.loader.tools.Layout;
import org.springframework.boot.loader.tools.LayoutFactory;
import org.springframework.boot.loader.tools.Layouts.Expanded;
import org.springframework.boot.loader.tools.Layouts.Jar;
import org.springframework.boot.loader.tools.Layouts.None;
import org.springframework.boot.loader.tools.Layouts.War;
import org.springframework.boot.loader.tools.Libraries;
import org.springframework.boot.loader.tools.Repackager;
import org.springframework.boot.loader.tools.Repackager.MainClassTimeoutWarningListener;

/**
 * Repackages existing JAR and WAR archives so that they can be executed from the command
 * line using {@literal java -jar}. With <code>layout=NONE</code> can also be used simply
 * to package a JAR with nested dependencies (and no main class, so not executable).
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Stephane Nicoll
 * @author Björn Lindström
 * @since 1.0.0
 */
@Mojo(name = "repackage", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = true,
		requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
		requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class RepackageMojo extends AbstractDependencyFilterMojo {

	private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s+");

	/**
	 * 项目信息
	 * <p>
	 * The Maven project.
	 *
	 * @since 1.0.0
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	/**
	 * 帮助类
	 * <p>
	 * Maven project helper utils.
	 *
	 * @since 1.0.0
	 */
	@Component
	private MavenProjectHelper projectHelper;

	/**
	 * 存放生成文件的目录
	 * <p>
	 * Directory containing the generated archive.
	 *
	 * @since 1.0.0
	 */
	@Parameter(defaultValue = "${project.build.directory}", required = true)
	private File outputDirectory;

	/**
	 * 打包后生成的文件名
	 * <p>
	 * Name of the generated archive.
	 *
	 * @since 1.0.0
	 */
	@Parameter(defaultValue = "${project.build.finalName}", readonly = true)
	private String finalName;

	/**
	 * 是否跳过重新打包
	 * <p>
	 * Skip the execution.
	 *
	 * @since 1.2.0
	 */
	@Parameter(property = "spring-boot.repackage.skip", defaultValue = "false")
	private boolean skip;

	/**
	 * 重新打包的文件使用的 classifier
	 * <p>
	 * Classifier to add to the repackaged archive. If not given, the main artifact will
	 * be replaced by the repackaged archive. If given, the classifier will also be used
	 * to determine the source archive to repackage: if an artifact with that classifier
	 * already exists, it will be used as source and replaced. If no such artifact exists,
	 * the main artifact will be used as source and the repackaged archive will be
	 * attached as a supplemental artifact with that classifier. Attaching the artifact
	 * allows to deploy it alongside to the original one, see <a href=
	 * "https://maven.apache.org/plugins/maven-deploy-plugin/examples/deploying-with-classifiers.html"
	 * > the maven documentation for more details</a>.
	 *
	 * @since 1.0.0
	 */
	@Parameter
	private String classifier;

	/**
	 * 是否安装重新打包后的文件到本地 maven 仓库
	 * <p>
	 * Attach the repackaged archive to be installed into your local Maven repository or
	 * deployed to a remote repository. If no classifier has been configured, it will
	 * replace the normal jar. If a {@code classifier} has been configured such that the
	 * normal jar and the repackaged jar are different, it will be attached alongside the
	 * normal jar. When the property is set to {@code false}, the repackaged archive will
	 * not be installed or deployed.
	 *
	 * @since 1.4.0
	 */
	@Parameter(defaultValue = "true")
	private boolean attach = true;

	/**
	 * 主类
	 * <p>
	 * The name of the main class. If not specified the first compiled class found that
	 * contains a 'main' method will be used.
	 *
	 * @since 1.0.0
	 */
	@Parameter
	private String mainClass;

	/**
	 * 重新打包后的文件类型
	 * <p>
	 * The type of archive (which corresponds to how the dependencies are laid out inside
	 * it). Possible values are JAR, WAR, ZIP, DIR, NONE. Defaults to a guess based on the
	 * archive type.
	 *
	 * @since 1.0.0
	 */
	@Parameter(property = "spring-boot.repackage.layout")
	private LayoutType layout;

	/**
	 * Layout 工厂
	 * <p>
	 * The layout factory that will be used to create the executable archive if no
	 * explicit layout is set. Alternative layouts implementations can be provided by 3rd
	 * parties.
	 *
	 * @since 1.5.0
	 */
	@Parameter
	private LayoutFactory layoutFactory;

	/**
	 * 必须从 fat jar 中解包才能运行的依赖
	 * <p>
	 * A list of the libraries that must be unpacked from fat jars in order to run.
	 * Specify each library as a {@code <dependency>} with a {@code <groupId>} and a
	 * {@code <artifactId>} and they will be unpacked at runtime.
	 *
	 * @since 1.1.0
	 */
	@Parameter
	private List<Dependency> requiresUnpack;

	/**
	 * Make a fully executable jar for *nix machines by prepending a launch script to the
	 * jar.
	 * <p>
	 * Currently, some tools do not accept this format so you may not always be able to
	 * use this technique. For example, {@code jar -xf} may silently fail to extract a jar
	 * or war that has been made fully-executable. It is recommended that you only enable
	 * this option if you intend to execute it directly, rather than running it with
	 * {@code java -jar} or deploying it to a servlet container.
	 *
	 * @since 1.3.0
	 */
	@Parameter(defaultValue = "false")
	private boolean executable;

	/**
	 * 内嵌脚本
	 * <p>
	 * The embedded launch script to prepend to the front of the jar if it is fully
	 * executable. If not specified the 'Spring Boot' default script will be used.
	 *
	 * @since 1.3.0
	 */
	@Parameter
	private File embeddedLaunchScript;

	/**
	 * 内嵌脚本使用的属性
	 * <p>
	 * Properties that should be expanded in the embedded launch script.
	 *
	 * @since 1.3.0
	 */
	@Parameter
	private Properties embeddedLaunchScriptProperties;

	/**
	 * 打包时是否排除 devtools 依赖
	 * <p>
	 * Exclude Spring Boot devtools from the repackaged archive.
	 *
	 * @since 1.3.0
	 */
	@Parameter(property = "spring-boot.repackage.excludeDevtools", defaultValue = "true")
	private boolean excludeDevtools = true;

	/**
	 * 是否包含 system 范围的依赖
	 * <p>
	 * Include system scoped dependencies.
	 *
	 * @since 1.4.0
	 */
	@Parameter(defaultValue = "false")
	public boolean includeSystemScope;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (this.project.getPackaging().equals("pom")) {
			getLog().debug("repackage goal could not be applied to pom project.");
			return;
		}
		if (this.skip) {
			getLog().debug("skipping repackaging as per configuration.");
			return;
		}
		repackage();
	}

	/**
	 * 重新打包
	 *
	 * @throws MojoExecutionException
	 */
	private void repackage() throws MojoExecutionException {
		// 标准打包的构件信息
		Artifact source = getSourceArtifact();
		// 重新打包后存储的文件
		File target = getTargetFile();
		Repackager repackager = getRepackager(source.getFile());
		// 查找依赖
		Set<Artifact> artifacts = filterDependencies(this.project.getArtifacts(), getFilters(getAdditionalFilters()));
		Libraries libraries = new ArtifactsLibraries(artifacts, this.requiresUnpack, getLog());
		try {
			// 重新打包
			LaunchScript launchScript = getLaunchScript();
			repackager.repackage(target, libraries, launchScript);
		} catch (IOException ex) {
			throw new MojoExecutionException(ex.getMessage(), ex);
		}
		updateArtifact(source, target, repackager.getBackupFile());
	}

	/**
	 * 获取重新打包的资源对应的 Artifact，优先使用 classfier 指定的 Artifact
	 * <p>
	 * Return the source {@link Artifact} to repackage. If a classifier is specified and
	 * an artifact with that classifier exists, it is used. Otherwise, the main artifact
	 * is used.
	 *
	 * @return the source artifact to repackage
	 */
	private Artifact getSourceArtifact() {
		Artifact sourceArtifact = getArtifact(this.classifier);
		return (sourceArtifact != null) ? sourceArtifact : this.project.getArtifact();
	}

	/**
	 * 获取 classifier 对应的 Artifact
	 *
	 * @param classifier
	 * @return
	 */
	private Artifact getArtifact(String classifier) {
		if (classifier != null) {
			for (Artifact attachedArtifact : this.project.getAttachedArtifacts()) {
				if (classifier.equals(attachedArtifact.getClassifier()) && attachedArtifact.getFile() != null
						&& attachedArtifact.getFile().isFile()) {
					return attachedArtifact;
				}
			}
		}
		return null;
	}

	/**
	 * 目标文件
	 *
	 * @return
	 */
	private File getTargetFile() {
		String classifier = (this.classifier != null) ? this.classifier.trim() : "";
		if (!classifier.isEmpty() && !classifier.startsWith("-")) {
			classifier = "-" + classifier;
		}
		if (!this.outputDirectory.exists()) {
			this.outputDirectory.mkdirs();
		}
		return new File(this.outputDirectory,
				this.finalName + classifier + "." + this.project.getArtifact().getArtifactHandler().getExtension());
	}

	/**
	 * 获取 Repackager
	 *
	 * @param source
	 * @return
	 */
	private Repackager getRepackager(File source) {
		Repackager repackager = new Repackager(source, this.layoutFactory);
		repackager.addMainClassTimeoutWarningListener(new LoggingMainClassTimeoutWarningListener());
		repackager.setMainClass(this.mainClass);
		if (this.layout != null) {
			getLog().info("Layout: " + this.layout);
			repackager.setLayout(this.layout.layout());
		}
		return repackager;
	}

	/**
	 * 获取附加的 ArtifactsFilter
	 *
	 * @return
	 */
	private ArtifactsFilter[] getAdditionalFilters() {
		List<ArtifactsFilter> filters = new ArrayList<>();
		if (this.excludeDevtools) {
			Exclude exclude = new Exclude();
			exclude.setGroupId("org.springframework.boot");
			exclude.setArtifactId("spring-boot-devtools");
			ExcludeFilter filter = new ExcludeFilter(exclude);
			filters.add(filter);
		}
		if (!this.includeSystemScope) {
			filters.add(new ScopeFilter(null, Artifact.SCOPE_SYSTEM));
		}
		return filters.toArray(new ArtifactsFilter[0]);
	}

	/**
	 * 获取启动脚本
	 *
	 * @return
	 * @throws IOException
	 */
	private LaunchScript getLaunchScript() throws IOException {
		if (this.executable || this.embeddedLaunchScript != null) {
			return new DefaultLaunchScript(this.embeddedLaunchScript, buildLaunchScriptProperties());
		}
		return null;
	}

	/**
	 * 脚本所需属性
	 *
	 * @return
	 */
	private Properties buildLaunchScriptProperties() {
		Properties properties = new Properties();
		if (this.embeddedLaunchScriptProperties != null) {
			properties.putAll(this.embeddedLaunchScriptProperties);
		}
		putIfMissing(properties, "initInfoProvides", this.project.getArtifactId());
		putIfMissing(properties, "initInfoShortDescription", this.project.getName(), this.project.getArtifactId());
		putIfMissing(properties, "initInfoDescription", removeLineBreaks(this.project.getDescription()),
				this.project.getName(), this.project.getArtifactId());
		return properties;
	}

	/**
	 * 空白字符替换为空格
	 *
	 * @param description
	 * @return
	 */
	private String removeLineBreaks(String description) {
		return (description != null) ? WHITE_SPACE_PATTERN.matcher(description).replaceAll(" ") : null;
	}

	/**
	 * 不存在则添加
	 *
	 * @param properties
	 * @param key
	 * @param valueCandidates
	 */
	private void putIfMissing(Properties properties, String key, String... valueCandidates) {
		if (!properties.containsKey(key)) {
			for (String candidate : valueCandidates) {
				if (candidate != null && !candidate.isEmpty()) {
					properties.put(key, candidate);
					return;
				}
			}
		}
	}

	private void updateArtifact(Artifact source, File target, File original) {
		if (this.attach) {
			attachArtifact(source, target);
		} else if (source.getFile().equals(target) && original.exists()) {
			String artifactId = (this.classifier != null) ? "artifact with classifier " + this.classifier
					: "main artifact";
			getLog().info(String.format("Updating %s %s to %s", artifactId, source.getFile(), original));
			source.setFile(original);
		} else if (this.classifier != null) {
			getLog().info("Creating repackaged archive " + target + " with classifier " + this.classifier);
		}
	}

	private void attachArtifact(Artifact source, File target) {
		if (this.classifier != null && !source.getFile().equals(target)) {
			getLog().info("Attaching repackaged archive " + target + " with classifier " + this.classifier);
			this.projectHelper.attachArtifact(this.project, this.project.getPackaging(), this.classifier, target);
		} else {
			String artifactId = (this.classifier != null) ? "artifact with classifier " + this.classifier
					: "main artifact";
			getLog().info("Replacing " + artifactId + " with repackaged archive");
			source.setFile(target);
		}
	}

	/**
	 * 日志打印
	 */
	private class LoggingMainClassTimeoutWarningListener implements MainClassTimeoutWarningListener {

		@Override
		public void handleTimeoutWarning(long duration, String mainMethod) {
			getLog().warn("Searching for the main-class is taking some time, "
					+ "consider using the mainClass configuration parameter");
		}

	}

	/**
	 * Archive layout types.
	 */
	public enum LayoutType {

		/**
		 * Jar Layout.
		 */
		JAR(new Jar()),

		/**
		 * War Layout.
		 */
		WAR(new War()),

		/**
		 * Zip Layout.
		 */
		ZIP(new Expanded()),

		/**
		 * Dir Layout.
		 */
		DIR(new Expanded()),

		/**
		 * No Layout.
		 */
		NONE(new None());

		private final Layout layout;

		LayoutType(Layout layout) {
			this.layout = layout;
		}

		public Layout layout() {
			return this.layout;
		}

	}

}
