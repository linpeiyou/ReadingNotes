# 第11章 Android的线程和线程池
- AsyncTask
- IntentService
- HandlerThread


## 1. 主线程和子线程

## 2. Android中的线程形态
为了简化在子线程中访问UI的过程，系统提供了AsyncTask。
AsyncTask经过几次修改，导致了对于不同的API版本AsyncTask具有不同的表现，尤其是多任务的并发执行上。

### 2.1 AsyncTask
AsyncTask是一种轻量级的异步任务类，它可以在线程池中执行后台任务，然后把执行进度和最终结果传递给主线程并在主线程中更新UI。

AsyncTask封装了Thread和Handler，AsyncTask不适合进行特别耗时的后台任务，对于特别耗时的任务来说，应该使用线程池。

```
public abstract class AsyncTask<Params, Progress, Result>
```

AsyncTask提供的核心方法：

1、onPreExecute()，在主线程中执行

2、doInBackground(Params... params)，在线程池中执行。
在此方法中可以通过publishProgress方法来更新任务进度，publishProgress会调用onProgressUpdate方法。
此方法需要返回计算结果给onPostExecute方法。

3、onProgressUpdate(Progress... values)，在主线程中执行

4、onPostExecute(Result result)，在主线程中执行

5、onCancelled()，在主线程中执行，当异步任务被取消时，onCancelled()方法会被调用，这个时候onPostExecute(Result result)则不会被调用

demo:
```
private class DownloadFilesTask extends AsyncTask<URL, Integer, Long> {
	protected Long doInBackground(URL... urls) {
		int count = urls.length;
		long totalSize = 0;
		for(int i = 0; i < count; ++i) {
			totalSize += Downloader.downloadFile(urls[i]);
			publishProgress((int) (i / (float) count) * 100));
			// Escape early if cancel() is called
			if(isCancelled()) 
				break;
		}
	}

	protected void onProgressUpdate(Integer... progress) {
		setProgressPercent(progress[0]);
	}

	protected void onPostExecute(Long result) {
		showDialog("Downloaded " + result + " bytes");
	}
}
```
上面的代码实现了一个具体的AsyncTask类，模拟文件的下载过程。可通过如下方式来执行：
```
new DownloadFilesTask().execute(url1, url2, url3);
```

**AsyncTask在使用过程中的限制：**
1、AsyncTask类必须在主线程中加载，所以第一次访问AsyncTask必须发生在主线程。
这个过程在Android 4.1及以上版本中已经被系统自动完成。
（在Android 5.0的源码中，在ActivityThread的main方法中，调用了AsyncTask的init方法，这就满足了AsyncTask的类必须在主线程中进行加载这个条件了。）

2、AsyncTask的对象必须在主线程中创建

3、execute方法必须在主线程调用

4、不要在程序中直接调用onPreExecute()、onPostExecute()、doInBackground()和onProgressUpdate()方法

5、一个AsyncTask对象只能执行一次，即只能调用一次execute方法，否则会报运行时异常

6、在Android 1.6之前，AsyncTask是串行执行任务的。
Android 1.6的时候AsyncTask开始采用线程池处理并行任务。
但是从Android3.0开始，为了避免AsyncTask所带来的并发错误，AsyncTask又采用了一个线程来串行执行任务。
**尽管如此，在Android3.0以及后续的版本中，我们仍然可以通过AsyncTask的executeOnExecutor方法来并发地执行任务**


