# 理解 Window 和 WindowManager

Window是一个抽象类，它的具体实现是PhoneWindow。
WindowManager是外界访问Window的入口，Window的具体实现位于WindowManagerService中，
WindowManager和WindowManagerService的交互是一个IPC过程。

Android中所有的视图都是通过Window来呈现的，不管是Activity、Dialog还是Toast，
它们的视图都是附加在Window上的，因此Window实际是View的直接管理者。

例：
1.单击事件由Window传递给DecorView，然后再由DecorView传递给我们的View
2.Activity的设置视图的方法setContentView在底层也是通过Window来完成的


## 1. Window和WindowManager

使用WindowManager添加一个Window，demo：
```
mFloatingButton = new Button(this);
mFloatingButton.setText("Button");
mLayoutParams = new WindowManager.LayoutParams(
		LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 
		0, 0, PixelFormat.TRANSPARENT);
mLayoutParams.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL | LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_SHOW_WHEN_LOCKED;
mLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
mLayoutParams.x = 100;
mLayoutParams.y = 300;
mWindowManager.addView(mFloatingButton, mLayoutParams);
```

上述代码将一个Button添加到屏幕坐标为（100，300）的位置上。

### Flags参数表示Window的属性，几个常用的属性：
FLAG_NOT_FOCUSABLE：Window不需要获取焦点，也不需要接受各种输入事件，
此标记会同时启用FLAG_NOT_TOUCH_MODAL，最终事件会直接传递给下层的具有焦点的Window。

FLAG_NOT_TOUCH_MODAL：在此模式下，系统会将当前Window区域以外的单击事件传递给底层的Window，
当前Window区域以内的单击事件则自己处理。一般来说需要开启此标记，否则其他Window将无法收到单击事件。

FLAG_SHOW_WHEN_LOCKED：开启此模式可以让Window显示在锁屏的界面上。


### Type参数表示Window的类型
Window有三种属性：应用Window、子Window、系统Window。
- 应用类Window对应着一个Activity
- 子Window不能单独存在，它需要附属在特定的父Window之中，比如Dialog
- 系统Window是需要声明权限才能创建的Window，比如Toast和系统状态栏这些都是系统Window

Window是分层的，每个Window都有对应的z-ordered，层级大的会覆盖在层级小的Window的上面
- 应用Window的层级范围是1~99
- 子Window的层级范围是1000~1999
- 系统Window的层级范围是2000~2999
这些层级范围对应着WindowManager.LayoutParams的type参数。
如果想要Window位于所有Window的最顶层，那么采用较大的层级既可。

系统层级有很多值，一般我们可以选用TYPE_SYSTEM_OVERLAY或者TYPE_SYSTEM_ERROR，
如果使用TYPE_SYSTEM_ERROR，只需要为type参数指定这个层级既可：mLayoutParams.type = LayoutParams.TYPE_SYSTEM_ERROR
同时声明权限：<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

因为系统类型的Window是需要检查权限的，如果不在AndroidManifest中使用相应的权限，那么创建Window的时候会报错：
```
E/AndroidRuntime(8071): Caused by: android.view.WindowManager$BadTokenException: 
Unable to add window android.view.ViewRootImpl$W@42882fe8 --permission denied for this window type
...
```


WindowManger所提供的功能很简单，常用的只有三个方法：添加View、更新View、删除View
这三个方法定义在ViewManager中，WindowManager继承了ViewManager
```
public interface ViewManager {
	void addView(View view, ViewGroup.LayoutParams params);
	void updateViewLayout(View view, ViewGroup.LayoutParams params);
	void removeView(View view);
}
```

可拖动的Window效果实现demo：
```
public boolean onTouch(View v, MotionEvent event) {
	int rawX = (int) event.getRawX();
	int rawY = (int) event.getRawY();
	switch(event.getAction()) {
		case MotionEvent.ACTION_MOVE:
			mLayoutParams.x = rawX;
			mLayoutParams.y = rawY;
			mWindowManager.updateViewLayout(mFloatingButton, mLayoutParams);
			break;
		default:
			break;
	}
	return false;
}
```


