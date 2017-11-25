# 四大组件的工作过程

四大组件：Activity、Service、BroadcastReceiver、ContentProvider

## 1. 四大组件的运行状态

除了BroadcastReceiver以外，都必须在AndroidManifest中注册
对于BroadcastReceiver，可以在AndroidManifest中注册也可以通过代码来注册

在调用方式上，Activity、Service和BroadcastReceiver需要借助Intent
ContentProvider无须借助Intent

### Activity
- Activity的启动由Intent触发，Intent可以分为**显式Intent**和**隐式Intent**
- 显示Intent可以明确地指向一个Activity组件
- 隐式Intent则指向一个或者多个目标Activity组件（也可能没有任何一个Activity组件可以处理这个隐式Intent）
- 一个Activity组件可以具有特定的启动模式
- 可通过Activity的finish方法来结束一个Activity组件的运行

### Service
- Service是一种计算型组件，用于在后台执行一系列计算任务
- 由于Service组件工作在后台，因此用户无法直接感知它的存在
- Service组件有两种状态：启动状态、绑定状态
- 当Service组件处于启动状态时，Service内部可以做一些后台计算，并且不需要和外界有直接的交互
尽管Service组件是用于执行后台计算的，但是它本身是运行在主线程中的，因此耗时的后台计算仍然要在单独的线程中去完成
- 当Service组件处于绑定状态时，Service内部同样可以进行后台计算，
处于这种状态时，外界可以很方便地和Service组件进行通信
- Service组件也是可以停止的，具体怎么停止看情况（stopService, unBindService）

### BroadcastReceiver
- BroadcastReceiver是一种消息型组件，用于在不同的组件乃至不同的应用之间传递消息
- BroadcastReceiver无法被用户直接感知，因为它工作在系统内部
- BroadcastReceiver有两种注册方式：静态注册、动态注册
- 静态注册：在AndroidManifest中注册广播
这种广播会在应用安装时被系统解析，这种形式的广播不需要启动应用就可以接收到相应的广播
- 动态注册：通过Context.registerReceiver()来实现，在不需要的时候通过Context.unRegisterReceiver()来解除广播
必须要应用启动才能注册并接收广播

在实际开发中，通过Context的一系列send方法来发送广播，被发送的广播会被系统发送给感兴趣的广播接收者
发送和接收过程的匹配是通过广播接收者的<intent-filter>来描述的

可以发现，BroadcastReceiver组件可以用来实现低耦合的观察者模式，观察者和被观察者之间可以没有任何耦合
由于BroadcastReceiver的特性，它不适合用来执行耗时操作
BroadcastReceiver组件一般来说不需要停止，它也没有停止的概念

### ContentProvider
- ContentProvider是一种数据共享型组件，用于向其他组件乃至其他应用共享数据
- ContentProvider无法被用户直接感知
- 对于一个ContentProvider组件来说，它的内部需要实现增删改查这四种操作
- 它的内部维持着一份数据集合，这个数据集合可以通过数据库来实现，也可以采用其他任何类型来实现，如List和Map
ContentProvider对数据结合的具体实现并没有任何要求
- 要注意的是，ContentProvider内部的insert, delete, update和query方法需要处理好线程同步
因为这几个方法是在Binder线程池被调用的
- ContentProvider组件不需要手动停止


## 2. Activity的工作过程

从问题出发：
- 系统内部是如何启动一个Activity的呢？
- 新的Activity对象是在何时创建的？
- Activity的onCreate方法又是在何时被系统回调的呢？