### 2.2 AsyncTask的工作原理
从execute方法开始分析，execute会调用executeOnExecutor方法：
```
public final AsyncTask<Params, Progress, Result> execute(Params... params) {
	return executeOnExecutor(sDefaultExecutor, params);
}

public final AsyncTask<Params, Progress, Result> executeOnExecutor(
	Executor exec, Params... params) {
	if(mStatus != Status.PENDING) {
		switch(mStatus) {
			case RUNNING:
				// 正在执行的
				throw new IllegalStateException("Cannot execute task: the task is already running.");
			case FINISHED:
				// 已执行过的，task只能执行一次
				throw new IllegalStateException("Cannot execute task: the task has already been executed (a task can be executed only once.)");
		}
	}
	mStatus = Status.RUNNING;
	onPreExecute();
	mWorker.mParams = params;
	exec.execute(mFuture);
	return this;
}
```
在上面的代码中，sDefaultExecutor是一个串行的线程池，一个进程中所有的AsyncTask全部在这个串行的线程池中排队执行。
在executeOnExecutor方法中，AsyncTask的onPreExecute方法最先执行，然后线程池开始执行。
线程池的执行过程：
```
public static final Executor SERIAL_EXECUTOR = new SerialExecutor();
private static volatile Executor sDefaultExecutor = SERIAL_EXECUTOR;

private static class SerialExecutor implements Executor {
	final ArrayDeque<Runnable> mTasks = new ArrayDeque<Runnable>();
	Runnable mActive;

	public synchronized void execute(final Runnable r) {
		mTasks.offer(new Runnable() {
			public void run() {
				try {
					r.run();
				} finnally {
					scheduleNext();
				}
			}
		})
		if(mActive == null) {
			// 这个时候没有正在活动的AsyncTask任务
			scheduleNext();
		}
	}

	protected synchronized void scheduleNext() {
		if((mActive = mTask.poll()) != null) {
			THREAD_POOL_EXECUTOR.execute(mActive);
		}
	}
}
```
从SerialExecutor的实现可以分析AsyncTask的排队执行的过程。
1. 首先系统会把AsyncTask的Params参数封装为FutureTask对象，FutureTask是一个并发类，在这里它充当了Runnable的作用。
2. 接着这个FutureTask会交给SerialExecutor的execute方法去处理。
SerialExecutor的execute方法首先会把FutureTask对象插入到任务队列mTasks中，如果这个时候没有正在活动的AsyncTask任务，那么就会调用SerialExecutor的scheduleNext方法来执行下一个AsyncTask任务。
同时当一个AsyncTask任务执行完成后，AsyncTask会继续执行其他任务直到所有的任务都被执行为止，可以看出，默认情况下，AsyncTask是串行执行的。

AsyncTask中有两个线程池（SerialExecutor和THREAD_POOL_EXECUTOR）和一个Handler（InteralHandler），其中线程池SerialExecutor用于任务的排队，而线程池THREAD_POOL_EXECUTOR用于真正地执行任务。InternalHandler用于将执行环境从线程池切换到主线程。

AsyncTask的构造方法中有如下这么一段代码，由于FutureTask的run方法会调用mWorker的call方法，因此mWorker的call方法最终会在线程池中执行。
```
public AsyncTask() {
	...

	mWorker = new WorkerRunnable<Params, Result>() {
		public Result call() throws Exception {
			// 表示当前任务已经被调用过了
			mTaskInvoked.set(true);

			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
			// noinspection unchecked
			return postResult(doInBackground(mParams));
		}
	};

	...
}
```

在mWorkerd的call方法中，首先将mTaskInvoked设为true，表示当前任务已经被调用过了。
然后执行AsyncTask的doInBackground方法，接着将其返回值传递给postResult方法，它的实现如下：
```
private Result postResult(Result result) {
	@SuppressWarnings("unchecked")
	Message message = sHandler.obtainMessage(MESSAGE_POST_RESULT, new AsyncTaskResult<Result>(this, result));
	message.sendToTarget();
	return result;
}
```

postResult方法会通过sHandler发送一个MESSAGE_POST_RESULT的消息，sHandler定义如下：
```
private static final InternalHandler sHandler = new InternalHandler();

private static class InternalHandler extends Handler {
	@SuppressWarnings({"unchecked", "RawUseOfParameterizedType"})
	@Override
	public void handleMessage(Message message) {
		AsyncTaskResult result = (AsyncTaskResult) msg.obj;
		switch(msg.what) {
			case MESSAGE_POST_RESULT:
				// There is only one result
				result.mTask.finish(result.mData[0]);
				break;
			case MESSAGE_POST_PROGRESS:
				result.mTask.onProgressUpdate(result.mData);
				break;
		}
	}
}
```

