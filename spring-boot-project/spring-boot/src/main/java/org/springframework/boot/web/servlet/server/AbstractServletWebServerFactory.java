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

package org.springframework.boot.web.servlet.server;

import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.SessionCookieConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.web.server.AbstractConfigurableWebServerFactory;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * 抽象的 ServletWebServerFactory
 * <p>
 * Abstract base class for {@link ConfigurableServletWebServerFactory} implementations.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Ivan Sopov
 * @author Eddú Meléndez
 * @author Brian Clozel
 * @since 2.0.0
 */
public abstract class AbstractServletWebServerFactory extends AbstractConfigurableWebServerFactory
		implements ConfigurableServletWebServerFactory {

	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * 上下文路径
	 */
	private String contextPath = "";

	/**
	 * 应用名称
	 */
	private String displayName;

	/**
	 * Session 配置
	 */
	private Session session = new Session();

	/**
	 * 是否注册默认 Servlet
	 *
	 */
	private boolean registerDefaultServlet = true;

	/**
	 * 文件扩展名到 mime 类型的映射
	 */
	private MimeMappings mimeMappings = new MimeMappings(MimeMappings.DEFAULT);

	/**
	 * 容器启动后的回调
	 */
	private List<ServletContextInitializer> initializers = new ArrayList<>();

	/**
	 * JSP 配置
	 */
	private Jsp jsp = new Jsp();

	/**
	 * Locale 到 Charset 的映射
	 */
	private Map<Locale, Charset> localeCharsetMappings = new HashMap<>();

	/**
	 * ServletContext 初始化参数
	 */
	private Map<String, String> initParameters = Collections.emptyMap();

	/**
	 * 文档根目录
	 */
	private final DocumentRoot documentRoot = new DocumentRoot(this.logger);

	private final StaticResourceJars staticResourceJars = new StaticResourceJars();

	/**
	 * Create a new {@link AbstractServletWebServerFactory} instance.
	 */
	public AbstractServletWebServerFactory() {
	}

	/**
	 * Create a new {@link AbstractServletWebServerFactory} instance with the specified
	 * port.
	 *
	 * @param port the port number for the web server
	 */
	public AbstractServletWebServerFactory(int port) {
		super(port);
	}

	/**
	 * Create a new {@link AbstractServletWebServerFactory} instance with the specified
	 * context path and port.
	 *
	 * @param contextPath the context path for the web server
	 * @param port        the port number for the web server
	 */
	public AbstractServletWebServerFactory(String contextPath, int port) {
		super(port);
		checkContextPath(contextPath);
		this.contextPath = contextPath;
	}

	/**
	 * Returns the context path for the web server. The path will start with "/" and not
	 * end with "/". The root context is represented by an empty string.
	 *
	 * @return the context path
	 */
	public String getContextPath() {
		return this.contextPath;
	}

	@Override
	public void setContextPath(String contextPath) {
		checkContextPath(contextPath);
		this.contextPath = contextPath;
	}

	/**
	 * 检查上下文路径
	 *
	 * @param contextPath
	 */
	private void checkContextPath(String contextPath) {
		Assert.notNull(contextPath, "ContextPath must not be null");
		if (!contextPath.isEmpty()) {
			if ("/".equals(contextPath)) {
				throw new IllegalArgumentException("Root ContextPath must be specified using an empty string");
			}
			if (!contextPath.startsWith("/") || contextPath.endsWith("/")) {
				throw new IllegalArgumentException("ContextPath must start with '/' and not end with '/'");
			}
		}
	}

	public String getDisplayName() {
		return this.displayName;
	}

	@Override
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * Flag to indicate that the default servlet should be registered.
	 *
	 * @return true if the default servlet is to be registered
	 */
	public boolean isRegisterDefaultServlet() {
		return this.registerDefaultServlet;
	}

	@Override
	public void setRegisterDefaultServlet(boolean registerDefaultServlet) {
		this.registerDefaultServlet = registerDefaultServlet;
	}

	/**
	 * Returns the mime-type mappings.
	 *
	 * @return the mimeMappings the mime-type mappings.
	 */
	public MimeMappings getMimeMappings() {
		return this.mimeMappings;
	}

	@Override
	public void setMimeMappings(MimeMappings mimeMappings) {
		this.mimeMappings = new MimeMappings(mimeMappings);
	}

	/**
	 * Returns the document root which will be used by the web context to serve static
	 * files.
	 *
	 * @return the document root
	 */
	public File getDocumentRoot() {
		return this.documentRoot.getDirectory();
	}

	@Override
	public void setDocumentRoot(File documentRoot) {
		this.documentRoot.setDirectory(documentRoot);
	}

	@Override
	public void setInitializers(List<? extends ServletContextInitializer> initializers) {
		Assert.notNull(initializers, "Initializers must not be null");
		this.initializers = new ArrayList<>(initializers);
	}

	@Override
	public void addInitializers(ServletContextInitializer... initializers) {
		Assert.notNull(initializers, "Initializers must not be null");
		this.initializers.addAll(Arrays.asList(initializers));
	}

	public Jsp getJsp() {
		return this.jsp;
	}

	@Override
	public void setJsp(Jsp jsp) {
		this.jsp = jsp;
	}

	public Session getSession() {
		return this.session;
	}

	@Override
	public void setSession(Session session) {
		this.session = session;
	}

	/**
	 * Return the Locale to Charset mappings.
	 *
	 * @return the charset mappings
	 */
	public Map<Locale, Charset> getLocaleCharsetMappings() {
		return this.localeCharsetMappings;
	}

	@Override
	public void setLocaleCharsetMappings(Map<Locale, Charset> localeCharsetMappings) {
		Assert.notNull(localeCharsetMappings, "localeCharsetMappings must not be null");
		this.localeCharsetMappings = localeCharsetMappings;
	}

	@Override
	public void setInitParameters(Map<String, String> initParameters) {
		this.initParameters = initParameters;
	}

	public Map<String, String> getInitParameters() {
		return this.initParameters;
	}

	/**
	 * 合并 ServletContextInitializer
	 *
	 * Utility method that can be used by subclasses wishing to combine the specified
	 * {@link ServletContextInitializer} parameters with those defined in this instance.
	 *
	 * @param initializers the initializers to merge
	 * @return a complete set of merged initializers (with the specified parameters
	 * appearing first)
	 */
	protected final ServletContextInitializer[] mergeInitializers(ServletContextInitializer... initializers) {
		List<ServletContextInitializer> mergedInitializers = new ArrayList<>();
		// 容器启动时设置 ServletContext 参数
		mergedInitializers.add((servletContext) -> this.initParameters.forEach(servletContext::setInitParameter));
		// 容器启动时配置 Session 信息
		mergedInitializers.add(new SessionConfiguringInitializer(this.session));
		// 添加方法参数和当前实例保存的 ServletContextInitializer 到列表
		mergedInitializers.addAll(Arrays.asList(initializers));
		mergedInitializers.addAll(this.initializers);
		return mergedInitializers.toArray(new ServletContextInitializer[0]);
	}

	/**
	 * 是否注册处理 JSP 的 Servlet
	 *
	 * Returns whether or not the JSP servlet should be registered with the web server.
	 *
	 * @return {@code true} if the servlet should be registered, otherwise {@code false}
	 */
	protected boolean shouldRegisterJspServlet() {
		return this.jsp != null && this.jsp.getRegistered()
				&& ClassUtils.isPresent(this.jsp.getClassName(), getClass().getClassLoader());
	}

	/**
	 * 获取有效的文档根目录
	 *
	 * Returns the absolute document root when it points to a valid directory, logging a
	 * warning and returning {@code null} otherwise.
	 *
	 * @return the valid document root
	 */
	protected final File getValidDocumentRoot() {
		return this.documentRoot.getValidDirectory();
	}

	protected final List<URL> getUrlsOfJarsWithMetaInfResources() {
		return this.staticResourceJars.getUrls();
	}

	protected final File getValidSessionStoreDir() {
		return getValidSessionStoreDir(true);
	}

	protected final File getValidSessionStoreDir(boolean mkdirs) {
		return this.session.getSessionStoreDirectory().getValidDirectory(mkdirs);
	}

	/**
	 * Session 配置
	 *
	 * {@link ServletContextInitializer} to apply appropriate parts of the {@link Session}
	 * configuration.
	 */
	private static class SessionConfiguringInitializer implements ServletContextInitializer {

		private final Session session;

		SessionConfiguringInitializer(Session session) {
			this.session = session;
		}

		@Override
		public void onStartup(ServletContext servletContext) throws ServletException {
			if (this.session.getTrackingModes() != null) {
				servletContext.setSessionTrackingModes(unwrap(this.session.getTrackingModes()));
			}
			configureSessionCookie(servletContext.getSessionCookieConfig());
		}

		/**
		 * 配置 Session 的 Cookie 信息
		 *
		 * @param config
		 */
		private void configureSessionCookie(SessionCookieConfig config) {
			Session.Cookie cookie = this.session.getCookie();
			if (cookie.getName() != null) {
				config.setName(cookie.getName());
			}
			if (cookie.getDomain() != null) {
				config.setDomain(cookie.getDomain());
			}
			if (cookie.getPath() != null) {
				config.setPath(cookie.getPath());
			}
			if (cookie.getComment() != null) {
				config.setComment(cookie.getComment());
			}
			if (cookie.getHttpOnly() != null) {
				config.setHttpOnly(cookie.getHttpOnly());
			}
			if (cookie.getSecure() != null) {
				config.setSecure(cookie.getSecure());
			}
			if (cookie.getMaxAge() != null) {
				config.setMaxAge((int) cookie.getMaxAge().getSeconds());
			}
		}

		/**
		 * 会话跟踪模式
		 *
		 * @param modes
		 * @return
		 */
		private Set<javax.servlet.SessionTrackingMode> unwrap(Set<Session.SessionTrackingMode> modes) {
			if (modes == null) {
				return null;
			}
			Set<javax.servlet.SessionTrackingMode> result = new LinkedHashSet<>();
			for (Session.SessionTrackingMode mode : modes) {
				result.add(javax.servlet.SessionTrackingMode.valueOf(mode.name()));
			}
			return result;
		}

	}

}