从Activity的startActivity方法开始分析
startActivity方法有好几种重载方式，最后都会调用startActivityForResult方法：
```
public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
	if(mParent == null) {
		Instrumentation.execStartActivity(this, mMainThread.getApplicationThread(), mToken, this, 
				intent, requestCode, options);
		if(ar != null) {
			mMainThread.sendActivityResult(mToken, mEmbeddedID, requestCode, ar.getResultCode(), 
					ar.getResultData());
		}
		if(requestCode >= 0) {
			// If this start is requesting a result, we can avoid makeing the activity visible
			// until the result is received. Setting this code during 
			// onCreate(Bundle savedInstanceState) or onResume() will keep the activity
			// hidden during this time, to avoid flikcering. 
			// This can only be done when a result is requested because that guarantees
			// we will get information back when the activity is finished,
			// no matter what happens to it.
			mStartedActivity = true;
		}

		final View decor = mWindow != null ? mWindow.peekDecorView() : null;
		if(decor != null) {
			decor.cancelPendingInputEvents();
		}
		// TODO Consider clearing/flushing other event sources and events for child windows.
	} else {
		if(options != null) {
			mParent.startActivityFromChild(this, intent, requestCode, options);
		} else {
			// Note we want to go through this method for compatibility with existing
			// applications that may have overriden it.
			mParent.startActivityFromChild(this, intent, requestCode);
		}
	}

	if(options != null && !isTopOfTask()) {
		mActivityTransitionState.startExitOutTransition(this, options);
	}
}
```

我们只需要关注mParent==null这部分的逻辑即可
mParent代表的是ActivityGroup，ActivityGroup最开始被用来在一个界面中嵌入多个子Activity
ActivityGroup在API 13中已经被废弃了，系统推荐使用Fragment来代替ActivityGroup

mMainThread.getApplicationThread()这个参数的类型的ApplicationThread
ApplicationThread是ActivityThread的一个内部类
ApplicationThread和ActivityThread在Activity的启动过程中发挥着很重要的作用


```
// Instrumentation.java
public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token,
		Activity target, Intent intent, int requestCode, Bundle options) {
	IApplicationThread whoThread = (IApplicationThread) contextThread;
	if(mActivityMonitors != null) {
		synchronized(mSync) {
			final int N = mActivityMonitors.size();
			for(int i = 0; i < N; ++i) {
				final ActivityMonitor am = mActivityMonitors.get(i);
				if(am.match(who, null, intent)) {
					am.mHits++;
					if(am.isBlocking()) {
						return requestCode >= 0 ? am.getResult() : null;
					}
					break;
				}
			}	
		}
	}
	try {
		intent.migrateExtraStreamToClipData();
		intent.prepareToLeaveProcess();
		int result = ActivityManagerNative.getDefault()
				.startActivity(whoThread, who.getBasePackagename(), intent,
					intent.resolveTypeIfNeeded(who.getContentResolver()), 
					token, target != null ? target.mEmbeddedID : null, 
					requestCode, 0, null, options);
		checkStartActivityResult(result, intent);
	} catch (RemoteException e) {
	}
	return null;
}
```

启动Activity真正的实现由ActivityManagerNative.getDefault()的startActivity方法来完成
ActivityManagerService（AMS）继承自ActivityManagerNative
而ActivityManagerNative继承自Binder并实现了IActivityManager这个Binder接口
因此AMS也是一个Binder，它是IActivityManager的具体实现

ActivityManagerNative.getDefault()其实是一个IActivityManager类型的Binder对象，它的具体实现是AMS
在ActivityManagerNative中，AMS这个Binder对象采用单例模式对外提供：
```
// ActivityManagerNative.java
static public IActivityManager getDefault() {
	return gDefault.get();
}

private static final Singleton<IActivityManager> gDefault = new Singleton<IActivityManager>() {
	protected IActivityManager create() {
		IBinder b = ServiceManager.getService("activity");
		if(false) {
			Log.v("ActivityManager", "default service binder = "+ b);
		}
		IActivityManager am = asInterface(b);
		if(false) {
			Log.v("ActivityManager", "default service = " + am);
		}
		return am;
	}
}
```

由上面的分析可以知道：Activity由AMS来启动
因此接下来要分析AMS的startActivity方法

