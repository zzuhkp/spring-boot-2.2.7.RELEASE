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

package org.springframework.boot.web.embedded.undertow;

import java.io.File;

import io.undertow.Undertow.Builder;

import org.springframework.boot.web.server.ConfigurableWebServerFactory;

/**
 * 基于 Undertow 的可配置 WebServerFactory
 * <p>
 * {@link ConfigurableWebServerFactory} for Undertow-specific features.
 *
 * @author Brian Clozel
 * @see UndertowServletWebServerFactory
 * @see UndertowReactiveWebServerFactory
 * @since 2.0.0
 */
public interface ConfigurableUndertowWebServerFactory extends ConfigurableWebServerFactory {

	/**
	 * Builder 配置
	 * <p>
	 * Add {@link UndertowBuilderCustomizer}s that should be used to customize the
	 * Undertow {@link Builder}.
	 *
	 * @param customizers the customizers to add
	 */
	void addBuilderCustomizers(UndertowBuilderCustomizer... customizers);

	/**
	 * 缓存大小
	 * <p>
	 * Set the buffer size.
	 *
	 * @param bufferSize buffer size
	 */
	void setBufferSize(Integer bufferSize);

	/**
	 * IO 线程数量
	 * <p>
	 * Set the number of IO Threads.
	 *
	 * @param ioThreads number of IO Threads
	 */
	void setIoThreads(Integer ioThreads);

	/**
	 * 工作线程数量
	 * <p>
	 * Set the number of Worker Threads.
	 *
	 * @param workerThreads number of Worker Threads
	 */
	void setWorkerThreads(Integer workerThreads);

	/**
	 * 是否使用直接缓存
	 * <p>
	 * Set whether direct buffers should be used.
	 *
	 * @param useDirectBuffers whether direct buffers should be used
	 */
	void setUseDirectBuffers(Boolean useDirectBuffers);

	/**
	 * 设置访问日志目录
	 * <p>
	 * Set the access log directory.
	 *
	 * @param accessLogDirectory access log directory
	 */
	void setAccessLogDirectory(File accessLogDirectory);

	/**
	 * 设置访问日志模式
	 * <p>
	 * Set the access log pattern.
	 *
	 * @param accessLogPattern access log pattern
	 */
	void setAccessLogPattern(String accessLogPattern);

	/**
	 * 设置访问日志前缀
	 * <p>
	 * Set the access log prefix.
	 *
	 * @param accessLogPrefix log prefix
	 */
	void setAccessLogPrefix(String accessLogPrefix);

	/**
	 * 设置访问日志后缀
	 * <p>
	 * Set the access log suffix.
	 *
	 * @param accessLogSuffix access log suffix
	 */
	void setAccessLogSuffix(String accessLogSuffix);

	/**
	 * 是否启动访问日志
	 * <p>
	 * Set whether access logs are enabled.
	 *
	 * @param accessLogEnabled whether access logs are enabled
	 */
	void setAccessLogEnabled(boolean accessLogEnabled);

	/**
	 * 是否启用访问日志循环
	 * <p>
	 * Set whether access logs rotation is enabled.
	 *
	 * @param accessLogRotate whether access logs rotation is enabled
	 */
	void setAccessLogRotate(boolean accessLogRotate);

	/**
	 * 是否处理 x-forward-* 请求头
	 * <p>
	 * Set if x-forward-* headers should be processed.
	 *
	 * @param useForwardHeaders if x-forward headers should be used
	 */
	void setUseForwardHeaders(boolean useForwardHeaders);

}
