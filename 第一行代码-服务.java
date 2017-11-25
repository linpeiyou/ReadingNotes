# 服务

## 1. 服务是什么
服务（Service）是Android中实现程序后台运行的解决方案，它非常适合去执行那些不需要和用户交互并且还要求长期运行的任务。
服务的运行不依赖于任何用户界面，即使程序被切换到后台，或者用户打开了另外一个应用程序，服务仍然能够保持正常运行。

服务不是运行在一个独立的进程当中的，而是依赖于创建服务时所在的应用程序进程。
当某个应用程序进程被杀掉时，所有依赖于该进程的服务也会停止运行。

服务并不会自动开启线程，所有的代码都是默认运行在主线程的。我们需要在服务的内部手动创建子线程，并在这里执行具体的任务，否则就有可能出现主线程被阻塞住的情况。



## 2. Android多线程编程

### 2.1 线程的基本用法
和Java一样，继承Thread或者实现Runnable

### 2.2 在子线程中更新UI
UI操作必须在主线程中执行
Android异步消息处理机制Handler

### 2.3 解析异步消息处理机制
Message、Handler、MessageQueue和Looper

整个流程：
1.首先需要在主线程中创建一个Handler对象，并重写handleMessage()方法。
2.然后当子线程中需要进行UI操作时，就创建一个Message对象，并通过Handler将这条消息发送出去。
3.之后这条消息会被添加到MessageQueue的队列中等待被处理。
4.而Looper则会一直尝试从MessageQueue中取出待处理消息，最后分发回Handler的handleMessage()方法中。

由于Handler是在主线程中创建的，所以此时handleMessage()方法中的代码也会在主线程中运行，于是就可以在这里执行UI操作了。

### 2.4 使用AsyncTask
AsyncTask背后的实现原理也是基于异步消息处理机制的，只是Android帮我们做了很好的封装而已。

```
class DownloadTask extends AsyncTask<T1, T2, T3> {
	...
}
```
T1: Params。在执行AsyncTask时需要传入的参数，可用于在后台任务中使用。
T2: Progress。后台任务执行时，如果需要在界面上显示当前的进度，则使用这里指定的泛型作为进度单位。
T3: Result。当任务执行完毕后，如果需要对结果进行返回，则使用这里指定的泛型作为返回值类型。

经常要去重写的方法：
1. OnPreExecute()
这个方法会在后台任务开始执行之前调用，用于进行一些界面上的初始化，比如显示一个进度条对话框等。

2. doInBackground(Params...)
这个方法的所有代码都会在子线程中运行，我们应该在这里去处理所有的耗时任务。
如果需要更新UI操作，比如更新进度，可以调用publishProgress(Progress...)方法。
任务一旦完成就可以通过return语句来将结果返回。

3. onProgressUpdate(Progress...)
当在后台任务中调用了publishProgress(Progress...)方法后，onProgressUpdate(Progress...)方法就会很快被调用，该方法中携带的参数就是在后台任务中传递过来的。
在这个方法中可以对UI进行操作。

4. onPostExecute(Result)
当后台任务执行完毕并通过return语句进行返回时，这个方法就很快会被调用。返回的数据会作为参数传递到此方法中。



## 3. 服务的基本用法

### 3.1 定义一个服务
exported属性表示是否允许除了当前应用程序之外的其他程序访问这个服务
enabled属性表示是否启用这个服务

```
public class MyService extends Service {
	public MyService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
}
```

onCreate()方法会在服务创建的时候调用
onStartCommand()方法会在每次服务启动的时候调用
onDestroy()方法会在服务销毁的时候调用

通常情况下，如果我们希望服务一旦启动就立刻去执行某个动作，就可以将逻辑写在onStartCommand()方法里。
而当服务销毁时，我们又应该在onDestroy()方法中去回收那些不再使用的资源。

Android的四大组件都必须在AndroidManifest.xml中进行注册才能生效。


### 3.2 启动和停止服务
```
// 启动服务
Intent startIntent = new Intent(this, MyService.class);
startService(startIntent);
// 停止服务
Intent stopIntent = new Intent(this, MyService.class);
stopService(stopIntent);
```


### 3.3 活动和服务进行通信
通过Binder对象来解决
```
public class MyService extends Service {

	private DownloadBinder mBinder = new DownloadBinder();

	class DownloadBinder extends Binder {

		public void startDownload() {
			Log.d(“MyService”, “startDownload executed”);
		}

		public int getProgress() {
			Log.d(“MyService”, “getProgress executed”);
			return 0;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

}
```