## 2. Window的内部机制

Window是一个抽象概念，每一个Window都对应着一个View和一个ViewRootImpl，
Window和View通过ViewRootImpl来建立联系，因此Window并不是实际存在的，它是以View的形式存在。

它提供的三个接口方法addView, updateView, removeView都是针对View的，View才是Window存在的实体。


### 2.1 Window的添加过程

WindowManager -> WindowManagerImpl -> WindowManagerGlobal -> ViewRootImpl ->
ViewRootImpl#setView() -> ViewRootImpl#requestLayout() -> ViewRootImpl#scheduleTraversals() ->
WindowSession -> 最终通过 WindowManagerService 来完成Window的添加


WindowManager是一个接口，它的真正实现是WindowManagerImpl类
在WindowManagerImpl中Window的三大操作的实现如下：
```
@Override
public void addView(View view, ViewGroup.LayoutParams params) {
	mGlobal.addView(view, params, mDisplay, mParentWindow);
}

@Override
public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
	mGlobal.updateViewLayout(view, params);
}

@Override
public void removeView(View view) {
	mGlobal.removeView(view, false);
}
```

WindowManagerImpl没有直接实现Window的三大操作，而是全部交给了WindowManagerGlobal来处理，
WindowManagerGlobal以工厂的形式向外提供自己的实例，在WindowManagerGlobal中有如下一段代码：
```
private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();
```
WindowMangerImpl这种工作模式是典型的桥接模式，将所有的操作全部委托给WindowManagerGlobal来实现。

WindowManagerGlobal的addView方法分为如下几步：
1.检查参数是否合法，如果是子Window那么还需要调整一些布局参数
```
// 检查参数是否合法
if(view == null) {
	throw new IllegalArgumentException("view must not be null");
}
if(display == null) {
	throw new IllegalArgumentException("display must not be null");
}
if(!(params instanceof WindowManager.LayoutParams)) {
	throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
}

final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams) params;
if(parentWindow != null) {
	// 如果是子Window那么还需要调整一些布局参数
	parentWindow.adjustLayoutParamsForSubWindow(wparams);
}
```

2.创建ViewRootImpl并将View添加到列表中
在WindowMangerGlobal内部有几个比较重要的列表：
```
private final ArrayList<View> mViews = new ArrayList<View>();
private final ArrayList<ViewRootImpl> mRoots = new ArrayList<ViewRootImpl>();
private final ArrayList<WindowManager.LayoutParams> mParams = new ArrayList<WindowManager.LayoutParams>();
private final ArraySet<View> mDyingViews = new ArraySet<View>();
```
mViews：所有Window所对应的View
mRoots：所有Window所对应的ViewRootImpl
mParams：所有Window所对应的布局参数
mDyingViews：正在被删除的View对象，或者说是已经调用removeView方法但是删除操作还未完成的Window对象

在addView中通过如下方式将Window的一系列对象添加到列表中：
```
root = new ViewRootImpl(view.getContext(), display);
view.setLayoutParams(wparams);

mViews.add(view);
mRoots.add(root);
mParams.add(wparams);
```


3.通过ViewRootImpl来更新界面并完成Window的添加过程
这个步骤由ViewRootImpl的setView方法来完成
View的绘制过程是由ViewRootImpl来完成的，这里也不例外，在setView内部会通过调用requestLayout来完成异步刷新请求

在下面的代码中，scheduleTraversals实际是View绘制的入口：
```
public void requestLayout() {
	if(!mHandlingLayoutInLayoutRequest) {
		checkThread();
		mLayoutRequested = true;
		scheduleTraversals();
	}
}
```

