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

package org.springframework.boot.devtools.remote.server;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.util.Assert;

/**
 * 基于 URL 的处理器映射
 * <p>
 * {@link HandlerMapper} implementation that maps incoming URLs.
 *
 * @author Rob Winch
 * @author Phillip Webb
 * @since 1.3.0
 */
public class UrlHandlerMapper implements HandlerMapper {

	private final String requestUri;

	private final Handler handler;

	/**
	 * Create a new {@link UrlHandlerMapper}.
	 *
	 * @param url     the URL to map
	 * @param handler the handler to use
	 */
	public UrlHandlerMapper(String url, Handler handler) {
		Assert.hasLength(url, "URL must not be empty");
		Assert.isTrue(url.startsWith("/"), "URL must start with '/'");
		this.requestUri = url;
		this.handler = handler;
	}

	@Override
	public Handler getHandler(ServerHttpRequest request) {
		if (this.requestUri.equals(request.getURI().getPath())) {
			return this.handler;
		}
		return null;
	}

}
