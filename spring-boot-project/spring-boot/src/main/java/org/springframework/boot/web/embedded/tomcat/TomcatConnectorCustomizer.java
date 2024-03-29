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

import org.apache.catalina.connector.Connector;

/**
 * 自定义 Connector 的回调
 * <p>
 * Callback interface that can be used to customize a Tomcat {@link Connector}.
 *
 * @author Dave Syer
 * @see ConfigurableTomcatWebServerFactory
 * @since 2.0.0
 */
@FunctionalInterface
public interface TomcatConnectorCustomizer {

	/**
	 * Customize the connector.
	 *
	 * @param connector the connector to customize
	 */
	void customize(Connector connector);

}
