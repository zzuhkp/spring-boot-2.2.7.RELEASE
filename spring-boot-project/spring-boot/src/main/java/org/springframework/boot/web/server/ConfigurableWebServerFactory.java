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

package org.springframework.boot.web.server;

import java.net.InetAddress;
import java.util.Set;

/**
 * 可配置的 WebServerFactory
 * <p>
 * A configurable {@link WebServerFactory}.
 *
 * @author Phillip Webb
 * @author Brian Clozel
 * @see ErrorPageRegistry
 * @since 2.0.0
 */
public interface ConfigurableWebServerFactory extends WebServerFactory, ErrorPageRegistry {

	/**
	 * 服务器监听端口
	 * <p>
	 * Sets the port that the web server should listen on. If not specified port '8080'
	 * will be used. Use port -1 to disable auto-start (i.e start the web application
	 * context but not have it listen to any port).
	 *
	 * @param port the port to set
	 */
	void setPort(int port);

	/**
	 * 服务器绑定地址
	 * <p>
	 * Sets the specific network address that the server should bind to.
	 *
	 * @param address the address to set (defaults to {@code null})
	 */
	void setAddress(InetAddress address);

	/**
	 * 处理异常的错误页面
	 * <p>
	 * Sets the error pages that will be used when handling exceptions.
	 *
	 * @param errorPages the error pages
	 */
	void setErrorPages(Set<? extends ErrorPage> errorPages);

	/**
	 * 服务器使用的 SSL 配置
	 * <p>
	 * Sets the SSL configuration that will be applied to the server's default connector.
	 *
	 * @param ssl the SSL configuration
	 */
	void setSsl(Ssl ssl);

	/**
	 * 设置 SSL store 提供信息
	 *
	 * Sets a provider that will be used to obtain SSL stores.
	 *
	 * @param sslStoreProvider the SSL store provider
	 */
	void setSslStoreProvider(SslStoreProvider sslStoreProvider);

	/**
	 * 设置 HTTP/2 配置
	 *
	 * Sets the HTTP/2 configuration that will be applied to the server.
	 *
	 * @param http2 the HTTP/2 configuration
	 */
	void setHttp2(Http2 http2);

	/**
	 * 设置压缩配置
	 *
	 * Sets the compression configuration that will be applied to the server's default
	 * connector.
	 *
	 * @param compression the compression configuration
	 */
	void setCompression(Compression compression);

	/**
	 * Sets the server header value.
	 *
	 * @param serverHeader the server header value
	 */
	void setServerHeader(String serverHeader);

}
