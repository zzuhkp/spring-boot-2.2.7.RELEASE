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

import java.beans.Introspector;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.restart.FailureHandler.Outcome;
import org.springframework.boot.devtools.restart.classloader.ClassLoaderFiles;
import org.springframework.boot.devtools.restart.classloader.RestartClassLoader;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.boot.system.JavaVersion;
import org.springframework.cglib.core.ClassNameReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Allows a running application to be restarted with an updated classpath. The restarter
 * works by creating a new application ClassLoader that is split into two parts. The top
 * part contains static URLs that don't change (for example 3rd party libraries and Spring
 * Boot itself) and the bottom part contains URLs where classes and resources might be
 * updated.
 * <p>
 * The Restarter should be {@link #initialize(String[]) initialized} early to ensure that
 * classes are loaded multiple times. Mostly the {@link RestartApplicationListener} can be
 * relied upon to perform initialization, however, you may need to call
 * {@link #initialize(String[])} directly if your SpringApplication arguments are not
 * identical to your main method arguments.
 * <p>
 * By default, applications running in an IDE (i.e. those not packaged as "fat jars") will
 * automatically detect URLs that can change. It's also possible to manually configure
 * URLs or class file updates for remote restart scenarios.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @see RestartApplicationListener
 * @see #initialize(String[])
 * @see #getInstance()
 * @see #restart()
 * @since 1.3.0
 */
public class Restarter {

	private static final Object INSTANCE_MONITOR = new Object();

	private static final String[] NO_ARGS = {};

	/**
	 * 单例对象
	 */
	private static Restarter instance;

	/**
	 * URL
	 */
	private final Set<URL> urls = new LinkedHashSet<>();

	private final ClassLoaderFiles classLoaderFiles = new ClassLoaderFiles();

	private final Map<String, Object> attributes = new HashMap<>();

	/**
	 * LeakSafeThread 队列
	 */
	private final BlockingDeque<LeakSafeThread> leakSafeThreads = new LinkedBlockingDeque<>();

	private final Lock stopLock = new ReentrantLock();

	private final Object monitor = new Object();

	private Log logger = new DeferredLog();

	private final boolean forceReferenceCleanup;

	/**
	 * 是否启用
	 */
	private boolean enabled = true;

	/**
	 * 初始化 URL
	 */
	private URL[] initialUrls;

	/**
	 * 主类
	 */
	private final String mainClassName;

	/**
	 * 线程上下文类加载器
	 */
	private final ClassLoader applicationClassLoader;

	/**
	 * 主方法参数
	 */
	private final String[] args;

	/**
	 * 异常处理器
	 */
	private final UncaughtExceptionHandler exceptionHandler;

	/**
	 * 是否初始化结束
	 */
	private boolean finished = false;

	private final List<ConfigurableApplicationContext> rootContexts = new CopyOnWriteArrayList<>();

	/**
	 * Internal constructor to create a new {@link Restarter} instance.
	 *
	 * @param thread                the source thread
	 * @param args                  the application arguments
	 * @param forceReferenceCleanup if soft/weak reference cleanup should be forced
	 * @param initializer           the restart initializer
	 * @see #initialize(String[])
	 */
	protected Restarter(Thread thread, String[] args, boolean forceReferenceCleanup, RestartInitializer initializer) {
		Assert.notNull(thread, "Thread must not be null");
		Assert.notNull(args, "Args must not be null");
		Assert.notNull(initializer, "Initializer must not be null");
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Creating new Restarter for thread " + thread);
		}
		// 先设置线程处理器，重启后通过抛出异常的方式退出主线程
		SilentExitExceptionHandler.setup(thread);
		this.forceReferenceCleanup = forceReferenceCleanup;
		this.initialUrls = initializer.getInitialUrls(thread);
		this.mainClassName = getMainClassName(thread);
		this.applicationClassLoader = thread.getContextClassLoader();
		this.args = args;
		this.exceptionHandler = thread.getUncaughtExceptionHandler();
		this.leakSafeThreads.add(new LeakSafeThread());
	}

	/**
	 * 获取主方法所在类
	 *
	 * @param thread
	 * @return
	 */
	private String getMainClassName(Thread thread) {
		try {
			return new MainMethod(thread).getDeclaringClassName();
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * 初始化
	 *
	 * @param restartOnInitialize 是否重启
	 */
	protected void initialize(boolean restartOnInitialize) {
		preInitializeLeakyClasses();
		if (this.initialUrls != null) {
			this.urls.addAll(Arrays.asList(this.initialUrls));
			if (restartOnInitialize) {
				this.logger.debug("Immediately restarting application");
				immediateRestart();
			}
		}
	}

	/**
	 * 立即重启
	 */
	private void immediateRestart() {
		try {
			// 等待新线程执行结束
			getLeakSafeThread().callAndWait(() -> {
				start(FailureHandler.NONE);
				cleanupCaches();
				return null;
			});
		} catch (Exception ex) {
			this.logger.warn("Unable to initialize restarter", ex);
		}
		// 再通过抛出异常的方式退出主线程
		SilentExitExceptionHandler.exitCurrentThread();
	}

	/**
	 * CGLIB ClassNameReader EARLY_EXIT 字段初始化
	 * <p>
	 * CGLIB has a private exception field which needs to initialized early to ensure that
	 * the stacktrace doesn't retain a reference to the RestartClassLoader.
	 */
	private void preInitializeLeakyClasses() {
		try {
			Class<?> readerClass = ClassNameReader.class;
			Field field = readerClass.getDeclaredField("EARLY_EXIT");
			field.setAccessible(true);
			((Throwable) field.get(null)).fillInStackTrace();
		} catch (Exception ex) {
			this.logger.warn("Unable to pre-initialize classes", ex);
		}
	}

	/**
	 * Set if restart support is enabled.
	 *
	 * @param enabled if restart support is enabled
	 */
	private void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Add additional URLs to be includes in the next restart.
	 *
	 * @param urls the urls to add
	 */
	public void addUrls(Collection<URL> urls) {
		Assert.notNull(urls, "Urls must not be null");
		this.urls.addAll(urls);
	}

	/**
	 * Add additional {@link ClassLoaderFiles} to be included in the next restart.
	 *
	 * @param classLoaderFiles the files to add
	 */
	public void addClassLoaderFiles(ClassLoaderFiles classLoaderFiles) {
		Assert.notNull(classLoaderFiles, "ClassLoaderFiles must not be null");
		this.classLoaderFiles.addAll(classLoaderFiles);
	}

	/**
	 * Return a {@link ThreadFactory} that can be used to create leak safe threads.
	 *
	 * @return a leak safe thread factory
	 */
	public ThreadFactory getThreadFactory() {
		return new LeakSafeThreadFactory();
	}

	/**
	 * Restart the running application.
	 */
	public void restart() {
		restart(FailureHandler.NONE);
	}

	/**
	 * 重启
	 * <p>
	 * Restart the running application.
	 *
	 * @param failureHandler a failure handler to deal with application that doesn't start
	 */
	public void restart(FailureHandler failureHandler) {
		if (!this.enabled) {
			this.logger.debug("Application restart is disabled");
			return;
		}
		this.logger.debug("Restarting application");
		getLeakSafeThread().call(() -> {
			Restarter.this.stop();
			Restarter.this.start(failureHandler);
			return null;
		});
	}

	/**
	 * 重启
	 * <p>
	 * Start the application.
	 *
	 * @param failureHandler a failure handler for application that won't start
	 * @throws Exception in case of errors
	 */
	protected void start(FailureHandler failureHandler) throws Exception {
		do {
			Throwable error = doStart();
			if (error == null) {
				return;
			}
			if (failureHandler.handle(error) == Outcome.ABORT) {
				return;
			}
		}
		while (true);
	}

	private Throwable doStart() throws Exception {
		Assert.notNull(this.mainClassName, "Unable to find the main class to restart");
		URL[] urls = this.urls.toArray(new URL[0]);
		ClassLoaderFiles updatedFiles = new ClassLoaderFiles(this.classLoaderFiles);
		// 使用新的类加载器加载变化的类
		ClassLoader classLoader = new RestartClassLoader(this.applicationClassLoader, urls, updatedFiles, this.logger);
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Starting application " + this.mainClassName + " with URLs " + Arrays.asList(urls));
		}
		return relaunch(classLoader);
	}

	/**
	 * 使用给定的类加载器重启应用
	 * <p>
	 * Relaunch the application using the specified classloader.
	 *
	 * @param classLoader the classloader to use
	 * @return any exception that caused the launch to fail or {@code null}
	 * @throws Exception in case of errors
	 */
	protected Throwable relaunch(ClassLoader classLoader) throws Exception {
		RestartLauncher launcher = new RestartLauncher(classLoader, this.mainClassName, this.args,
				this.exceptionHandler);
		launcher.start();
		launcher.join();
		return launcher.getError();
	}

	/**
	 * 停止
	 * <p>
	 * Stop the application.
	 *
	 * @throws Exception in case of errors
	 */
	protected void stop() throws Exception {
		this.logger.debug("Stopping application");
		this.stopLock.lock();
		try {
			for (ConfigurableApplicationContext context : this.rootContexts) {
				context.close();
				this.rootContexts.remove(context);
			}
			cleanupCaches();
			if (this.forceReferenceCleanup) {
				forceReferenceCleanup();
			}
		} finally {
			this.stopLock.unlock();
		}
		System.gc();
		System.runFinalization();
	}

	private void cleanupCaches() throws Exception {
		Introspector.flushCaches();
		cleanupKnownCaches();
	}

	/**
	 * 清理缓存
	 *
	 * @throws Exception
	 */
	private void cleanupKnownCaches() throws Exception {
		// Whilst not strictly necessary it helps to cleanup soft reference caches
		// early rather than waiting for memory limits to be reached
		ResolvableType.clearCache();
		cleanCachedIntrospectionResultsCache();
		ReflectionUtils.clearCache();
		clearAnnotationUtilsCache();
		if (!JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.NINE)) {
			clear("com.sun.naming.internal.ResourceManager", "propertiesCache");
		}
	}

	/**
	 * 清理缓存
	 *
	 * @throws Exception
	 */
	private void cleanCachedIntrospectionResultsCache() throws Exception {
		clear(CachedIntrospectionResults.class, "acceptedClassLoaders");
		clear(CachedIntrospectionResults.class, "strongClassCache");
		clear(CachedIntrospectionResults.class, "softClassCache");
	}

	/**
	 * 清理缓存
	 *
	 * @throws Exception
	 */
	private void clearAnnotationUtilsCache() throws Exception {
		try {
			AnnotationUtils.clearCache();
		} catch (Throwable ex) {
			clear(AnnotationUtils.class, "findAnnotationCache");
			clear(AnnotationUtils.class, "annotatedInterfaceCache");
		}
	}

	/**
	 * 清理缓存
	 *
	 * @param className
	 * @param fieldName
	 */
	private void clear(String className, String fieldName) {
		try {
			clear(Class.forName(className), fieldName);
		} catch (Exception ex) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Unable to clear field " + className + " " + fieldName, ex);
			}
		}
	}

	/**
	 * 清理缓存
	 *
	 * @param type
	 * @param fieldName
	 * @throws Exception
	 */
	private void clear(Class<?> type, String fieldName) throws Exception {
		try {
			Field field = type.getDeclaredField(fieldName);
			field.setAccessible(true);
			Object instance = field.get(null);
			if (instance instanceof Set) {
				((Set<?>) instance).clear();
			}
			if (instance instanceof Map) {
				((Map<?, ?>) instance).keySet().removeIf(this::isFromRestartClassLoader);
			}
		} catch (Exception ex) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Unable to clear field " + type + " " + fieldName, ex);
			}
		}
	}

	/**
	 * 是否 RestartClassLoader 加载的类
	 *
	 * @param object
	 * @return
	 */
	private boolean isFromRestartClassLoader(Object object) {
		return (object instanceof Class && ((Class<?>) object).getClassLoader() instanceof RestartClassLoader);
	}

	/**
	 * 通过内存溢出清除软引用、弱引用
	 * <p>
	 * Cleanup any soft/weak references by forcing an {@link OutOfMemoryError} error.
	 */
	private void forceReferenceCleanup() {
		try {
			final List<long[]> memory = new LinkedList<>();
			while (true) {
				memory.add(new long[102400]);
			}
		} catch (OutOfMemoryError ex) {
			// Expected
		}
	}

	/**
	 * 初始化结束
	 * <p>
	 * Called to finish {@link Restarter} initialization when application logging is
	 * available.
	 */
	void finish() {
		synchronized (this.monitor) {
			if (!isFinished()) {
				this.logger = DeferredLog.replay(this.logger, LogFactory.getLog(getClass()));
				this.finished = true;
			}
		}
	}

	/**
	 * 是否结束初始化
	 *
	 * @return
	 */
	boolean isFinished() {
		synchronized (this.monitor) {
			return this.finished;
		}
	}

	/**
	 * 准备上下文
	 *
	 * @param applicationContext
	 */
	void prepare(ConfigurableApplicationContext applicationContext) {
		if (applicationContext != null && applicationContext.getParent() != null) {
			return;
		}
		if (applicationContext instanceof GenericApplicationContext) {
			prepare((GenericApplicationContext) applicationContext);
		}
		this.rootContexts.add(applicationContext);
	}

	void remove(ConfigurableApplicationContext applicationContext) {
		if (applicationContext != null) {
			this.rootContexts.remove(applicationContext);
		}
	}

	/**
	 * 设置资源加载器
	 *
	 * @param applicationContext
	 */
	private void prepare(GenericApplicationContext applicationContext) {
		ResourceLoader resourceLoader = new ClassLoaderFilesResourcePatternResolver(applicationContext,
				this.classLoaderFiles);
		applicationContext.setResourceLoader(resourceLoader);
	}

	/**
	 * 获取 LeakSafeThread
	 *
	 * @return
	 */
	private LeakSafeThread getLeakSafeThread() {
		try {
			return this.leakSafeThreads.takeFirst();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * 获取或添加属性
	 *
	 * @param name
	 * @param objectFactory
	 * @return
	 */
	public Object getOrAddAttribute(String name, final ObjectFactory<?> objectFactory) {
		synchronized (this.attributes) {
			if (!this.attributes.containsKey(name)) {
				this.attributes.put(name, objectFactory.getObject());
			}
			return this.attributes.get(name);
		}
	}

	/**
	 * 移除属性
	 *
	 * @param name
	 * @return
	 */
	public Object removeAttribute(String name) {
		synchronized (this.attributes) {
			return this.attributes.remove(name);
		}
	}

	/**
	 * Return the initial set of URLs as configured by the {@link RestartInitializer}.
	 *
	 * @return the initial URLs or {@code null}
	 */
	public URL[] getInitialUrls() {
		return this.initialUrls;
	}

	/**
	 * Initialize and disable restart support.
	 */
	public static void disable() {
		initialize(NO_ARGS, false, RestartInitializer.NONE);
		getInstance().setEnabled(false);
	}

	/**
	 * 初始化
	 * <p>
	 * Initialize restart support. See
	 * {@link #initialize(String[], boolean, RestartInitializer)} for details.
	 *
	 * @param args main application arguments
	 * @see #initialize(String[], boolean, RestartInitializer)
	 */
	public static void initialize(String[] args) {
		initialize(args, false, new DefaultRestartInitializer());
	}

	/**
	 * 初始化
	 * <p>
	 * Initialize restart support. See
	 * {@link #initialize(String[], boolean, RestartInitializer)} for details.
	 *
	 * @param args        main application arguments
	 * @param initializer the restart initializer
	 * @see #initialize(String[], boolean, RestartInitializer)
	 */
	public static void initialize(String[] args, RestartInitializer initializer) {
		initialize(args, false, initializer, true);
	}

	/**
	 * 初始化
	 * <p>
	 * Initialize restart support. See
	 * {@link #initialize(String[], boolean, RestartInitializer)} for details.
	 *
	 * @param args                  main application arguments
	 * @param forceReferenceCleanup if forcing of soft/weak reference should happen on
	 * @see #initialize(String[], boolean, RestartInitializer)
	 */
	public static void initialize(String[] args, boolean forceReferenceCleanup) {
		initialize(args, forceReferenceCleanup, new DefaultRestartInitializer());
	}

	/**
	 * 初始化
	 * <p>
	 * Initialize restart support. See
	 * {@link #initialize(String[], boolean, RestartInitializer, boolean)} for details.
	 *
	 * @param args                  main application arguments
	 * @param forceReferenceCleanup if forcing of soft/weak reference should happen on
	 * @param initializer           the restart initializer
	 * @see #initialize(String[], boolean, RestartInitializer)
	 */
	public static void initialize(String[] args, boolean forceReferenceCleanup, RestartInitializer initializer) {
		initialize(args, forceReferenceCleanup, initializer, true);
	}

	/**
	 * 初始化
	 * <p>
	 * Initialize restart support for the current application. Called automatically by
	 * {@link RestartApplicationListener} but can also be called directly if main
	 * application arguments are not the same as those passed to the
	 * {@link SpringApplication}.
	 *
	 * @param args                  main 方法参数
	 *                              main application arguments
	 * @param forceReferenceCleanup if forcing of soft/weak reference should happen on
	 *                              each restart. This will slow down restarts and is intended primarily for testing
	 * @param initializer           初始参数
	 *                              the restart initializer
	 * @param restartOnInitialize   是否重启
	 *                              if the restarter should be restarted immediately when
	 *                              the {@link RestartInitializer} returns non {@code null} results
	 */
	public static void initialize(String[] args, boolean forceReferenceCleanup, RestartInitializer initializer,
								  boolean restartOnInitialize) {
		Restarter localInstance = null;
		synchronized (INSTANCE_MONITOR) {
			if (instance == null) {
				// 初始化
				localInstance = new Restarter(Thread.currentThread(), args, forceReferenceCleanup, initializer);
				instance = localInstance;
			}
		}
		if (localInstance != null) {
			localInstance.initialize(restartOnInitialize);
		}
	}

	/**
	 * 获取实例
	 * <p>
	 * Return the active {@link Restarter} instance. Cannot be called before
	 * {@link #initialize(String[]) initialization}.
	 *
	 * @return the restarter
	 */
	public static Restarter getInstance() {
		synchronized (INSTANCE_MONITOR) {
			Assert.state(instance != null, "Restarter has not been initialized");
			return instance;
		}
	}

	/**
	 * 设置实例
	 * <p>
	 * Set the restarter instance (useful for testing).
	 *
	 * @param instance the instance to set
	 */
	static void setInstance(Restarter instance) {
		synchronized (INSTANCE_MONITOR) {
			Restarter.instance = instance;
		}
	}

	/**
	 * Clear the instance. Primarily provided for tests and not usually used in
	 * application code.
	 */
	public static void clearInstance() {
		synchronized (INSTANCE_MONITOR) {
			instance = null;
		}
	}

	/**
	 * Thread that is created early so not to retain the {@link RestartClassLoader}.
	 */
	private class LeakSafeThread extends Thread {

		private Callable<?> callable;

		private Object result;

		LeakSafeThread() {
			setDaemon(false);
		}

		void call(Callable<?> callable) {
			this.callable = callable;
			start();
		}

		@SuppressWarnings("unchecked")
		<V> V callAndWait(Callable<V> callable) {
			this.callable = callable;
			start();
			try {
				// 异步调用并等待 cllable 返回结果
				join();
				return (V) this.result;
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public void run() {
			// We are safe to refresh the ActionThread (and indirectly call
			// AccessController.getContext()) since our stack doesn't include the
			// RestartClassLoader
			try {
				// 当前线程对象从 leakSafeThreads 取出来后，异步执行任务前，再放一个线程到队列，便于下次获取
				Restarter.this.leakSafeThreads.put(new LeakSafeThread());
				this.result = this.callable.call();
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(1);
			}
		}

	}

	/**
	 * {@link ThreadFactory} that creates a leak safe thread.
	 */
	private class LeakSafeThreadFactory implements ThreadFactory {

		@Override
		public Thread newThread(Runnable runnable) {
			return getLeakSafeThread().callAndWait(() -> {
				Thread thread = new Thread(runnable);
				thread.setContextClassLoader(Restarter.this.applicationClassLoader);
				return thread;
			});
		}

	}

}