sHandler是一个静态的Handler对象，为了能够将执行环境切换到主线程，这就要求sHandler这个对象必须在主线程创建。
由于静态成员会在加载类的时候进行初始化，因此这就变相要求AsyncTask的类必须在主线程加载。
否则同一个进程中的AsyncTask都将无法工作。

sHandler收到MESSAGE_POST_RESULT这个消息后会调用AsyncTask的finish方法：
```
private void finish(Result result) {
	if(isCancelled()) {
		onCancelled(result);
	} else {
		onPostExecute(result);
	}
	mStatus = Status.FINISHED;
}
```


在Android 3.0及以上的版本中，如果要让AsyncTask并行，可以采用AsyncTask的executeOnExecutor方法。
不过要注意这个放在是Android 3.0新添加的方法，不能在低版本中使用。
demo:
```
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public void executeTasks() {
	if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
		new MyAsyncTask("AsyncTask#1").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
		new MyAsyncTask("AsyncTask#2").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
		new MyAsyncTask("AsyncTask#3").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
		new MyAsyncTask("AsyncTask#4").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
		new MyAsyncTask("AsyncTask#5").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
	}
}

private static class MyAsyncTask extends AsyncTask<String, Integer, String> {
	private String mName = "AsyncTask";

	public MyAsyncTask(String name) {
		super();
		mName = name;
	}

	@Override
	protected String doInBackground(String... params) {
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return mName;
	}

	@Override
	protected void onPostExecute(String result) {
		super.onPostExecute(result);
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Log.e(TAG, result + " execute finish at " + df.format(new Date()));
	}
}
```

### 2.3 HandlerThread
HanlderThread继承了Thread，它是一种可以使用Handler的Thread。
它的实现也很简单，就是在run方法中通过Looper.prepare()来创建消息队列，并通过Looper.loop()来开启消息循环。
这样在实际的使用中就允许在HandlerThread中创建Handler了。
HandlerThread的run方法如下所示：
```
public void run() {
	mTid = Process.myTid();
	Looper.prepare();
	synchronized(this) {
		mLooper = Looper.myLooper();
		notifyAll();
	}
	Process.setThreadPriority(mPriority);
	onLooperPrepared();
	Looper.loop();
	mTid = -1;
}
```

从HandlerThread的实现来看，它和普通的Thread有显著的不同之处：
普通Thread主要用于在run方法中执行一个耗时任务
而HandlerThread在内部创建了消息队列，外界需要通过Handler的消息方式来通知HandlerThread执行一个具体的任务。

HandlerThread是一个很有用的类，它在Android中的一个具体的使用场景是IntentService。

由于HandlerThread的run方法是一个无限循环，因此明确不需要再使用HandlerThread时，
可以通过它的quit或者quitSafely方法来终止线程的执行，这是一个良好的编程习惯。


### 2.4 IntentService
未看

## 3. Android中的线程池
未看

### 3.1 ThreadPoolExecutor
未看

### 3.2 线程池的分类

1、FixedThreadPool
线程数量固定
当线程处于空闲状态时，不会被回收，除非线程池被关闭了
当所有的线程都处于活动状态时，新任务都会处于等待状态，直到有线程空闲出来


```
public static ExecutorService newFixedThreadPool(int nThreads) {
	return new ThreadPoolExecutor(nThreads, nThreads,
								0L, TimeUnit.MILLISECONDS,
								new LinkedBlockingQueue<Runnable>());
}
```

2、CachedThreadPool

```
public static ExecutorService newCachedThreadPool() {
	return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
								60L, TimeUnit.SECONDS,
								new SynchronousQueue<Runnable>());
}
```





