接着会通过WindowSession最终来完成Window的添加过程。
下面的代码中，mWindowSession的类型是IWindowSession，它是一个Binder对象，真正的实现类是Session，
也就是Window的添加过程是一次IPC调用
```
try {
	mOrigWindowType = mWindowAttributes.type;
	mAttachInfo.mRecomputeGlobalAttributes = true;
	collectViewAttributes();
	res = mWindowSession.addToDisplay(mWindow, mSeq, mWindowAttributes,
			getHostVisibility(), mDisplay.getDisplayId(),
			mAttachInfo.mContentInsets, mInputChannel);
} catch (RemoteException e) {
	mAdded = false;
	mView = null;
	mAttachInfo.mRootView = null;
	mInputChannel = null;
	mFallbackEventHandler.setView(null);
	unscheduleTraversals();
	setAccessibilityFocus(null, null);
	throw new RuntimeException("Adding window failed", e);
}

```

在Session内部会通过WindowManagerService来完成Window的添加，代码如下所示：
```
public int addToDisplay(IWindow window, int seq, WindowManager.LayoutParams attrs,
		int viewVisibility, int displayId, Rect outContentInsets, InputChannel outInputChannel) {
	return mService.addWindow(this, window, seq, attrs, viewVisibility,
			displayId, outContentInsets, outInputChannel);
}
```
Window的添加请求交给WindowManagerService去处理了
在WindowManagerService内部会为每一个应用保留一个单独的Session



### 2.2 Window的删除过程

WindowManager -> WindowManagerImpl -> WindowManagerGlobal ->
WindowManager#removeView() 或 WindowManager#removeViewImmediate() ->
ViewRootImpl#die() -> ViewRootImpl#doDie() -> dispatchDetachFromWindow() ->
Session#remove() -> WindowManagerService#removeWindow()


Window的删除过程和添加过程一样，都是先通过WindowManagerImpl后，再进一步通过WindowManagerGlobal来实现的。
```
public void removeView(View view, boolean immediate) {
	if(view == null) {
		throw new IllegalArgumentException("view must not be null");
	}

	synchronized(mLock) {
		int index = findViewLocked(view, true);
		View curView = mRoots.get(index).getView();
		removeViewLocked(index, immediate);
		if(curView == view) {
			return;
		}

		throw new IllegalArgumentException("Calling with view " + view
				+ " but the ViewAncestor is attached to " + curView);
	}
}
```
首先通过findViewLocked来查找待删除的View的索引，这个查找过程就是对建立的数组遍历

然后调用removeViewLocked来做进一步的删除
```
private void removeViewLocked(int index, boolean immediate) {
	ViewRootImpl root = mRoots.get(index);
	View view = root.getView();
	if(view != null) {
		InputMethodManager imm = InputMethodManager.getInstance();
		if(imm != null) {
			imm.windowDismissed(mViews.get(index).getWindowToken());
		}
	}
	boolean deferred = root.die(immediate);
	if(view != null) {
		view.assignParent(null);
		if(deferred) {
			mDyingViews.add(view);
		}
	}
}
```
removeViewLocked是通过ViewRootImpl来完成删除操作的。
在WindowManager中提供了两种删除接口：removeView（异步删除）和removeViewImmediate（同步删除）
其中removeViewImmediate使用起来要特别注意，一般来说不需要使用此方法来删除Window以免发生意外错误

这里主要说removeView（异步删除）的情况，删除操作由ViewRootImpl的die方法来完成
**在异步删除的情况下，die只是发送一个请求删除的消息后就立刻返回了**
**这时候View并没有完成删除操作，所以最后会将其添加到mDyingViews中，mDyingViews表示待删除的View列表**

