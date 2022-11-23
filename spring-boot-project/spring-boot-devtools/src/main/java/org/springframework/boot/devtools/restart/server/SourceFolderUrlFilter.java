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

package org.springframework.boot.devtools.restart.server;

import java.net.URL;

/**
 * Filter URLs based on a source folder name. Used to match URLs from the running
 * classpath against source folders on a remote system.
 *
 * @author Phillip Webb
 * @see DefaultSourceFolderUrlFilter
 * @since 1.3.0
 */
@FunctionalInterface
public interface SourceFolderUrlFilter {

	/**
	 * 确定给定的目录是否匹配 URL
	 * <p>
	 * Determine if the specified URL matches a source folder.
	 *
	 * @param sourceFolder http 请求体中包含的目录
	 *                     the source folder
	 * @param url          类加载器对应类路径的 URL
	 *                     the URL to check
	 * @return {@code true} if the URL matches
	 */
	boolean isMatch(String sourceFolder, URL url);

}
