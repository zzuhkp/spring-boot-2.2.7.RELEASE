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
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletContext;

import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletContextInitializer;

/**
 * 可配置的 ServletWebServerFactory
 * <p>
 * A configurable {@link ServletWebServerFactory}.
 *
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Eddú Meléndez
 * @author Brian Clozel
 * @see ServletWebServerFactory
 * @see WebServerFactoryCustomizer
 * @since 2.0.0
 */
public interface ConfigurableServletWebServerFactory extends ConfigurableWebServerFactory, ServletWebServerFactory {

	/**
	 * 上下文路径
	 * <p>
	 * Sets the context path for the web server. The context should start with a "/"
	 * character but not end with a "/" character. The default context path can be
	 * specified using an empty string.
	 *
	 * @param contextPath the contextPath to set
	 */
	void setContextPath(String contextPath);

	/**
	 * 应用名称
	 * <p>
	 * Sets the display name of the application deployed in the web server.
	 *
	 * @param displayName the displayName to set
	 * @since 1.3.0
	 */
	void setDisplayName(String displayName);

	/**
	 * Session 配置
	 * <p>
	 * Sets the configuration that will be applied to the container's HTTP session
	 * support.
	 *
	 * @param session the session configuration
	 */
	void setSession(Session session);

	/**
	 * 是否注册默认 Servlet
	 * <p>
	 * Set if the DefaultServlet should be registered. Defaults to {@code true} so that
	 * files from the {@link #setDocumentRoot(File) document root} will be served.
	 *
	 * @param registerDefaultServlet if the default servlet should be registered
	 */
	void setRegisterDefaultServlet(boolean registerDefaultServlet);

	/**
	 * mime 类型映射
	 * <p>
	 * Sets the mime-type mappings.
	 *
	 * @param mimeMappings the mime type mappings (defaults to
	 *                     {@link MimeMappings#DEFAULT})
	 */
	void setMimeMappings(MimeMappings mimeMappings);

	/**
	 * 文档根目录
	 * <p>
	 * Sets the document root directory which will be used by the web context to serve
	 * static files.
	 *
	 * @param documentRoot the document root or {@code null} if not required
	 */
	void setDocumentRoot(File documentRoot);

	/**
	 * 设置初始化回调
	 * <p>
	 * Sets {@link ServletContextInitializer} that should be applied in addition to
	 * {@link ServletWebServerFactory#getWebServer(ServletContextInitializer...)}
	 * parameters. This method will replace any previously set or added initializers.
	 *
	 * @param initializers the initializers to set
	 * @see #addInitializers
	 */
	void setInitializers(List<? extends ServletContextInitializer> initializers);

	/**
	 * 添加初始化回调
	 * <p>
	 * Add {@link ServletContextInitializer}s to those that should be applied in addition
	 * to {@link ServletWebServerFactory#getWebServer(ServletContextInitializer...)}
	 * parameters.
	 *
	 * @param initializers the initializers to add
	 * @see #setInitializers
	 */
	void addInitializers(ServletContextInitializer... initializers);

	/**
	 * JSP Servlet 配置
	 * <p>
	 * Sets the configuration that will be applied to the server's JSP servlet.
	 *
	 * @param jsp the JSP servlet configuration
	 */
	void setJsp(Jsp jsp);

	/**
	 * Locale 到 Charset 的映射
	 * <p>
	 * Sets the Locale to Charset mappings.
	 *
	 * @param localeCharsetMappings the Locale to Charset mappings
	 */
	void setLocaleCharsetMappings(Map<Locale, Charset> localeCharsetMappings);

	/**
	 * ServletContext 初始化参数
	 * <p>
	 * Sets the init parameters that are applied to the container's
	 * {@link ServletContext}.
	 *
	 * @param initParameters the init parameters
	 */
	void setInitParameters(Map<String, String> initParameters);

}