ViewRootImpl的die方法如下所示：
```
boolean die(boolean immediate) {
	// Make sure we do execute immediately if we are in the middle of a traversal
	// or the damage done by dispatchDetachedFromWindow will cause havoc on return.
	// 如果我们正在遍历中，要确保立即执行，否则dispatchDetachedFromWindow造成的破坏会导致严重的后果
	if(immediate && !mIsInTraversal) {
		doDie();
		return false;
	}

	if(!mIsDrawing) {
		destroyHardwareRenderer();
	} else {
		Log.e(TAG, "Attempting to destroy the window while drawing!\n" + 
			" window=" + this + ", title=" + mWindowAttributes.getTitle());
	}
	mHandler.sendEmptyMessage(MSG_DIE);
	return true;
}
```
如果是异步删除，那么就发送一个MSG_DIE的消息，ViewRootImpl中的Handler会处理此消息并调用doDie()方法
如果是同步删除（立即删除），那么就不发送消息直接调用doDie()方法

在doDie()方法内部会调用dispatchDetachFromWindow()方法，真正删除View的逻辑在dispatchDetachFromWindow()方法内部实现

#### dispatchDetachFromWindow()方法主要做了四件事：
1.垃圾回收相关的工作，比如清除数据和消息、移除回调

2.通过Session的remove方法删除Window：mWindowSession.remove(mWindow)，
这同样是一个IPC过程，最终会调用WindowManagerService的removeWindow方法

3.调用View的dispatchDetachedFromWindow方法，
在内部会调用onDetachedFromWindow()以及onDetachedFromWindowInternal()。

4.调用WindowManagerGlobal的doRemoveView方法刷新数据，
包括mRoots、mParams、mDyingViews，需要将当前Window所关联的这三类对象从列表中删除



### 2.3 Window的更新过程

WindowManager -> WindowManagerImpl -> WindowManagerGlobal#updateViewLayout() ->
ViewRootImpl#setLayoutParams() -> ViewRootImpl#scheduleTraversals() ->
measure(), layout(), draw() && WindowSession -> WindowManagerService#relayoutWindow()

```
public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
	if(view == null) {
		throw new IllegalArgumentException("view must not be null");
	}
	if(!(params instanceof WindowManager.LayoutParams)) {
		throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
	}

	final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams) params;

	view.setLayoutParams(wparams);

	synchronized(mLock) {
		int index = findViewLocked(view, true);
		ViewRootImpl root = mRoots.get(index);
		mParams.remove(index);
		mParams.add(index, wparams);
		root.setLayoutParams(wparams, false);
	}
}
```
首先它需要更新View的LayoutParams并替换掉老的LayoutParams
接着再更新ViewRootImpl的LayoutParams，这一步是通过ViewRootImpl的setLayoutParams来实现的

在ViewRootImpl中会通过scheduleTraversals方法来对View重新布局，包括测量、布局、重绘。
除了View本身的重绘以外，ViewRootImpl还会通过WindowSession来更新Window的视图，
这个过程最终是由WindowManagerService的relayoutWindow()来具体实现的，同样是一个IPC过程



## 3. Window的创建过程

View是Android中的视图的呈现方式
但是View不能单独存在，它必须附着在Window这个抽象的概念上面，有视图的地方就有Window

可提供视图的地方有：Activity, Dialog, Toast
还有一些依托Window而实现的视图：PopUpWindow, 菜单
有视图的地方就有Window，因此Activity、Dialog、Toast等视图都对应着一个Window

本节分析这些视图元素中的Window的创建过程


### 3.1 Activity的Window创建过程

要分析Activity中的Window的创建过程就必须了解Activity的启动过程

Activity的启动过程很复杂，最终会由ActivityThread中的performLaunchActivity()来完成整个启动过程
在这个方法内部会通过类加载器创建Activity的实例对象，
并调用其attach方法为其关联运行过程中所依赖的一系列上下文环境变量，代码如下：
```
java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
activity = mInstrumentation.newActivity(cl, component.getClassName(), r.intent);
...
if(activity != null) {
	Context appContext = createBaseContextForActivity(r, activity);
	CharSequence title = r.activityInfo.loadLabel(appContext.getPackageManager());
	Configuration config = new Configuration(mCompatConfiguration);

	if(DEBUG_CONFIGURATION)
		Slog.v(TAG, "Launching activity " + r.activityInfo.name +
				" with config " + config);
	activity.attach(appContext, this, getInstrumentation(), r.token,
			r.ident, app, r.intent, r.activityInfo, title, r.parent,
			r.embeddedID, r.lastNonConfigurationInstances, config,
			r.voiceInteractor);
	...
}
```

