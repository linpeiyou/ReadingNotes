# 理解RemoteViews

RemoteViews表示的是一个View结构，它可以在其他进程中显示
由于RemoteViews在其他进程中显示，为了能够更新它的界面，RemoteViews提供了一组基础的操作用于跨进程更新界面

RemoteViews在Android中的使用场景有两种：通知栏和桌面小部件


## 1. RemoteViews的应用

通知栏：主要是通过NotificationManager的notify方法来实现的，除了默认效果外，还可以另外定义布局
桌面小部件：通过AppWidgetProvider实现，AppWidgetProvider本质上是一个广播

它们更新界面时无法像在Activity里面那样去直接更新View
这是因为二者的界面都运行在其他进程中，确切来说是系统的SystemServer进程

为了跨进程更新界面，RemoteViews提供了一系列set方法，并且这些方法只是View全部方法的子集
另外RemoteViews支持的View类型也是有限的

### 1.1 RemoteViews在通知栏上的应用

RemoteViews在通知栏上除了默认的效果外，还支持自定义布局

使用系统默认的样式弹出一个通知：
单击通知后会打开DemoActivity_1同时会清除本身
```
Notification notification = new Notification();
notification.icon = R.drawable.ic_launcher;
notification.tickerText = "hello world";
notification.when = System.currentTimeMillis();
notification.flags = Notification.FLAG_AUTO_CANCEL;
Intent intent = new Intent(this, DemoActivity_1.class);
PendingIntent pendingIntent = PendingIntent.getActivity(this, 
		0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
notification.setLatestEventInfo(this, "chapter_5", "this is notification.", pendingIntent);
NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
manager.nofity(1, notification);
```

自定义通知：
提供一个布局文件，然后通过RemoteViews来加载这个布局文件即可
```
Notification notification = new Notification();
notification.icon = R.drawable.ic_launcher;
notification.tickerText = "hello world";
notification.when = System.currentTimeMillis();
notification.flags = Notification.FLAG_AUTO_CANCEL;
Intent intent = new Intent(this, DemoActivity_1.class);
PendingIntent pendingIntent = PendingIntent.getActivity(this, 
		0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.layout_notification);
remoteViews.setTextViewText(R.id.msg, "chapter_5");
remoteViews.setImageViewResource(R.id.icon, R.drawable.icon1);
PendingIntent openActivity2PendingIntent = PendingIntent.getActivity(this, 0,
		new Intent(this, DemoActivity_2.class), PendingIntent.FLAG_UPDATE_CURRENT);
remoteViews.setOnClickPendingIntent(R.id.open_activity2, openActivity2PendingIntent);

notification.contentView = remoteViews;
notification.contentIntent = pendingIntent;
NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
manager.notify(2, notification);
```

只要提供当前应用的包名和布局文件的资源id即可创建一个RemoteViews对象
更新RemoteViews时，无法直接访问里面的View，必须通过RemoteViews所提供的一系列方法来更新View
（如：setTextViewText、setImageViewResource）
要给一个控件加单击事件，则要使用PendingIntent并通过setOnClickPendingIntent方法来实现


### 1.2 RemoteViews在桌面小部件上的应用

AppWidgetProvider本质上是一个广播，它继承了BroadcastReceiver

桌面小部件的开发步骤：

#### 1. 定义桌面小部件
在res/layout/下新建一个XML文件，命名为widget.xml，如：
```
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
	android:layout_width="match_parent"
	android:layout_height="match_parent"
	android:orientation="vertical">

	<ImageView
		android:id="@+id/imageView1"
		android:layout_width="wrap_content"
		android:layout_height="wrap_content"
		android:src="@drawable/icon1" />
<LinearLayout/>
```