在分析AMS的startActivity之前，我们先看一下Instrumentation的execStartActivity方法
其中的一行代码：checkStartActivityResult(result, intent)
checkStartActivityResult的作用很明显，就是检查启动Activity的结果，源码如下：
```
// Instrumentation.java
/** @hide */
public static void checkStartActivityResult(int res, Object intent) {
	if(res >= ActivityManager.START_SUCCESS) {
		return;
	}

	switch(res) {
		case ActivityManager.START_INTENT_NOT_RESOLVED:
		case ActivityManager.START_CLASS_NOT_FOUND:
			if(intent instanceof Intent && ((Intent) intent).getComponent() != null) 
				throw new ActivityNotFoundException("Unable to find explict activity class "
					+ ((Intent) intent).getComponent().toShortString()
					+ "; have you declared this activity in your AndroidManifest.xml?");
			throw new ActivityNotFoundException("No Activity found to handle " + intent);

		case ActivityManager.START_PERMISSION_DENIED:
			throw new SecurityException("Not allowed to start activity " + intent);

		case ActivityManager.START_FORWARD_AND_REQUEST_CONFILT:
			throw new AndroidRuntimeException("FORWARD_RESULT_FLAG used while also requesting a result");

		case ActivityManager.START_NOT_ACTIVITY:
			throw new IllegealArgumentException("PendingIntent is not an activity");

		case ActivityManager.START_NOT_VOICE_COMPATIBLE:
			throw new SecurityException("Starting under voice control not allowed for: " + intent);

		default:
			throw new AndroidRuntimeException("Unknown error code " + res + " when Starting " + intent);
	}
}
```

继续分析AMS的startActivity方法：
```
// ActivityManagerService.java
public final int startActivity(IApplicationThread caller, String callingPackage, Intent intent,
		String resolvedType, IBinder resultTo, String resultWho, int requestCode,
		int startFlags, ProfilerInfo profilerInfo, Bundle options) {
	
	return startActivityAsUser(caller, callingPackage, intent, 
			resolvedType, resultTo, resultWho, requestCode,
			startFlags, profilerInfo, options, UserHandle.getCallingUserId());
}

public final int startActivityAsUser(IApplicationThread caller, String callingPackage, Intent intent,
		String resolvedType, IBinder resultTo, String resultWho, int requestCode,
		int startFlags, ProfilerInfo profilerInfo, Bundle options, int userId) {

	enforceNotIsolatedCaller("startActivity");
	userId = handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(), userId,
			false, ALLOW_FULL_ONLY, "startActivity", null);
	// TODO: Switch to user app stacks here.
	return mStackSupervisor.startActivityMayWait(caller, -1, callingPackage, intent,
			resolvedType, null, null, resultTo, resultWho, requestCode, 
			startFlags, profilerInfo, null, null, options, userId, null, null);
}
```

可以看到Activity的启动过程又转移到了ActivityStackSupervisor的startActivityMayWait方法中了
-> 在startActivityMayWait中调用了startActivityLocked方法
-> 然后startActivityLocked又调用了startActivityUncheckedLocked方法
-> 接着startActivityUncheckedLocked又调用了ActivityStack的resumeTopActivitiesLocked方法
这个时候启动过程从ActivityStackSupervisor转移到了ActivityStack：
```
// ActivityStack.java
final boolean resumeTopActivitiesLocked(ActivityRecord prev, Bundle options) {
	if(inResumeTopActivity) {
		// Don't event start recursing.
		return false;
	}

	boolean result = false;
	try {
		// Protect against recursion.
		inResumeTopActivity = true;
		result = resumeTopActivityInnerLocked(prev, options);
	} finally {
		inResumeTopActivity = false;
	}
	return result;
}
```

