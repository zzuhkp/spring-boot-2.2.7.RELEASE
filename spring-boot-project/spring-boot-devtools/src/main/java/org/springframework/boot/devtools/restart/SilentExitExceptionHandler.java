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

package org.springframework.boot.devtools.restart;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;

/**
 * 静默退出
 * <p>
 * {@link UncaughtExceptionHandler} decorator that allows a thread to exit silently.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
class SilentExitExceptionHandler implements UncaughtExceptionHandler {

	/**
	 * 代理的对象
	 */
	private final UncaughtExceptionHandler delegate;

	SilentExitExceptionHandler(UncaughtExceptionHandler delegate) {
		this.delegate = delegate;
	}

	@Override
	public void uncaughtException(Thread thread, Throwable exception) {
		if (exception instanceof SilentExitException) {
			if (isJvmExiting(thread)) {
				preventNonZeroExitCode();
			}
			// 退出主线程
			return;
		}
		if (this.delegate != null) {
			this.delegate.uncaughtException(thread, exception);
		}
	}

	/**
	 * JVM 退出
	 *
	 * @param exceptionThread
	 * @return
	 */
	private boolean isJvmExiting(Thread exceptionThread) {
		for (Thread thread : getAllThreads()) {
			if (thread != exceptionThread && thread.isAlive() && !thread.isDaemon()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 获取所有线程
	 *
	 * @return
	 */
	protected Thread[] getAllThreads() {
		ThreadGroup rootThreadGroup = getRootThreadGroup();
		Thread[] threads = new Thread[32];
		int count = rootThreadGroup.enumerate(threads);
		while (count == threads.length) {
			threads = new Thread[threads.length * 2];
			count = rootThreadGroup.enumerate(threads);
		}
		return Arrays.copyOf(threads, count);
	}

	/**
	 * 获取根 ThreadGroup
	 *
	 * @return
	 */
	private ThreadGroup getRootThreadGroup() {
		ThreadGroup candidate = Thread.currentThread().getThreadGroup();
		while (candidate.getParent() != null) {
			candidate = candidate.getParent();
		}
		return candidate;
	}

	/**
	 * 退出
	 */
	protected void preventNonZeroExitCode() {
		System.exit(0);
	}

	/**
	 * 设置线程异常处理器为当前类的对象实例
	 *
	 * @param thread
	 */
	static void setup(Thread thread) {
		UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
		if (!(handler instanceof SilentExitExceptionHandler)) {
			handler = new SilentExitExceptionHandler(handler);
			thread.setUncaughtExceptionHandler(handler);
		}
	}

	static void exitCurrentThread() {
		throw new SilentExitException();
	}

	private static class SilentExitException extends RuntimeException {

	}

}