#### 2. 定义小部件配置信息
在res/xml下新建appwidget_provider_info.xml，名称随意选择，添加如下内容：
```
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
	android:initialLayout="@layout/widget"
	android:minHeight="84dp"
	android:minWidth="84dp"
	android:updatePeriodMillis="8640000"/>
```
上面几个参数的意义：initialLayout是指小工具所使用的初始化布局；minHeight和minWidth定义小工具的最小尺寸；
updatePeriodMillis定义小工具的自动更新周期，毫秒为单位，每隔一个周期，小工具的自动更新就会触发

#### 3. 定义小部件的实现类
这个类需要继承AppWidgetProvider，代码如下：
下面的代码实现了一个简单的桌面小部件，小部件上显示一张图片。单击它后，这个图片就会旋转一周。
当小部件被添加到桌面后，会通过RemoteViews来加载布局文件
而当小部件被单击后的旋转效果则是通过不断地更新RemoteViews来实现的
```
public class MyAppWidgetProvider extends AppWidgetProvider {
	public static final String CLICK_ACTION = "com.ryg.chapter_5.action.CLICK";

	public MyAppWidgetProvider() {
		super();
	}

	@Override
	public void onReceive(final Context context, Intent intent) {
		super.onReceive(context, intent);
		// 这里判断是不是自己的action
		if(intent.getAction().equals(CLICK_ACTION)) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					Bitmap srcbBitmap = BitmapFactory.decodeResource(
							context.getResources(), R.drawable.icon1);
					AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
					for(int i = 0; i < 37; ++i) {
						float degree = (i * 10) % 360;
						RemoteViews remoteViews = new RemoteViews(
								context.getPackageName(), R.layout.widget);
						remoteViews.setImageViewResource(R.id.imageView1,
								rotateBitmap(context, srcbBitmap, degree));
						Intent intentClick = new Intent();
						intentClick.setAction(CLICK_ACTION);
						PendingIntent pendingIntent = PendingIntent.getBroadcast(
								context, 0, intentClick, 0);
						remoteViews.setOnClickPendingIntent(R.id.imageView1, 
								pendingIntent);
						appWidgetManager.updateAppWidget(new ComponentName(
								context, MyAppWidgetProvider.class), remoteViews);
						SystemClock.sleep(30);
					}
				}

			}).start();
		}
	}

	/**
	 * 每次桌面小部件更新时都调用一次该方法
	 */
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);

		final int counter = appWidgetIds.length;
		for(int i = 0; i < counter; ++i) {
			int appWidgetId = appWidgetIds[i];
			onWidgetUpdate(context, appWidgetManager, appWidgetId);
		}
	}

	/**
	 * 桌面小部件更新
	 */
	@Override
	private void onWidgetUpdate(Context context, AppWidgetManager appWidgetManager,
			int appWidgetId) {
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget);

		// "桌面小部件"单击事件发送的Intent广播
		Intent intentClick = new Intent();
		intentClick.setAction(CLICK_ACTION);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intentClick, 0);
		remoteViews.setOnClickPendingIntent(R.id.imageView1, remoteViews);
		appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
	}

	private Bitmap rotateBitmap(Context context, Bitmap srcbBitmap, float degree) {
		Matrix matrix = new Matrix();
		matrix.reset();
		matrix.setRotate(degree);
		Bitmap tmpBitmap = Bitmap.createBitmap(srcbBitmap, 0, 0, 
				srcbBitmap.getWidth(), srcbBitmap.getHeight(), matrix, true);
		return tmpBitmap;
	}
}
```

#### 4. AndroidManifest.xml中声明小部件
因为桌面小部件本质上是一个广播组件，因此必须要注册
```
<receiver android:name=".MyAppWidgetProvider">
	<meta-data
		android:name="android.appwidget.provider"
		android:resource="@xml/appwidget_provider_info">
	</meta-data>

	<intent-filter>
		<action android:name="com.ryg.chapter_5.action.CLICK" />
		<action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
	</intent-filter>
</receiver>
```

第一个Action用于识别小部件的单击行为
第二个Action则作为小部件的标识而必须存在（不加的话这个receiver就不是桌面小部件，也无法出现在手机的小部件列表里）