-> resumeTopActivitiesLocked调用了resumeTopActivityInnerLocked
-> resumeTopActivityInnerLocked又调用了ActivityStackSupervisor的startSpecificActivityLocked方法
```
// ActivityStackSupervisor.java
void startSpecificActivityLocked(ActivityRecord r, boolean andResume, boolean checkConfig) {
	// Is this activity's application already running?
	ProcessRecord app = mService.getProcessRecordLocked(r.processName,
			r.info.applicationInfo.uid, true);

	r.task.stack.setLaunchTime(r);

	if(app != null && app.thread != null) {
		try {
			if((r.info.flags & ActivityInfo.FLAG_MULTIPROCESS) == 0
					|| !"android".equals(r.info.packageName)) {
				// Don't add this if it is a platform component that is marked
				// to run in multiple processes, because this is actually part of
				// the framework so doesn't make sense to track as a separate apk in the process.
				app.addPackage(r.info.packageName, r.info.applicationInfo.versionCode,
						mService.mProcessStats);
			}
			realStartActivityLocked(r, app, andResume, checkConfig);
			return;
		} catch(RemoteException e) {
			Slog.w(TAG, "Exception when starting activity " +
					r.intent.getComponent().flattenToShortString(), e);
		}

		// If a dead object exception was thrown -- fall through to restart the application.
	}

	mService.startProcessLocked(r.processName, r.info.applicationInfo, true, 0,
			"activity", r.intent.getComponent(), false, false, true);
}
```

从上面代码看出，startSpecificActivityLocked方法调用了realStartActivityLocked方法

Activity的启动过程在ActivityStackSupervisor和ActivityStack之间的传递顺序如下：
ActivityStackSupervisor               ActivityStack
        
startActivityMayWait
        ↓
startActivityLocked
        ↓
startActivityUncheckedLocked  ->     resumeTopActivitiesLocked
                                                 ↓
startSpecificActivityLocked   <-     resumeTopActivityInnerLocked
        ↓
realStartActivityLocked



在ActivityStackSupervisor的realStartActivityLocked方法中有如下一段代码：
```
// ActivityStackSupervisor.java
app.thread.scheduleLaunchActivity(new Intent(r.intent), r.appToken, System.identityHashCode(r), 
		r.info, new Configuration(mService.mConfiguration), r.compat,
		r.task.voiceInteractor, app.repProcState, r.icicle, r.persistentState,
		results, newIntents, !andResume, mService.isNextTransitionForward(), profilerInfo);
```

其中app.thread的类型为IApplicationThread，IApplicationThread的声明如下：
```
public interface IApplicationThread extends IInterface {
	void schedulePauseActivity(IBinder token, boolean finished, boolean userLeaving,
			int configChanges, boolean dontReport) throws RemoteException;

	void scheduleStopActivity(IBinder token, boolean showWindow, int configChanges)
			throws RemoteException;

	void scheduleWindowVisibility(IBinder token, boolean showWindow) throws RemoteException;

	void scheduleSleeping(IBinder token, boolean sleeping) throws RemoteException;

	void scheduleResumeActivity(IBinder token, int procState, boolean isForward, Bundle resumeArgs)
			throws RemoteException;

	void scheduleSendResult(...) throws RemoteException;

	void scheduleLaunchActivity(...) throws RemoteException;

	void scheduleRelaunchActivity(...) throws RemoteException;

	void scheduleNewIntent(...) throws RemoteException;

	void scheduleDestroyActivity(...) throws RemoteException;

	void scheduleReceiver(...) throws RemoteException;

	static final int BACKUP_MODE_INCREMENTAL = 0;
	static final int BACKUP_MODE_FULL = 1;
	static final int BACKUP_MODE_RESTORE = 2;
	static final int BACKUP_MODE_RESTORE_FULL = 3;

	void scheduleCreateBackupAgent(ApplicationInfo app, CompatibilityInfo compatInfo, int backupMode)
			throws RemoteException;

	void scheduleDestroyBackupAgent(ApplicationInfo app, CompatibilityInfo compatInfo)
			throws RemoteException;

	void scheduleCreateService(IBinder token, ServiceInfo info, CompatibilityInfo compatInfo,
			int processState) throws RemoteException;

	void scheduleBindService(IBinder token, Intent intent, boolean rebind, int processState)
			throws RemoteException;

	void scheduleUnbindService(IBinder token, Intent intent) throws RemoteException;

	void scheduleServiceArgs(IBinder token, boolean taskRemoved, int startId, int flags, Intent args)
			throws RemoteException;

	void scheduleStopService(IBinder token) throws RemoteException;

	...
}
```
因为IApplicationThread继承了IInterface接口，所以它是一个Binder类型的接口
从上面的代码可以看出，IApplicationThread中包含了大量的启动、停止Activity的接口；还有启动和停止服务的接口
IApplicationThread这个Binder接口的实现者完成了大量和Activity、Service的启动/停止相关的功能