在Activity的attach方法里，系统会创建Activity所属的Window对象并为其设置回调接口，
Window对象的创建是通过PolicyManager的makeNewWindow方法实现的。

由于Activity实现了Window的Callback接口，因此当Window接收到外界的状态改变时就会回调Activity的方法
Callback接口中的方法很多，有几个是我们比较熟悉的，比如：
onAttachedToWindow, onDetachedFromWindow, dispatchTouchEvent等，代码如下：
```
mWindow = PolicyManager.makeNewWindow(this);
mWindow.setCallback(this);
mWindow.setOnWindowDismissedCallback(this);
mWindow.getLayoutInflater().setPrivateFactory(this);

if(info.softInputMode != WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED) {
	mWindow.setSoftInputMode(info.softInputMode);
}

if(info.uiOptions != 0) {
	mWindow.setUiOptions(info.uiOptions);
}
```

Activity的Window是通过PolicyManager的一个工厂方法来创建的，
PolicyManager是一个策略类，其中实现的几个工厂方法全部在策略接口IPolicy中声明了，
IPolicy的定义如下：
```
public interface IPolicy {
	Window makeNewWindow(Context context);
	LayoutInflater makeNewLayoutInflater(Context context);
	WindowManagerPolicy makeNewWindowManager();
	FallbackEventHandler makeNewFallbackEventHandler(Context context);
}
```

在实际调用中，PolicyManager的真正实现是Policy类，Policy类中的makeNewWindow方法实现如下：
Window的具体实现是PhoneWindow
```
public Window makeNewWindow(Context context) {
	return new PhoneWindow(context);
}
```

下面分析Activity的视图怎么附属在Window上的，Activity的视图由setContentView()方法提供：
```
public void setContentView(int layoutResID) {
	getWindow().setContentView(layoutResID);
	initWindowDecorActionBar();
}
```
Activity将具体实现交给了PhoneWindow处理



PhoneWindow的setContentView方法大概遵循如下几个步骤：

1.如果没有DecorView，那么就创建它
DecorView是一个FrameLayout
DecorView是Activity中的顶级View，一般来说它的内部包含**标题栏**和**内部栏**（会随着主题的变换而发生改变）
内容栏是一定存在的，内容栏的id是固定的，为android.R.id.content

DecorView的创建过程由installDecor方法来完成，在方法内部会通过generateDecor方法来直接创建DecorView
这个时候DecorView还只是一个空白的FrameLayout：
```
protected DecorView generateDecor() {
	return new DecorView(getContext(), -1);
}
```

为了初始化DecorView结构，PhoneWindow还需要通过generateLayout方法来加载具体的布局文件到DecorView中，
具体的布局文件和系统版本以及主题有关，这个过程如下：
```
View in = mLayoutInfalter.inflate(layoutResource, null);
decor.addView(in, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
mContentRoot = (ViewGroup) in;
ViewGroup contentParent = (ViewGroup) findViewById(ID_ANDROID_CONTENT);
```

其中ID_ANDROID_CONTENT的定义如下，这个id所对应的ViewGroup就是mContentParent：
```
public static final int ID_ANDROID_CONTENT = com.android.internal.R.id.content;
```


2.将View添加到DecorView的mContentParent中
步骤1中已经创建并初始化了DecorView
因此这一步直接将Activity的视图添加到DecorView的mContentParent中即可：
mLayoutInfalter.inflate(layoutResID, mContentParent);

到此为止，Activity的布局文件已经添加到DecorView里面了
（这也是为什么方法叫setContentView而不是setView或者其他名字的原因）