AppWidgetProvider除了最常用的onUpdate方法，还有其他几个方法：
**onEnabled、onDisabled、onDeleted、onReceive，这些方法会自动地被onReceive方法在合适的时间调用**


**当广播到来后，AppWidgetProvider会自动根据广播的Action通过onReceive方法来自动分发广播，也就是调用上述几个方法**
调用时机如下：
onEnable：当该窗口小部件第一次添加到桌面时调用该方法，可以添加多次但只在第一次调用

onUpdate：小部件被添加时或者每次小部件更新时都会调用一次改方法，
小部件的更新时机由updatePeriodMillis来指定，每个周期小部件都会自动更新一次

onDeleted：每删除一次桌面小部件就调用一次

onDisabled：当最后一个该类型的桌面小部件被删除时调用该方法

onReceive：这是广播的内置方法，用于分发具体的事件给其他方法

AppWidgetProvider的onReceive方法源码实现：
```
// AppWidgetProvider#onReceive()
public void onReceive(Context context, Intent intent) {
	// Project against rogue update broadcasts (not really a security issue,
	// just filter bad broadcasts out so subclasses are less likely to crash).
	String action = intent.getAction();
	if(AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
		Bundle extras = intent.getExtras();
		if(extras != null) {
			int[] appWidgetIds = extras.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
			if(appWidgetIds != null && appWidgetIds.length > 0) {
				this.onUpdate(context, AppWidgetManager.getInstance(context), appWidgetIds);
			}
		}

	} else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
		Bundle extras = intent.getExtras();
		if(extras != null && extras.containsKey(AppWidgetManager.EXTRA_APPWIDGET_ID)) {
			final int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
			this.onDelete(context, new int[] { appWidgetId });
		}

	} else if (AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(action)) {
		Bundle extras = intent.getExtras();
		if(extras != null && extras.containsKey(AppWidgetManager.EXTRA_APPWIDGET_ID)
				&& extras.containsKey(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS)) {
			int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
			Bundle widgetExtras = extras.getBundle(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS);
			this.onAppWidgetOptionsChanged(context, AppWidgetManager.getInstance(context),
					appWidgetId, widgetExtras);
		}

	} else if (AppWidgetManager.ACTION_APPWIDGET_ENABLED.equals(action)) {
		this.onEnabled(context);

	} else if (AppWidgetManager.ACTION_APPWIDGET_DISABLED.equals(action)) {
		this.onDisabled(context);

	} else if (AppWidgetManager.ACTION_APPWIDGET_RESTORED.equals(action)) {
		Bundle extras = intent.getExtras();
		if(extras != null) {
			int[] oldIds = extras.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS);
			int[] newIds = extras.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
			if(oldIds != null && oldIds.length > 0) {
				this.onRestored(context, oldIds, newIds);
				this.onUpdate(context, AppWidgetManager.getInstance(context), newIds);
			}
		}
	}
}
```


### 1.3 PendingIntent概述

PendingIntent是什么？
PendingIntent和Intent的区别是什么？

PendingIntent表示的是接下来有一个Intent（即意图）将在某个特定的时刻发生
它的Intent的区别在于，PendingIntent是在将来的某个不确定的时刻发生，而Intent是立刻发生


PendingIntent典型的使用场景是给RemoteViews添加单击事件，因为RemoteViews运行在远程进程中，
因此RemoteViews无法像普通的View那样通过setOnClickListener方法设置单击事件

**PendingIntent支持三种待定意图：启动Activity、启动Service、发送广播**
对应着它的三个接口方法：
```
// 获得一个PendingIntent，该待定意图发生时，效果相当于Context.startActivity(Intent);
static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags);

// 获得一个PendingIntent，该待定意图发生时，效果相当于Context.startService(Intent);
static PendingIntent getService(Context context, int requestCode, Intent intent, int flags);

// 获得一个PendingIntent，该待定意图发生时，效果相当于Context.sendBroadcast(Intent)
static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags);
```