IApplicationThread的实现者：ActivityThread中的内部类ApplicationThread：
```
// ActivityThread.java
private class ApplicationThread extends ApplicatonThreadNative {
	...
}

// ApplicationThreadNative.java
public abstract class ApplicationThreadNative extends Binder implements IApplicationThread {
	...
}
```

在ApplicationThreadNative内部，还有一个ApplicationThreadProxy类，这个类的实现如下：
```
// ApplicationThreadNative.java
class ApplicationThreadProxy implements IApplicationThread {
	private final IBinder mRemote;

	public ApplicationThreadProxy(IBinder remote) {
		mRemote = remote;
	}

	public final IBinder asBinder() {
		return mRemote;
	}

	public final void schedulePauseActivity(IBinder token, boolean finished, boolean userLeaving,
			int configChanges, boolean dontReport) throws RemoteException {
		Parcel data = Parcel.obtain();
		data.writeInterfaceToken(IApplicationThread.descriptor);
		data.writeStrongBinder(token);
		data.writeInt(finished ? 1 : 0);
		data.writeInt(userLeaving ? 1 : 0);
		data.writeInt(configChanges);
		data.writeInt(dontReport ? 1 : 0);
		mRemote.transact(SCHEDULE_PAUSE_ACTIVITY_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
		data.recycle();
	} 

	public final void scheduleStopActivity(IBinder token, boolean showWindow, int configChanges)
			throws RemoteException {
		Parcel data = Parcel.obtain();
		data.writeInterfaceToken(IApplicationThread.descriptor);
		data.writeStrongBinder(token);
		data.writeInt(showWindow ? 1 : 0);
		data.writeInt(configChanges);
		mRemote.transact(SCHEDULE_STOP_ACTIVITY_TRANSACTION, data, null, IBinder.FLAG_ONEWAY);
		data.recycle();
	}

	...
}
```

Activity的启动过程最终由ApplicationThread通过scheduleLaunchActivity方法来启动
```
// ActivityThread.java

// we use token to identify this activity without having to send the 
// activity itself back to the activity manager. (matters more with ipc)
public final void scheduleLaunchActivity(Intent intent, IBinder token, int ident
		ActivityInfo info, Configuration curConfig, CompatibilityInfo compatInfo,
		IVoiceInteractor voiceInteractor, int procState, Bundle state,
		PersistableBundle persistentState, List<ResultInfo> pendingResults,
		List<Intent> pendingNewIntents, boolean notResumed, boolean isForward,
		ProfilerInfo profilerInfo) {

	updateProcessState(procState, false);

	ActivityClientRecord r = new ActivityClientRecord();

	r.token = token;
	r.ident = ident;
	r.intent = intent;
	r.voiceInteractor = voiceInteractor;
	r.activityInfo = info;
	r.compatInfo = compatInfo;
	r.state = state;
	r.persistentState = persistentState;

	r.pendingResults = pendingResults;
	r.PendingIntents = pendingNewIntents;

	r.startsNotResumed = notResumed;
	r.isForward = isForward;

	r.profilerInfo = profilerInfo;

	updatePendingConfiguration(curConfig);

	sendMessage(H.LAUNCH_ACTIVITY, r);
}
```

ApplicationThread中的scheduleLaunchActivity实现很简单，就是发送一个启动Activity的消息交给Handler处理
这个Handler的名字是：H
实现如下：
```
private void sendMessage(int what, Object obj, int arg1, int arg2, boolean async) {
	if(DEBUG_MESSAGES)
		Slog.v(TAG, "SCHEDULE " + what + " " + mH.codeToString(what) + ": " + arg1 + " / " + obj);
	Message msg = Message.obtain();
	msg.what = what;
	msg.obj = obj;
	msg.arg1 = arg1;
	msg.arg2 = arg2;
	if(async) {
		msg.setAsynchronous(true);
	}
	mH.sendMessage(msg);
}
```

