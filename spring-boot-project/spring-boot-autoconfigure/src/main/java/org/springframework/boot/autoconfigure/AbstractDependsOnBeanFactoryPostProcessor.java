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

package org.springframework.boot.autoconfigure;

import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BeanFactoryPostProcessor 抽象基类，可用于设置给定 bean 依赖的其他 bean
 * <p>
 * Abstract base class for a {@link BeanFactoryPostProcessor} that can be used to
 * dynamically declare that all beans of a specific type should depend on specific other
 * beans identified by name or type.
 *
 * @author Marcel Overdijk
 * @author Dave Syer
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Dmytro Nosan
 * @see BeanDefinition#setDependsOn(String[])
 * @since 1.3.0
 */
public abstract class AbstractDependsOnBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

	private final Class<?> beanClass;

	private final Class<? extends FactoryBean<?>> factoryBeanClass;

	private final Function<ListableBeanFactory, Set<String>> dependsOn;

	/**
	 * Create an instance with target bean, factory bean classes, and dependency names.
	 *
	 * @param beanClass        target bean class
	 * @param factoryBeanClass target factory bean class
	 * @param dependsOn        dependency names
	 */
	protected AbstractDependsOnBeanFactoryPostProcessor(Class<?> beanClass,
														Class<? extends FactoryBean<?>> factoryBeanClass, String... dependsOn) {
		this.beanClass = beanClass;
		this.factoryBeanClass = factoryBeanClass;
		this.dependsOn = (beanFactory) -> new HashSet<>(Arrays.asList(dependsOn));
	}

	/**
	 * Create an instance with target bean, factory bean classes, and dependency types.
	 *
	 * @param beanClass        target bean class
	 * @param factoryBeanClass target factory bean class
	 * @param dependencyTypes  dependency types
	 * @since 2.1.7
	 */
	protected AbstractDependsOnBeanFactoryPostProcessor(Class<?> beanClass,
														Class<? extends FactoryBean<?>> factoryBeanClass, Class<?>... dependencyTypes) {
		this.beanClass = beanClass;
		this.factoryBeanClass = factoryBeanClass;
		this.dependsOn = (beanFactory) -> Arrays.stream(dependencyTypes)
				.flatMap((dependencyType) -> getBeanNames(beanFactory, dependencyType).stream())
				.collect(Collectors.toSet());
	}

	/**
	 * Create an instance with target bean class and dependency names.
	 *
	 * @param beanClass target bean class
	 * @param dependsOn dependency names
	 * @since 2.0.4
	 */
	protected AbstractDependsOnBeanFactoryPostProcessor(Class<?> beanClass, String... dependsOn) {
		this(beanClass, null, dependsOn);
	}

	/**
	 * Create an instance with target bean class and dependency types.
	 *
	 * @param beanClass       target bean class
	 * @param dependencyTypes dependency types
	 * @since 2.1.7
	 */
	protected AbstractDependsOnBeanFactoryPostProcessor(Class<?> beanClass, Class<?>... dependencyTypes) {
		this(beanClass, null, dependencyTypes);
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		for (String beanName : getBeanNames(beanFactory)) {
			BeanDefinition definition = getBeanDefinition(beanName, beanFactory);
			String[] dependencies = definition.getDependsOn();
			for (String dependencyName : this.dependsOn.apply(beanFactory)) {
				dependencies = StringUtils.addStringToArray(dependencies, dependencyName);
			}
			// 设置依赖的 bean
			definition.setDependsOn(dependencies);
		}
	}

	@Override
	public int getOrder() {
		return 0;
	}

	/**
	 * 获取 bean 类型对应的名称
	 *
	 * @param beanFactory
	 * @return
	 */
	private Set<String> getBeanNames(ListableBeanFactory beanFactory) {
		Set<String> names = getBeanNames(beanFactory, this.beanClass);
		if (this.factoryBeanClass != null) {
			names.addAll(getBeanNames(beanFactory, this.factoryBeanClass));
		}
		return names;
	}

	/**
	 * 获取给定类型的 bean 名称
	 *
	 * @param beanFactory
	 * @param beanClass
	 * @return
	 */
	private static Set<String> getBeanNames(ListableBeanFactory beanFactory, Class<?> beanClass) {
		String[] names = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, beanClass, true, false);
		return Arrays.stream(names).map(BeanFactoryUtils::transformedBeanName).collect(Collectors.toSet());
	}

	/**
	 * 获取 bean 名称对应的 BeanDefinition
	 *
	 * @param beanName
	 * @param beanFactory
	 * @return
	 */
	private static BeanDefinition getBeanDefinition(String beanName, ConfigurableListableBeanFactory beanFactory) {
		try {
			return beanFactory.getBeanDefinition(beanName);
		} catch (NoSuchBeanDefinitionException ex) {
			BeanFactory parentBeanFactory = beanFactory.getParentBeanFactory();
			if (parentBeanFactory instanceof ConfigurableListableBeanFactory) {
				return getBeanDefinition(beanName, (ConfigurableListableBeanFactory) parentBeanFactory);
			}
			throw ex;
		}
	}

}