```
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
	
	private MyService.DownloadBinder downloadBinder;

	private ServiceConnection connection = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {		
		}
	
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			downloadBinder = (MyService.DownloadBinder) service;
			downloadBinder.startDownload();
			downloadBinder.getProgress();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		...
		((Button) findViewById(R.id.bind_service)).setOnClickListener(this);
		((Button) findViewById(R.id.unbind_service)).setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()) {
			...
			case R.id.bind_service:
				Intent bindIntent = new Intent(this, MyService.class);
				bindService(bindIntent, connection, BIND_AUTO_CREATE);
				break;
			case R.id.unbind_service:
				unbindService(connection);
				break;
			default:
				break;
		}
	}
}
```

onServiceConnected()方法和onServiceDisconnected()方法分别会在活动与服务成功绑定以及解除绑定的时候调用。在onServiceConnected()方法中，我们通过向下转型得到DownloadBinder的实例，有了这个实例，活动和服务之间的关系就变得非常紧密了。

bindService()方法接收3个参数，这里第三个参数 BIND_AUTO_CREATE 表示在活动和服务进行绑定后自动创建服务。
这会使得MyService中的onCreate()方法得到执行，但onStartCommand()方法不会执行。

任何一个服务在整个应用程序范围内都是通用的，即MyService不仅可以和MainActivity绑定，还可以和任何一个其他的活动进行绑定，而且在绑定完成后它们都可以获取到相同的DownloadBinder实例。


## 4. 服务的生命周期
每个服务只会存在一个实例，所以不管调用了多少次startService()方法，只要调用一次stopService()或stopSelf()方法，服务就会停止下来了。

1.调用了startService()后，又去调用stopService()，服务中的onDestroy()方法就会执行，表示服务已经销毁了。
2.调用了bindService()后，又去调用unbindService()，onDestroy()方法也会执行。
3.如果对一个服务既调用了startService()方法，又调用了bindService()方法。
根据Android系统的机制，一个服务只要被启动或者被绑定了之后，就会一直处于运行状态，必须要让以上两种条件同时不满足，服务才能被销毁。
所以，这种情况下要同时调用stopService()和unbindService()方法，onDestroy()方法才会执行。


## 5. 服务的更多技巧

### 5.1 使用前台服务
当系统出现内存不足的情况时，有可能会回收掉正在后台运行的服务。
如果你希望服务可以一直保持运行状态，不会因为系统内存不足的原因导致被回收，就可以考虑使用前台服务。

前台服务和普通服务的最大区别在于：前台服务会一直有一个正在运行的图标在系统的状态栏显示，下拉状态栏后可以看到更加详细的信息，非常类似于通知的效果。

```
public class MyService extends Service {
	...
	@Override
	public void onCreate() {
		super.onCreate();
		Log.d("MyService", "onCreate executed");
		Intent intent = new Intent(this, MainActivity.class);
		PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
		Notification notification = new NotificationCompat.Builder(this)
				.setContentTitle("This is content title")
				.setContentText("This is content text")
				.setWhen(System.currentTimeMills())
				.setSmallIcon(R.mipmap.ic_launcher)
				.setLargeIcon(BitmapFactory.decodeResource(getResource(), R.mipmap.ic_launcher))
				.setContentIntent(pi)
				.build();
		startForeground(1, notification);
	}
}
```

这段代码和创建通知的代码十分相似，只不过在构建出Notification对象后并没有使用NotificationManager来将通知显示出来，而是调用了startForeground()方法。
startForeground()方法接收两个参数
第一个参数是通知的id，类似于notify()方法的第一个参数；
第二个参数是构建出的Notification对象。调用startForeground()方法后就会让MyService变成一个前台服务，并在系统状态栏显示出来。


### 5.2 使用IntentService
如果想实现让一个服务在执行完毕后自动停止的功能，可以这样写：
```
public class MyService extends Service {
	...
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				// 处理具体的逻辑
				...
				stopSelf();
			}
		}).start();
		return super.onStartCommand(intent, flags, startId);
	}
}
```

上面的写法虽然不复杂，但是总会有忘记开启线程，或者忘记调用stopSelf()方法的情况。
为了可以简单地创建一个异步、会自动停止的服务，Android专门提供了一个IntentService类，这个类就很好地解决了前面所提到的两种尴尬。

```
public class MyIntentService extends IntentService {
	
	public MyIntentService() {
		// 调用父类的有参构造函数
		super("MyIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// 打印当前的线程
		Log.d("MyIntentService", "Thread id is " + Thread.currentThread().getId());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d("MyIntentService", "onDestroy executed");
	}
}
```

1.首先要提供一个无参的构造函数，并且必须在其内部调用父类的有参构造函数。
2.然后要在子类中取实现onHandleIntent()这个抽象方法，这个方法在子线程中运行。


## 6. 服务的最佳实践——完整版的下载实例
未看


## 7. 小结与点评
- Android多线程编程
- 服务的基本用法
- 服务的生命周期
- 前台服务
- IntentService




