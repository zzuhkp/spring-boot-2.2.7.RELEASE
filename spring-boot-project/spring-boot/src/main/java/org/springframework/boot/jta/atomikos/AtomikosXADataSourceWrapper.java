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

package org.springframework.boot.jta.atomikos;

import javax.sql.XADataSource;

import org.springframework.boot.jdbc.XADataSourceWrapper;

/**
 * Atomikos XADataSource 包装
 * <p>
 * {@link XADataSourceWrapper} that uses an {@link AtomikosDataSourceBean} to wrap a
 * {@link XADataSource}.
 *
 * @author Phillip Webb
 * @since 1.2.0
 */
public class AtomikosXADataSourceWrapper implements XADataSourceWrapper {

	@Override
	public AtomikosDataSourceBean wrapDataSource(XADataSource dataSource) throws Exception {
		AtomikosDataSourceBean bean = new AtomikosDataSourceBean();
		bean.setXaDataSource(dataSource);
		return bean;
	}

}