3.回调Activity的onContentChanged方法通知Activity视图已经发生改变
由于Activity实现了Window的Callback接口，
这里表示Activity的布局文件已经被添加到DecorView的mContentParent中了，
于是需要通知Activity，使其可以做相应的处理。
```
final Callback cb = getCallback();
if(cb != null && !isDestroyed()) {
	cb.onContentChanged();
}
```
Activity的onContentChange方法是个空实现，我们可以在子Activity中处理这个回调



**经过上面三个步骤，DecorView已经被创建完成并且初始化完毕**
**Activity的布局文件也已经成功添加到DecorView的mContentParent中，**
**但这时DecorView还没有被WindowManager正式添加到Window中。**

虽然说早在Activity的attach方法中，Window已经被创建了，
但是这个时候由于DecorView并没有被WindowManager识别，所以这个时候的Window无法提供具体功能，
因为它还无法接受外界的输入信息

在ActivityThread的handleResumeActivity方法中，
首先会调用Activity的onResume方法，
接着会调用Activity的makeVisible()，
**正是在makeVisible方法中，DecorView真正地完成了添加和显示这两个过程，**
到这里Activity的视图才能被用户看到，如下所示：
```
void makeVisible() {
	if(!mWindowAdded) {
		ViewManager wm = getWindowManager();
		wm.addView(mDecor, getWindow().getAttributes());
		mWindowAdded = true;
	}
	mDecor.setVisibility(View.VISIBLE);
}
```


### 3.2 Dialog的Window创建过程

Dialog的Window创建过程和Activity类似

1.创建Window
Dialog中Window的创建同样是通过PolicyManager的makeNewWindow方法来完成的
创建后的对象实际上就是PhoneWindow，过程和Activity的Window创建过程一致
```
Dialog(Context context, int theme, boolean createContextThemeWrapper) {
	...
	mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
	Window w = PolicyManager.makeNewWindow(mContext);
	mWindow = w;
	w.setCallback(this);
	w.setOnWindowDismissedCallback(this);
	w.setWindowManager(mWindowManager, null, null);
	w.setGravity(Gravity.CENTER);
	mListenersHandler = new ListenersHandler(this);
}
```


2.初始化DecorView并将Dialog的视图添加到DecorView中
这个过程也和Activity的类似，都是通过Window去添加指定的布局文件
```
public void setContentView(int layoutResID) {
	mWindow.setContentView(layoutResID);
}
```


3.将DecorView添加到Window中并显示
在Dialog的show方法中，会通过WindowManager将DecorView添加到Window中：
```
mWindowManager.addView(mDecor, l);
mShowing = true;
```


从上面三个步骤看，Dialog的Window创建和Activity的Window创建过程很类似，几乎没什么区别
当Dialog被关闭时，它会通过WindowManager来移除DecorView：
```
mWindowManager.removeViewImmediate(mDecor);
```


普通的Dialog有一个特殊之处，就是必须采用Activity的Context，
如果采用Application的Context，那么就会报错，如：
```
Dialog dialog = new Dilaog(this.getApplicationContext());
TextView textView = new TextView(this);
textView.setText("this is toast!");
dialog.setContentView(textView);
dialog.show();
```
错误如下：
```
E/AndroidRuntime(1185): Caused by: android.view.WindowManager$BadTokenException:
Unable to add window -- token null is not for an application
...
```

上述错误表明没有应用token，而应用token一般只有Activity拥有，
所以这里只需要用Activity作为Context来显示对话框即可。

另外，系统Window比较特殊，它可以不需要token，
因此在上面的例子中，只需要指定对话框的Window为系统类型就可以正常弹出对话框。

本章一开始讲到，WindowManager.LayoutParams中的type表示Window的类型，
而系统Window的层级范围是2000~2999，这些层级就对应着type参数。
对于本例来说，可以选用TYPE_SYSTEM_ERROR来指定对话框的Window类型为系统Window，如下所示：
dialog.getWindow().setType(LayoutParams.TYPE_SYSTEM_ERROR);

