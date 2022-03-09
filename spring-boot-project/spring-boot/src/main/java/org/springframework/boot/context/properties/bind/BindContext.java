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

package org.springframework.boot.context.properties.bind;

import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;

/**
 * BindHandler 使用的上下文信息
 * <p>
 * Context information for use by {@link BindHandler BindHandlers}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @since 2.0.0
 */
public interface BindContext {

	/**
	 * 获取 Binder
	 * <p>
	 * Return the source binder that is performing the bind operation.
	 *
	 * @return the source binder
	 */
	Binder getBinder();

	/**
	 * 返回绑定的当前深度。根绑定从深度0开始。每个后续的属性绑定都会将深度增加1。
	 * <p>
	 * Return the current depth of the binding. Root binding starts with a depth of
	 * {@code 0}. Each subsequent property binding increases the depth by {@code 1}.
	 *
	 * @return the depth of the current binding
	 */
	int getDepth();

	/**
	 * 获取被 Binder 使用的 ConfigurationPropertySource
	 * <p>
	 * Return an {@link Iterable} of the {@link ConfigurationPropertySource sources} being
	 * used by the {@link Binder}.
	 *
	 * @return the sources
	 */
	Iterable<ConfigurationPropertySource> getSources();

	/**
	 * 获取实际绑定的 ConfigurationProperty
	 * <p>
	 * Return the {@link ConfigurationProperty} actually being bound or {@code null} if
	 * the property has not yet been determined.
	 *
	 * @return the configuration property (may be {@code null}).
	 */
	ConfigurationProperty getConfigurationProperty();

}