这里说下第二个参数requestCode和第四个参数flags：
requestCode表示PendingIntent发送方的请求码，多数情况下设为0即可，requestCode会影响到flags的效果
flags常见的类型有：FLAG_ONE_SHOT、FLAG_NO_CREATE、FLAG_CANCEL_CURRENT、FLAG_UPDATE_CURRENT

说明这四个标记位之前，要明白一个概念：**PendingIntent的匹配规则，即在什么情况下两个PendingIntent是相同的**

**PendingIntent的匹配规则为：**
如果两个PendingIntent它们内部的Intent相同并且requestCode也相同，那么这两个PendingIntent就是相同的
**Intent的匹配规则为：**
如果两个Intent的ComponentName和intent-filter都相同，那么这两个Intent就是相同的（Extras可以不同）

FLAG_ONE_SHOT
当前描述的PendingIntent只能被使用一次，然后它就会被自动cancel
如果后续还有相同的PendingIntent，那么它们的send方法就会调用失败。
对于通知栏消息来说，如果采用此标记位，那么同类的通知只能使用一次，后续的通知单击后将无法打开

FLAG_NO_CREATE
当前描述的PendingIntent不会主动创建，如果当前PendingIntent之前不存在，
那么getActivity、getService、getBroadcast方法会直接返回null，即获取PendingIntent失败。
这个标记位很少见，它无法单独使用，在日常开发中它并没有太多的使用意义

FLAG_CANCEL_CURRENT
当前描述的PendingIntent如果已经存在，那么它们都会被cancel，然后系统会创建一个新的PendingIntent
对于通知栏消息来说，那些被cancel的消息单击后将无法打开

FLAG_UPDATE_CURRENT
当前描述的PendingIntent如果已经存在，那么它们都会被更新，即它们的Intent中的Extras会被替换成最新的


结合通知栏消息再描述一遍：
如下代码中：manager.notify(id, notification)

如果notify的第一个参数id是常量，那么多次调用notify只能弹出一个通知，后续的通知会把前面的通知完全替代掉
如果每次id都不同，那么多次调用notify会弹出多个通知

如果notify方法的id是常量，那么不管PendingIntent是否匹配，后续通知会替代前面的通知
如果notify方法的id每次都不同
那么当PendingIntent不匹配时，在这种情况下采用何种标记位，这些通知之间都不会互相干扰；
如果PendingIntent处于匹配状态时，要分情况讨论：
- 如果采用了FLAG_ONE_SHOT标记位，那么后续通知中的PendingIntent会和第一条通知保持完全一致，
包括其中的Extras，单击任何一条通知后，剩下的通知均无法再打开，当所有的通知都被清除后，会再次重复这个过程

- 如果采用了FLAG_CANCEL_CURRENT标记位，那么只有最新的通知可以打开，之前弹出的所有通知均无法打开

- 如果采用了FLAG_UPDATE_CURRENT标记位，那么之前弹出的通知中的PendingIntent会被更新，
最终它们和最新的一条通知保持完全一致，包括其中的Extras，并且这些通知都是可以打开的


## 2. RemoteViews的内部机制

首先看一下最常用的构造方法：
```
public RemoteViews(String packageName, int layoutId)
```

packageName：当前应用的包名
layoutId：待加载的布局文件

RemoteViews目前并不能支持所有的View类型，它支持的所有类型如下：
Layout: FrameLayout、LinearLayout、RelativeLayout、GridLayout

View: AnalogClock、Button、Chronometer、ImageButton、ImageView、ProgressBar、TextView、
ViewFlipper、ListView、GridView、StackView、AdapterViewFlipper、ViewStub

上面是RemoteViews所支持的所有View类型
RemoteViews不支持它们的子类以及其他View类型（也就是说无法使用除上述列表以外的View，也无法使用自定义View）

