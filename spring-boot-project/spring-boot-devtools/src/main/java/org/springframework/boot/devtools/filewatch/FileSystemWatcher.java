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

import org.springframework.util.Assert;

import java.io.File;
import java.io.FileFilter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Watches specific folders for file changes.
 *
 * @author Andy Clement
 * @author Phillip Webb
 * @see FileChangeListener
 * @since 1.3.0
 */
public class FileSystemWatcher {

	private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(1000);

	private static final Duration DEFAULT_QUIET_PERIOD = Duration.ofMillis(400);

	/**
	 * 文件改变的回调接口列表
	 */
	private final List<FileChangeListener> listeners = new ArrayList<>();

	/**
	 * 监听线程是否为守护线程
	 */
	private final boolean daemon;

	/**
	 * 检查文件变化的时间间隔
	 */
	private final long pollInterval;

	/**
	 * 检查到文件变化后确保文件修改完成的时间
	 */
	private final long quietPeriod;

	private final AtomicInteger remainingScans = new AtomicInteger(-1);

	/**
	 * 监听的目录 -> 目录快照
	 */
	private final Map<File, FolderSnapshot> folders = new HashMap<>();

	/**
	 * 监听文件变化的线程
	 */
	private Thread watchThread;

	/**
	 * 触发重启的文件过滤器
	 */
	private FileFilter triggerFilter;

	private final Object monitor = new Object();

	/**
	 * Create a new {@link FileSystemWatcher} instance.
	 */
	public FileSystemWatcher() {
		this(true, DEFAULT_POLL_INTERVAL, DEFAULT_QUIET_PERIOD);
	}

	/**
	 * Create a new {@link FileSystemWatcher} instance.
	 *
	 * @param daemon       if a daemon thread used to monitor changes
	 * @param pollInterval the amount of time to wait between checking for changes
	 * @param quietPeriod  the amount of time required after a change has been detected to
	 *                     ensure that updates have completed
	 */
	public FileSystemWatcher(boolean daemon, Duration pollInterval, Duration quietPeriod) {
		Assert.notNull(pollInterval, "PollInterval must not be null");
		Assert.notNull(quietPeriod, "QuietPeriod must not be null");
		Assert.isTrue(pollInterval.toMillis() > 0, "PollInterval must be positive");
		Assert.isTrue(quietPeriod.toMillis() > 0, "QuietPeriod must be positive");
		Assert.isTrue(pollInterval.toMillis() > quietPeriod.toMillis(),
				"PollInterval must be greater than QuietPeriod");
		this.daemon = daemon;
		this.pollInterval = pollInterval.toMillis();
		this.quietPeriod = quietPeriod.toMillis();
	}

	/**
	 * 添加监听器
	 * <p>
	 * Add listener for file change events. Cannot be called after the watcher has been
	 * {@link #start() started}.
	 *
	 * @param fileChangeListener the listener to add
	 */
	public void addListener(FileChangeListener fileChangeListener) {
		Assert.notNull(fileChangeListener, "FileChangeListener must not be null");
		synchronized (this.monitor) {
			checkNotStarted();
			this.listeners.add(fileChangeListener);
		}
	}

	/**
	 * 添加监听的目录
	 * <p>
	 * Add source folders to monitor. Cannot be called after the watcher has been
	 * {@link #start() started}.
	 *
	 * @param folders the folders to monitor
	 */
	public void addSourceFolders(Iterable<File> folders) {
		Assert.notNull(folders, "Folders must not be null");
		synchronized (this.monitor) {
			for (File folder : folders) {
				addSourceFolder(folder);
			}
		}
	}

	/**
	 * 添加监听的目录
	 * <p>
	 * Add a source folder to monitor. Cannot be called after the watcher has been
	 * {@link #start() started}.
	 *
	 * @param folder the folder to monitor
	 */
	public void addSourceFolder(File folder) {
		Assert.notNull(folder, "Folder must not be null");
		Assert.isTrue(!folder.isFile(), "Folder '" + folder + "' must not be a file");
		synchronized (this.monitor) {
			checkNotStarted();
			this.folders.put(folder, null);
		}
	}

	/**
	 * 设置触发重启的文件过滤器
	 * <p>
	 * Set an optional {@link FileFilter} used to limit the files that trigger a change.
	 *
	 * @param triggerFilter a trigger filter or null
	 */
	public void setTriggerFilter(FileFilter triggerFilter) {
		synchronized (this.monitor) {
			this.triggerFilter = triggerFilter;
		}
	}

	/**
	 * 确保监听线程还没有启动
	 */
	private void checkNotStarted() {
		synchronized (this.monitor) {
			Assert.state(this.watchThread == null, "FileSystemWatcher already started");
		}
	}

	/**
	 * Start monitoring the source folder for changes.
	 */
	public void start() {
		synchronized (this.monitor) {
			saveInitialSnapshots();
			if (this.watchThread == null) {
				Map<File, FolderSnapshot> localFolders = new HashMap<>(this.folders);
				this.watchThread = new Thread(new Watcher(this.remainingScans, new ArrayList<>(this.listeners),
						this.triggerFilter, this.pollInterval, this.quietPeriod, localFolders));
				this.watchThread.setName("File Watcher");
				this.watchThread.setDaemon(this.daemon);
				this.watchThread.start();
			}
		}
	}