然后在AndroidManifest中声明权限：
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />



### 3.3 Toast的Window创建过程

Toast和Dialog不同，它的工作过程就稍显复杂。
首先Toast也是基于Window来实现的，但是由于Toast有定时取消这一功能，所以系统采用了Handler

在Toast内部有两类IPC过程，
第一类是 Toast 访问 NotificationManagerService
第二类是 NotificationManagerService 回调 Toast 里的TN接口

为了便于描述，下面将 NotificationManagerService 简称为 NMS

Toast属于系统Window，它内部的视图由两种方式指定，
一种是系统默认的样式，另一种是通过setView方法来指定一个自定义View

不管如何，它们都对应Toast的一个View类型的内部成员mNextView
Toast提供了show和cancel分别用于显示和隐藏Toast，它们的内部是一个IPC过程，
show方法和cancel方法的实现如下：
```
public void show() {
	if(mNextView == null) {
		throw new RuntimeException("setView must have been called");
	}

	INotificatonManager service = getService();
	String pkg = mContext.getOpPackageName();
	TN tn = mTN;
	tn.mNextView = mNextView;

	try {
		service.enqueueToast(pkg, tn, mDuration);
	} catch (RemoteException e) {
		// Empty
	}
}

public void cancel() {
	mTN.hide();

	try {
		getService().cancelToast(mContext.getPackageName(), mTN);
	} catch (RemoteException e) {
		// Empty
	}
}
```
从代码可以看到，显示和隐藏Toast都需要通过NMS来实现，
由于NMS运行在系统的进程中，所以只能通过远程调用的方式来显示和隐藏Toast。

需要注意的是TN这个类，它是一个Binder类，在Toast和NMS进行IPC的过程中，
当NMS处理Toast的显示或者隐藏请求时会跨进程回调TN中的方法，这个时候由于TN运行在Binder线程池中，
所以需要通过Handler将其切换到当前线程（指发送Toast请求所在的线程）中。

注意，由于这里使用了Handler，所以意味着Toast无法在没有Looper的线程中弹出，
因为Handler需要使用Looper才能完成切换线程的功能


首先看Toast的显示过程，它调用了NMS中的enqueueToast方法：
```
INotificatonManager service = getService();
String pkg = mContext.getOpPackageName();
TN tn = mTN;
tn.mNextView = mNextView;

try {
	service.enqueueToast(pkg, tn, mDuration);
} catch (RemoteException e) {
	// Empty
}
```

第一个参数：当前应用包名
第二个参数：tn表示远程回调
第三个参数：Toast的时长

enqueueToast首先将Toast请求封装为ToastRecord对象并将其添加到一个名为mToastQueue的队列中
mToastQueue是一个ArrayList，**对于非系统应用来说，mToastQueue中最多能同时存在50个ToastRecord**
这么做是为了防止DOS（Denial of Service）。

如果不这么做，这将会导致其他应用没有机会弹出Toast，
那么对于其他应用的Toast请求，系统的行为就是拒绝服务，这就是拒绝服务攻击的含义，这种手段常用于网络攻击中。
```
// Limit the number of toasts that any given package except the android
// package can enqueue. Prevents DOS attacks and deals with leaks.
if(!isSystemToast) {
	int count = 0;
	final int N = mToastQueue.size();
	for(int i = 0; i < N; ++i) {
		final ToastRecord r = mToastQueue.get(i);
		if(r.pkg.equals(pkg)) {
			++count;
			if(count >= MAX_PACKAGE_NOTIFICATIONS) {
				Slog.e(TAG, "Package has already posted " + count 
					+ " toasts. Not showing more. Package=" + pkg);
				return;
			}
		}
	} 
}
```

正常情况下，一个应用不可能达到上限，当ToastRecord被添加到mToastQueue中后，
NMS就会通过showNextToastLocked方法来显示当前的Toast。