比如在通知栏的RemoteViews中使用系统的EditText，通知栏消息将无法弹出并抛出如下异常：
```
E/StatusBar(765): Could not inflate view for notification com.ryg.chapter_5/0x2
E/StatusBar(765): android.view.InflateException: Binary XML file line #25: 
Error inflating Class android.widget.EditText
E/StatusBar(765): Caused by: android.view.InflateException: Binary XML file line #25:
Class not allow to be inflated android.widget.EditText
...
```

RemoteViews没有提供findViewById方法，必须通过一系列set方法来更新元素
一些常用的set方法：
```
// 设置TextView的文本
setTextViewText(int viewId, CharSequence text)
// 设置TextView的字体大小
setTextViewTextSize(int viewId, int units, float size)
// 设置TextView的字体颜色
setTextColor(int viewId, int color)
// 设置ImageView的图片资源
setImageViewResource(int viewId, int srcId)
// 设置ImageView的图片
setImageViewResource
// 反射调用View对象的参数类型为int的方法
setInt(int viewId, String methodName, int value)
// 反射调用View对象的参数类型为long的方法
setLong(int viewId, String methodName, long value)
// 反射调用View对象的参数类型为boolean的方法
setBoolean(int viewId, String methodName, boolean value)
// 为View添加单击事件，事件类型只能为PendingIntent
setOnClickPendingIntent(int viewId, PendingIntent pendingIntent)
```


通知栏和桌面小部件分别由NotificationManager和AppWidgetManager管理
NotificationManager通过Binder和SystemServer进程中的NotificationManagerService通信
AppWidgetManager通过Binder和SystemServer进程中的AppWidgetService通信

由此可见，通知栏和桌面小部件中的布局文件实际上是在NotificationManagerService和AppWidgetService中被加载的
而它们运行在系统的SystemServer中，这就和我们的进程构成了跨进程通信的场景


-> 首先，RemoteViews会通过Binder传递到SystemServer进程，因为RemoteViews实现了Parcelable接口，
因此它可以跨进程传输，系统会根据RemoteViews中的包名等信息去得到该应用的资源
-> 然后，会通过LayoutInflater去加载RemoteViews中的布局文件。
在SystemServer进程中加载后的布局文件是一个普通的View而已，只不过相对于我们的进程它是一个RemoteViews
-> 接着，系统会对View执行一系列界面更新任务，这些任务就是我们通过set方法来提交的
set方法对View所做的更新并不是立刻执行的，在RemoteViews内部会记录所有的更新操作，具体的执行时机要等到RemoteViews被加载后才能执行
这样RemoteViews就可以在SystemServer进程中显示了，这就是我们所看到的通知栏信息或桌面小部件
-> 需要更新RemoteViews时，我们需要调用一系列set方法并通过NotificationManager和AppWidgetManager来提交更新任务，
具体的更新操作也是在SystemServer进程中完成的


 




```
// RemoteViews
public void setTextViewText(int viewId, CharSequence text) {
	setCharSequence(viewId, "setText", text);
}

public void setCharSequence(int viewId, String methodName, CharSequence value) {
	addAction(new ReflectionAction(viewId, methodName, ReflectionAction.CHAR_SEQUENCE), value);
}

private void addAction(Action a) {
	...
	if(mActions == null) {
		mActions = new ArrayList<Action>();
	}
	mActions.add(a);
	// update the memory usage stats
	a.updateMemoryUsageEstimate(mMemoryUsageCounter);
}
```

