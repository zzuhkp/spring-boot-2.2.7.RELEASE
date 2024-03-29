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

package org.springframework.boot.devtools.filewatch;

import java.util.Set;

/**
 * 文件改变的回调接口
 * <p>
 * Callback interface when file changes are detected.
 *
 * @author Andy Clement
 * @author Phillip Webb
 * @since 1.3.0
 */
@FunctionalInterface
public interface FileChangeListener {

	/**
	 * Called when files have been changed.
	 *
	 * @param changeSet 改变的文件
	 *                  a set of the {@link ChangedFiles}
	 */
	void onChange(Set<ChangedFiles> changeSet);

}
