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

package org.springframework.boot.web.embedded.tomcat;

import java.io.File;
import java.nio.charset.Charset;

import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;

import org.springframework.boot.web.server.ConfigurableWebServerFactory;

/**
 * Tomcat 可配置的 WebServerFactory
 * <p>
 * {@link ConfigurableWebServerFactory} for Tomcat-specific features.
 *
 * @author Brian Clozel
 * @see TomcatServletWebServerFactory
 * @see TomcatReactiveWebServerFactory
 * @since 2.0.0
 */
public interface ConfigurableTomcatWebServerFactory extends ConfigurableWebServerFactory {

	/**
	 * Tomcat 基本目录，未指定将使用临时目录
	 * <p>
	 * Set the Tomcat base directory. If not specified a temporary directory will be used.
	 *
	 * @param baseDirectory the tomcat base directory
	 */
	void setBaseDirectory(File baseDirectory);

	/**
	 * 设置后台延迟处理秒数
	 * <p>
	 * Sets the background processor delay in seconds.
	 *
	 * @param delay the delay in seconds
	 */
	void setBackgroundProcessorDelay(int delay);

	/**
	 * 应用到 Engine 上的 Valve
	 * <p>
	 * Add {@link Valve}s that should be applied to the Tomcat {@link Engine}.
	 *
	 * @param engineValves the valves to add
	 */
	void addEngineValves(Valve... engineValves);

	/**
	 * Connector 自定义
	 * <p>
	 * Add {@link TomcatConnectorCustomizer}s that should be added to the Tomcat
	 * {@link Connector}.
	 *
	 * @param tomcatConnectorCustomizers the customizers to add
	 */
	void addConnectorCustomizers(TomcatConnectorCustomizer... tomcatConnectorCustomizers);

	/**
	 * Context 自定义
	 * <p>
	 * Add {@link TomcatContextCustomizer}s that should be added to the Tomcat
	 * {@link Context}.
	 *
	 * @param tomcatContextCustomizers the customizers to add
	 */
	void addContextCustomizers(TomcatContextCustomizer... tomcatContextCustomizers);

	/**
	 * ProtocolHandler 自定义
	 * <p>
	 * Add {@link TomcatProtocolHandlerCustomizer}s that should be added to the Tomcat
	 * {@link Connector}.
	 *
	 * @param tomcatProtocolHandlerCustomizers the customizers to add
	 * @since 2.2.0
	 */
	void addProtocolHandlerCustomizers(TomcatProtocolHandlerCustomizer<?>... tomcatProtocolHandlerCustomizers);

	/**
	 * 解码 URL 的字符集
	 * <p>
	 * Set the character encoding to use for URL decoding. If not specified 'UTF-8' will
	 * be used.
	 *
	 * @param uriEncoding the uri encoding to set
	 */
	void setUriEncoding(Charset uriEncoding);

}