```
// RemoteViews#apply()
public View apply(Context context, ViewGroup parent, OnClickHandler handler) {
	RemoteViews rvToApply = getRemoteViewsToApply(context);

	View result;
	...

	LayoutInflater inflater = (LayoutInflater) context.getSystemService(
			Context.LAYOUT_INFLATER_SERVICE);

	// Clone inflater so we load resource from correct context and 
	// we don't add a filter to the static version returned by getSystemService.
	inflater = inflater.cloneInContext(inflationContext);
	inflater.setFilter(this);
	result = inflater.inflate(rvToAplly.getLayoutId(), parent, false);

	rvToAplly.performApply(result, parent, handler);

	return result;
}
```
上面的代码中，首先会通过LayoutInflater去加载RemoteViews中的布局文件
（RemoteViews中的布局文件可以通过getLayoutId这个方法获得）
加载完成布局文件后会通过performApply去执行一些更新操作，代码如下：

```
// RemoteViews#performApply
private void performApply(View v, ViewGroup parent, OnClickHandler handler) {
	if(mActions != null) {
		handler = handler == null ? DEFAULT_ON_CLICK_HANDLER : handler;
		final int count = mActions.size();
		for(int i = 0; i < count; ++i) {
			Action a = mActions.get(i);
			a.apply(v, parent, handler);
		}
	}
}
```
performApply的作用就是遍历mActions这个列表并执行每个Action对象的apply方法


RemoteViews在通知栏和桌面小部件中的工作过程和上面描述的过程是一致的
当我们调用RemoteViews的set方法时，并不会立刻更新它们的界面
必须要通过NotificationManager的notify方法或AppWidgetManager的updateAppWidget方法才能更新它们的界面

实际上在AppWidgetManager的updateAppWidget的内部实现中
它们的确是通过RemoteViews的**apply**以及**reapply**方法来加载或者更新界面的

**apply和reapply的区别在于：**
apply会加载布局并更新界面；而reapply只会更新界面
通知栏和桌面小部件在初始化界面时会调用apply方法，而在后续的更新界面时则会调用reapply方法

```
private final class ReflectionAction extends Action {

	ReflectionAction(int viewId, String methodName, int type, Object value) {
		this.viewId = viewId;
		this.methodName = methodName;
		this.type = type;
		this.value = value;
	}

	...

	@Override
	public void apply(View root, ViewGroup rootParent, OnClickHandler handler) {
		final View view = root.findViewById(viewId);
		if(view == null)
			return;

		Class<?> param = getParameterType();
		if(param == null) {
			throw new ActionException("bad type: " + this.type);
		}

		try {
			getMethod(view, this.methodName, param).invoke(view, wrapArg(this.value)); 
		} catch (ActionException e) {
			throw e;
		} catch (Exception ex) {
			throw new ActionException(ex);
		}
	}
}
```

ReflectionAction表示一个反射动作，通过它对View的操作会以反射的方式来调用
其中getMethod就是根据方法名来得到反射所需的Method对象

使用ReflectionAction的set方法有：setTextViewText、setBoolean、setLong、setDouble等

除了ReflectionAction，还有其他Action
如：TextViewSizeAction、ViewPaddingAction、SetOnClickPendingIntent

这里分析一下TextViewSizeAction：
```
private class TextViewSizeAction extends Action {

	public TextViewSizeAction(int viewId, int units, float size) {
		this.viewId = viewId;
		this.units = units;
		this.size = size;
	}

	...

	@Override 
	public void apply(View root, ViewGroup rootParent, OnClickHandler handler) {
		final TextView target = (TextView) root.findViewById(viewId);
		if(target == null)
			return;
		target.setTextSize(units, size);
	}

	public String getActionName() {
		return "TextViewSizeAction";
	}

	int units;
	float size;

	public final static int TAG = 13;
}
```
TextViewSizeAction的实现比较简单
之所以不用反射来实现，是因为setTextSize这个方法有2个参数，无法复用ReflectionAction




## 3. RemoteViews的意义

**本节将打造一个模拟的通知栏效果并实现跨进程的UI更新**

首先有2个Activity分别运行在不同的进程中，一个名字叫A，一个名字叫B
其中A扮演者模拟通知栏的角色，而B则可以不停地发送通知栏消息