Toast的显示是由ToastRecord的callback来完成的，
这个callback实际上就是Toast中的TN对象的远程Binder，
通过callback来访问TN中的方法是需要跨进程来完成的，
最终被调用的TN中的方法会运行在发起Toast请求的应用的Binder线程池中
```
void showNextToastLocked() {
	ToastRecord record = mToastQueue.get(0);
	while(record != null) {
		if(DBG) {
			Slog.d(TAG, "Show pkg=" + record.pkg + " callback=" + record.callback);
		}
		try {
			record.callback.show();
			sheduleTimeoutLocked(record);
			return;
		} catch (RemoteException e) {
			Slog.w(TAG, "Object died trying to show notification " + record.callback + 
				" in package " + record.pkg);
			// remove it from the list and let the process die 
			int index = mToastQueue.indexOf(record);
			if(index >= 0) {
				mToastQueue.remove(index);
			}
			keepProcessAliveLocked(record.pid);
			if(mToastQueue.size() > 0) {
				record = mToastQueue.get(0);
			} else {
				record = null;
			}
		}
	}
}
```

Toast显示后，NMS还会通过scheduleTimeoutLocked方法来发送一个延时消息，
具体的延时取决于Toast的时长，如下所示：
```
private void scheduleTimeoutLocked(ToastRecord r) {
	mHandler.removeCallbackAndMessages(r);
	Message m = Message.obtain(mHandler, MESSAGE_TIMEOUT, r);
	long delay = r.duration == Toast.LENGTH_LONG ? LONG_DELAY : SHORT_DELAY;
	mHandler.sendMessageDelayed(m, delay);
}
```

在上面的代码中，LONG_DELAY是3.5s，SHORT_DELAY是2s。
延迟相应的时间后，NMS会通过cancelToastLocked方法来隐藏Toast并将其从mToastQueue中移除，
这个时候如果mToastQueue中还有其他的Toast，那么NMS就继续显示其他Toast


Toast的隐藏也是通过ToastRecord的callback来完成的，
这同样也是一次IPC过程，它的工作方式和Toast的显示过程是类似的：
```
try {
	record.callback.hide();
} catch (RemoteException e) {
	Slog.w(TAG, "Object died trying to hide notification " + record.callback 
		+ " in package " + record.pkg);
	// don't worry about this, we're about to remove it from the list anyway
}

```

Toast的显示和隐藏过程是通过Toast中的TN这个类来实现的
它的两个方法show和hide对应着Toast的显示和隐藏
由于这两个方法是被NMS以跨进程的方式调用的，因此它们运行在Binder线程池中
为了将执行环境切换到Toast请求所在的线程，在它们的内部使用了Handler：
```
/**
 * schedule handleShow into the right thread
 */ 
@Override
public void show() {
	if(localLOGV)
		Log.v(TAG, "SHOW: " + this);
	mHandler.post(mShow);
}

/**
 * schedule handleHide into the right thread
 */ 
@Override
public void hide() {
	if(localLOGV)
		Log.v(TAG, "HIDE: " + this);
	mHandler.post(mHide);
}
```

上述代码中，mShow和mHide是两个Runnable，它们内部分别调用了handleShow和handleHide方法

由此可见，handleShow和handleHide才是真正完成显示和隐藏Toast的地方
TN的handleShow中会将Toast的视图添加到Window中：
```
mWM = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
mWM.addView(mView, mParams);
```

TN的handleHide中会将Toast的视图从Window中移除：
```
if(mView.getParent() != null) {
	if(localLOGV)
		Log.v(TAG, "REMOVE! " + mView + " in " + this);
	mWM.removeView(mView);
}
```


## 总结
本章的意义在于让读者对于Window有一个更加清晰的认识，同时能够深刻理解Window和View的依赖关系，
这有助于理解其他更深层级的概念，比如SurfaceFlinger。
通过本章读者可以知道，任何View都是附属在一个Window上面的。