	/**
	 * 初始化 FolderSnapshot
	 */
	private void saveInitialSnapshots() {
		this.folders.replaceAll((f, v) -> new FolderSnapshot(f));
	}

	/**
	 * 停止监听
	 *
	 * Stop monitoring the source folders.
	 */
	public void stop() {
		stopAfter(0);
	}

	/**
	 * 停止监听
	 *
	 * Stop monitoring the source folders.
	 *
	 * @param remainingScans the number of remaining scans
	 */
	void stopAfter(int remainingScans) {
		Thread thread;
		synchronized (this.monitor) {
			thread = this.watchThread;
			if (thread != null) {
				this.remainingScans.set(remainingScans);
				if (remainingScans <= 0) {
					thread.interrupt();
				}
			}
			this.watchThread = null;
		}
		if (thread != null && Thread.currentThread() != thread) {
			try {
				thread.join();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static final class Watcher implements Runnable {

		private final AtomicInteger remainingScans;

		/**
		 * 文件改变监听器
		 */
		private final List<FileChangeListener> listeners;

		/**
		 * 触发重启的文件过滤器
		 */
		private final FileFilter triggerFilter;

		/**
		 * 监测文件变化的时间间隔
		 */
		private final long pollInterval;

		/**
		 * 监测到文件变化后确保文件修改已完成的等待时间
		 */
		private final long quietPeriod;

		/**
		 * 监测文件变化的目录
		 */
		private Map<File, FolderSnapshot> folders;

		private Watcher(AtomicInteger remainingScans, List<FileChangeListener> listeners, FileFilter triggerFilter,
						long pollInterval, long quietPeriod, Map<File, FolderSnapshot> folders) {
			this.remainingScans = remainingScans;
			this.listeners = listeners;
			this.triggerFilter = triggerFilter;
			this.pollInterval = pollInterval;
			this.quietPeriod = quietPeriod;
			this.folders = folders;
		}

		@Override
		public void run() {
			int remainingScans = this.remainingScans.get();
			while (remainingScans > 0 || remainingScans == -1) {
				try {
					if (remainingScans > 0) {
						this.remainingScans.decrementAndGet();
					}
					scan();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				remainingScans = this.remainingScans.get();
			}
		}

		private void scan() throws InterruptedException {
			Thread.sleep(this.pollInterval - this.quietPeriod);
			Map<File, FolderSnapshot> previous;
			Map<File, FolderSnapshot> current = this.folders;
			do {
				previous = current;
				current = getCurrentSnapshots();
				// 休眠给定时间保证修改已完成再判断是否变化
				Thread.sleep(this.quietPeriod);
			}
			while (isDifferent(previous, current));
			if (isDifferent(this.folders, current)) {
				updateSnapshots(current.values());
			}
		}

		/**
		 * 目录内的文件是否发生变化
		 *
		 * @param previous
		 * @param current
		 * @return
		 */
		private boolean isDifferent(Map<File, FolderSnapshot> previous, Map<File, FolderSnapshot> current) {
			if (!previous.keySet().equals(current.keySet())) {
				return true;
			}
			for (Map.Entry<File, FolderSnapshot> entry : previous.entrySet()) {
				FolderSnapshot previousFolder = entry.getValue();
				FolderSnapshot currentFolder = current.get(entry.getKey());
				if (!previousFolder.equals(currentFolder, this.triggerFilter)) {
					return true;
				}
			}
			return false;
		}

		/**
		 * 获取最新目录快照
		 *
		 * @return
		 */
		private Map<File, FolderSnapshot> getCurrentSnapshots() {
			Map<File, FolderSnapshot> snapshots = new LinkedHashMap<>();
			for (File folder : this.folders.keySet()) {
				snapshots.put(folder, new FolderSnapshot(folder));
			}
			return snapshots;
		}

		/**
		 * @param snapshots
		 */
		private void updateSnapshots(Collection<FolderSnapshot> snapshots) {
			Map<File, FolderSnapshot> updated = new LinkedHashMap<>();
			Set<ChangedFiles> changeSet = new LinkedHashSet<>();
			for (FolderSnapshot snapshot : snapshots) {
				FolderSnapshot previous = this.folders.get(snapshot.getFolder());
				updated.put(snapshot.getFolder(), snapshot);
				ChangedFiles changedFiles = previous.getChangedFiles(snapshot, this.triggerFilter);
				if (!changedFiles.getFiles().isEmpty()) {
					// 发现变化的文件
					changeSet.add(changedFiles);
				}
			}
			if (!changeSet.isEmpty()) {
				// 触发监听器
				fireListeners(Collections.unmodifiableSet(changeSet));
			}
			this.folders = updated;
		}

		private void fireListeners(Set<ChangedFiles> changeSet) {
			for (FileChangeListener listener : this.listeners) {
				listener.onChange(changeSet);
			}
		}

	}

}