我们在B中创建RemoteViews对象，然后通知A显示这个RemoteViews对象

如何通知A显示B中的RemoteViews呢？我们可以像系统一样采用Binder来实现，但是这里为了简单起见采用了广播。
B每发送一次模拟通知，就会发送一个特定的广播，然后A接收到广播后就开始显示B中定义的RemoteViews对象
这个过程和系统的通知栏消息的显示过程几乎一致


首先看B的实现，B只要构造RemoteViews对象并传输给A即可：
（这一过程通知栏是用Binder实现的，本例中采用广播实现）
```
RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.layout_simulated_notification);
remoteViews.setTextViewText(R.id.msg, "msg from process:" + Process.myPid());
remoteViews.setImageViewResource(R.id.icon, R.drawable.icon1);
PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
		new Intent(this, DemoActivity_1.class), PendingIntent.FLAG_UPDATE_CURRENT});
PendingIntent openActivity2PendingIntent = PendingIntent.getActivity(this, 0,
		new Intent(this, DemoActivity_2.class), PendingIntent.FLAG_UPDATE_CURRENT);
remoteViews.setOnClickPendingIntent(R.id.item_holder, pendingIntent);
remoteViews.setOnClickPendingIntent(R.id.open_activity2, openActivity2PendingIntent);
Intent intent = new Intent(MyConstants.REMOTE_ACTION);
intent.putExtra(MyConstants.EXTRA_REMOTE_VIEWS, remoteViews);
sendBroadcast(intent);
```

A的代码也很简单，只需要接收B中的广播并显示RemoteViews即可：
```
public class MainActivity extends Activity {
	
	private staitc final String TAG = "MainActivity";

	private LinearLayout mRemoteViewsContent;

	private BroadcastReceiver mRemoteViewsReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			RemoteViews remoteViews = intent.getParcelableExtra(MyConstants.EXTRA_REMOTE_VIEWS);
			if(remoteViews != null) {
				updateUI(remoteViews);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initView();
	}

	private void initView() {
		mRemoteViewsContent = (LinearLayout) findViewById(R.id.remote_views_content);
		IntentFilter filter = new IntentFilter(MyConstants.REMOTE_ACTION);
		registerReceiver(mRemoteViewsReceiver, filter);
	}

	private void updateUI(RemoteViews remoteViews) {
		View view = remoteViews.apply(this, mRemoteViewsContent);
		mRemoteViewsContent.addView(view);
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(mRemoteViewsReceiver);
		super.onDestroy();
	}
}
```

上述的主要逻辑其实就是updateUI方法。
当A收到广播后，会从Intent中取出RemoteViews对象，然后通过它的apply方法加载布局文件并执行更新操作，
最后将得到的View添加到A的布局中即可。（通知栏的底层就是这么实现的）


**使用RemoteViews和AIDL比较：**
如果一个应用需要能够更新另外一个应用的某个界面
如果对界面的更新比较频繁，这个使用AIDL就会有效率问题，同时AIDL接口就有可能变得很复杂；
这个时候如果采用RemoteViews来实现就没有这个问题了。
使用RemoteViews也有缺点，就是仅支持一些常见的View，对于自定义View是不支持的。

所以使用AIDL还是RemoteViews要看具体情况
如果界面中的View都是一些简单的且被RemoteViews支持的View，那么可以考虑采用RemoteViews，否则就不适合用RemoteViews了


在上面的代码中
```
View view = remoteViews.apply(this, mRemoteViewsContent);
mRemoteViewsContent.addView(view);
```
这种写法在同一个应用的多进程情况下是适用的，但是如果在不同应用中，
那么B中的布局文件的资源id传输到A中以后很有可能是无效的，因为A中的这个布局文件的资源id不可能刚好和B中的资源id一样

面对这种情况，这里给出一种方法（这个方法的具体实现细节下次回顾的时候再写）
代码如下：
```
layout.addView(remoteViews.apply(this, layout));
```