Handler H 对消息的处理：
```
private class H extends Handler {
	public static final int LAUNCH_ACTIVITY				= 100;
	public static final int PAUSE_ACTIVITY 				= 101;
	public static final int PAUSE_ACTIVITY_FINISHING	= 102;
	public static final int STOP_ACTIVITY_SHOW			= 103;
	public static final int STOP_ACTIVITY_HIDE 			= 104;
	public static final int SHOW_WINDOW					= 105;
	public static final int HIDE_WINDOW 				= 106;
	public static final int RESUME_ACTIVITY				= 107;
	...

	public void handleMessage(Message msg) {
		if(DEBUG_MESSAGES)
			Slog.v(TAG, ">>> handling: " + codeToString(msg.what));
		switch(msg.what) {
			case LAUNCH_ACTIVITY: {
				Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "activityStart");
				final ActivityClientRecord r = (ActivityClientRecord) msg.obj;
				r.packageInfo = getPackageInfoNoCheck(r.activityInfo.applicationInfo, r.compatInfo);
				handleLaunchActivity(r, null);
				Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
			} break;

			case RELAUNCH_ACTIVITY: {
				...
			} break;

			case PAUSE_ACTIVITY: {
				...
			} break;

			...
		}
		if(DEBUG_MESSAGES)
			Slog.v(TAG, "<<< done: " + codeToString(msg.what));
	}
}
```

从Handler H对“LAUNCH_ACTIVITY”这个消息的处理可知
Activity启动过程由ActivityThread的handleLaunchActivity方法来实现：
```
// ActivityThread.java
private void handleLaunchActivity(ActivityClientRecord r, Intent customIntent) {
	...
	if(localLOGV)
		Slog.v(TAG, "Handling launch of " + r);

	Activity a = performLaunchActivity(r, customIntent);

	if(a != null) {
		r.createConfig = new Configuration(mConfiguration);
		Bundle oldState = r.state;
		handleResumeActivity(r.token, false, r.isForward, !r.activity.mFinished && !r.startsNotResumed);
		...
	}
	...
}
```

performLaunchActivity方法最终完成了Activity对象的创建和启动过程
并且ActivityThread通过handleResumeActivity方法来调用被启动的Activity的onResume这一生命周期方法


performLaunchActivity这个方法主要完成了下面几件事：
1.从ActivityClientRecord中获取待启动的Activity的组件信息
```
ActivityInfo aInfo = r.activityInfo;
if(r.packageInfo == null) {
	r.packageInfo = getPackageInfo(aInfo.applicationInfo, r.compatInfo, Context.CONTEXT_INCLUDE_CODE);
}

ComponentName component = r.intent.getComponent();
if(component == null) {
	component = r.intent.resolveActivity(mInitialApplication.getPackageManager());
	r.intent.setComponent(component);
}

if(r.activityInfo.targetActivity != null) {
	component = new ComponentName(r.activityInfo.packageName, r.activityInfo.targetActivity);
}
```

2.通过Instrumentation的newActivity方法使用类加载器创建Activity对象
```
Activity activity = null;
try {
	java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
	activity = mInstrumentation.newActivity(cl, component.getClassName(), r.intent);
	StrictMode.incrementExpectedActivityCount(activity.getClass());
	r.intent.setExtraClassLoader(cl);
	r.intent.prepareToEnterProcess();
	if(r.state != null) {
		r.state.setClassLoader(cl);
	}
} catch(Exception e) {
	if(!mInstrumentation.onException(activity, e)) {
		throw new RuntimeException("Unable to instantiate activity " + component + 
				": " + e.toString(), e);
	}
}
```

mInstrumentation的newActivity实现比较简单，就是通过类加载器来创建Activity对象：
```
public Activity newActivity(ClassLoader cl, String className, Intent intent) 
		throw InstantiationException, IllegalAccessException, ClassNotFoundException {

	return (Activity) cl.loadClass(className).newInstance();
}
```

