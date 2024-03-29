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

package org.springframework.boot.devtools.env;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.logger.DevToolsLogFactory;
import org.springframework.boot.devtools.restart.Restarter;
import org.springframework.boot.devtools.system.DevToolsEnablementDeducer;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.log.LogMessage;
import org.springframework.util.ClassUtils;

/**
 * {@link EnvironmentPostProcessor} to add properties that make sense when working at
 * development time.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Madhura Bhave
 * @since 1.3.0
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class DevToolsPropertyDefaultsPostProcessor implements EnvironmentPostProcessor {

	private static final Log logger = DevToolsLogFactory.getLog(DevToolsPropertyDefaultsPostProcessor.class);

	private static final String ENABLED = "spring.devtools.add-properties";

	private static final String WEB_LOGGING = "logging.level.web";

	private static final String[] WEB_ENVIRONMENT_CLASSES = {
			"org.springframework.web.context.ConfigurableWebEnvironment",
			"org.springframework.boot.web.reactive.context.ConfigurableReactiveWebEnvironment"};

	private static final Map<String, Object> PROPERTIES;

	static {
		Map<String, Object> properties = new HashMap<>();
		properties.put("spring.thymeleaf.cache", "false");
		properties.put("spring.freemarker.cache", "false");
		properties.put("spring.groovy.template.cache", "false");
		properties.put("spring.mustache.cache", "false");
		properties.put("server.servlet.session.persistent", "true");
		properties.put("spring.h2.console.enabled", "true");
		properties.put("spring.resources.cache.period", "0");
		properties.put("spring.resources.chain.cache", "false");
		properties.put("spring.template.provider.cache", "false");
		properties.put("spring.mvc.log-resolved-exception", "true");
		properties.put("server.error.include-stacktrace", "ALWAYS");
		properties.put("server.servlet.jsp.init-parameters.development", "true");
		properties.put("spring.reactor.debug", "true");
		PROPERTIES = Collections.unmodifiableMap(properties);
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (DevToolsEnablementDeducer.shouldEnable(Thread.currentThread()) && isLocalApplication(environment)) {
			if (canAddProperties(environment)) {
				logger.info(LogMessage.format("Devtools property defaults active! Set '%s' to 'false' to disable",
						ENABLED));
				// 添加属性源
				environment.getPropertySources().addLast(new MapPropertySource("devtools", PROPERTIES));
			}
			if (isWebApplication(environment) && !environment.containsProperty(WEB_LOGGING)) {
				logger.info(LogMessage.format(
						"For additional web related logging consider setting the '%s' property to 'DEBUG'",
						WEB_LOGGING));
			}
		}
	}

	/**
	 * 是否为本地应用
	 *
	 * @param environment
	 * @return
	 */
	private boolean isLocalApplication(ConfigurableEnvironment environment) {
		return environment.getPropertySources().get("remoteUrl") == null;
	}

	/**
	 * 是否可以添加属性
	 *
	 * @param environment
	 * @return
	 */
	private boolean canAddProperties(Environment environment) {
		if (environment.getProperty(ENABLED, Boolean.class, true)) {
			return isRestarterInitialized() || isRemoteRestartEnabled(environment);
		}
		return false;
	}

	/**
	 * Restarter 是否已经被初始化
	 *
	 * @return
	 */
	private boolean isRestarterInitialized() {
		try {
			Restarter restarter = Restarter.getInstance();
			return (restarter != null && restarter.getInitialUrls() != null);
		} catch (Exception ex) {
			return false;
		}
	}

	/**
	 * 远程 Restarter 是否已启用
	 *
	 * @param environment
	 * @return
	 */
	private boolean isRemoteRestartEnabled(Environment environment) {
		return environment.containsProperty("spring.devtools.remote.secret");
	}

	/**
	 * 是否为 Web 应用
	 *
	 * @param environment
	 * @return
	 */
	private boolean isWebApplication(Environment environment) {
		for (String candidate : WEB_ENVIRONMENT_CLASSES) {
			Class<?> environmentClass = resolveClassName(candidate, environment.getClass().getClassLoader());
			if (environmentClass != null && environmentClass.isInstance(environment)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 解析类
	 *
	 * @param candidate
	 * @param classLoader
	 * @return
	 */
	private Class<?> resolveClassName(String candidate, ClassLoader classLoader) {
		try {
			return ClassUtils.resolveClassName(candidate, classLoader);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
