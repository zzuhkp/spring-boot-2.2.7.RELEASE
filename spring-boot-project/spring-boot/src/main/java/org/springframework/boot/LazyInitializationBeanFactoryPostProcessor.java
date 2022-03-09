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

package org.springframework.boot;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.core.Ordered;

import java.util.Collection;

/**
 * 未设置 lazyInit 的 BeanDefinition 设置为 true
 * <p>
 * {@link BeanFactoryPostProcessor} to set lazy-init on bean definitions that are not
 * {@link LazyInitializationExcludeFilter excluded} and have not already had a value
 * explicitly set.
 *
 * @author Andy Wilkinson
 * @author Madhura Bhave
 * @author Tyler Van Gorder
 * @author Phillip Webb
 * @see LazyInitializationExcludeFilter
 * @since 2.2.0
 */
public final class LazyInitializationBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// Take care not to force the eager init of factory beans when getting filters
		Collection<LazyInitializationExcludeFilter> filters = beanFactory
				.getBeansOfType(LazyInitializationExcludeFilter.class, false, false).values();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
			if (beanDefinition instanceof AbstractBeanDefinition) {
				postProcess(beanFactory, filters, beanName, (AbstractBeanDefinition) beanDefinition);
			}
		}
	}

	/**
	 * 未设置 lazyInit 的 BeanDefinition 设置为 true
	 *
	 * @param beanFactory
	 * @param filters
	 * @param beanName
	 * @param beanDefinition
	 */
	private void postProcess(ConfigurableListableBeanFactory beanFactory,
							 Collection<LazyInitializationExcludeFilter> filters, String beanName,
							 AbstractBeanDefinition beanDefinition) {
		Boolean lazyInit = beanDefinition.getLazyInit();
		if (lazyInit != null) {
			return;
		}
		Class<?> beanType = getBeanType(beanFactory, beanName);
		if (!isExcluded(filters, beanName, beanDefinition, beanType)) {
			beanDefinition.setLazyInit(true);
		}
	}

	/**
	 * 获取 bean 类型
	 *
	 * @param beanFactory
	 * @param beanName
	 * @return
	 */
	private Class<?> getBeanType(ConfigurableListableBeanFactory beanFactory, String beanName) {
		try {
			return beanFactory.getType(beanName, false);
		} catch (NoSuchBeanDefinitionException ex) {
			return null;
		}
	}

	/**
	 * 是否排除给定 bean
	 *
	 * @param filters
	 * @param beanName
	 * @param beanDefinition
	 * @param beanType
	 * @return
	 */
	private boolean isExcluded(Collection<LazyInitializationExcludeFilter> filters, String beanName,
							   AbstractBeanDefinition beanDefinition, Class<?> beanType) {
		if (beanType != null) {
			for (LazyInitializationExcludeFilter filter : filters) {
				if (filter.isExcluded(beanName, beanDefinition, beanType)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

}