3.通过LoadedApk的makeApplication方法来尝试创建Application对象
```
public Application makeApplication(boolean forceDefaultAppClass, Instrumentation instrumentation) {
	if(mApplication != null) {
		return mApplication;
	}

	Application app = null;
	String appClass = mApplicationInfo.clasName;
	if(forceDefaultAppClass || (appClass == null)) {
		appClass = "android.app.Application";
	}

	try {
		java.lang.ClassLoader cl = getClassLoader();
		if(mPackageName.equals("android")) {
			initializeJavaContextClassLoader();
		}
		ContextImpl appContext = ContextImpl.createAppContext(mActivityThread, this);
		app = mActivityThread.mInstrumentation.newApplication(cl, appClass, appContext);
		appContext.setOuterContext(app);
	} catch(Exception e) {
		if(!mActivityThread.mInstrumentation.onException(app, e)) {
			throw new RuntimeException("Unable to instantiate application " + appClass +
					": " + e.toString(), e);
		}
	}
	mActivityThread.mAllApplication.add(app);
	mApplication = app;

	if(instrumentation != null) {
		try {
			instrumentation.callApplicationOnCreate(app);
		} catch(Exception e) {
			if(!instrumentation.onException(app, e)) {
				throw new RuntimeException("Unable to create application " + app.getClass().getName()
						+ ": " + e.toString(), e);
			}
		}
	}
	...
	return app;
}
```

如果Application已经被创建过了，那么就不会再重复创建了（一个应用只有一个Application对象）
Application对象的创建是通过Instrumentation来完成的
Instrumentation#newApplication()的过程和Instrumentation#newActivity()的过程一样，都是通过类加载器来实现的
Application创建完毕后，系统会调用Instrumentation#classApplicationOnCreate()来调用Application的onCreate方法

4.创建ContextImpl对象并通过Activity的attach方法来完成一些重要数据的初始化
```
Context appContext = createBaseContextForActivity(r, activity);
ChartSequence title = r.activityInfo.loadLabel(appContext.getPackageManager());
Configuration config = new Configuration(mCompatConfiguration);
if(DEBUG_CONFIGURATION)
	Slog.v(TAG, "Lauching activity " + r.activityInfo.name + " with config " + config);
activity.attach(appContext, this, getInstrumentation(), r.token, r.ident,
		app, r.intent, r.activityInfo, title, r.parent, r.embeddedID,
		r.lastNonConfigurationInstances, config, r.voiceInteractor);
```

ContextImpl是一个很重要的数据结构，它是Context的具体实现
ContextImpl是通过Activity的attach方法来和Activity建立关联的
除此之外，在attach方法中Activity还会完成Window的创建并建立自己和Window的关联
这样当Window接收到外部输入事件后就可以将事件传递给Activity了


5.调用Activity的onCreate方法
mInstrumentation.callActivityOnCreate(activity, r.state)
这时候Activity的onCreate被调用，意味着Activity已经完成了整个启动过程



## 3. Service的工作过程

Service分为两种工作状态
1.启动状态：主要用于执行后台计算
2.绑定状态：主要用于和其他组件交互

Service的这两种状态是可以共存的，即Service即可以处于启动状态也可以同时处于绑定状态

通过Context的startService方法启动一个Service：
```
Intent intentService = new Intent(this, MyService.class);
startService(intentService);
```

通过Context的bindService方法以绑定的方式启动一个Service：
```
Intent intentService = new Intent(this, MyService.class);
bindService(intentService, mServiceConnection, BIND_AUTO_CREATE);
```

### 3.1 Service的启动过程

Service的启动过程从ContextWrapper的startActivity开始：
```
public ComponentName startService(Intent service) {
	return mBase.startService(service);
}
```

上面的mBase类型是ContextImpl，




### 3.2 Service的绑定过程






## 4. BroadcastReceiver的工作过程



## 5. ContentProvider的工作过程







































