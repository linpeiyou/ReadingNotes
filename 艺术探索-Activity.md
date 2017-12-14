# Activity的生命周期和启动模式

本章分析Activity在使用过程中的一些不容易搞清楚的概念
主要包括：生命周期、启动模式、IntentFilter的匹配规则

Activity在异常情况下的生命周期是十分微妙的
Activity的启动模式和形形色色的Flags也很复杂
隐式启动Activity中也有着复杂的Intent匹配过程

## 1. Activity的生命周期全面分析

典型情况下的生命周期：指在有用户参与的情况下，Activity所经过的生命周期的改变
异常情况下的生命周期：Activity被系统回收或者由于当前设备的Configuration发生改变从而导致Activity被销毁重建

异常情况下的生命周期的关注点和典型情况下略有不同

### 1.1 典型情况下的生命周期分析

(1) onCreate：表示Activity正在被创建

(2) onRestart：表示Activity正在重新启动

一般情况下，当当前Activity从不可见重新变为可见状态时，onRestart就会被调用。
这种情形一般是用户行为所导致的，比如用户按Home键切换到桌面或者用户打开了一个新的Activity，
这时当前的Activity就会暂停，也就是onPause和onStop被执行了，接着用户又回到这个Activity，就会执行onRestart。

(3) onStart：表示Activity正在被启动

这时Activity已经可见了，但是还没有出现在前台，还无法和用户交互。
这个时候其实可以理解为Activity已经显示出来了，但是我们还看不到。

(4) onResume：表示Activity已经可见了，并且出现在前台并开始活动

和onStart的对比：onStart和onResume都表示Activity已经可见，
但是onStart的时候Activity还在后台，onResume的时候Activity才显示到前台

(5) onPause：表示Activity正在停止

正常情况下，紧接着onStop就会被调用
在特殊情况下，如果这个时候快速地再回到当前Activity，那么onResume会被调用。
不过这是极端情况，用户操作很难重现这一场景。

在onPause的时候可以做一些存储数据、停止动画等工作，但是不能太耗时，隐藏这会影响到新Activity的显示
onPause必须先执行完，新Activity的onResume才会执行。

(6) onStop：表示Activity即将停止

可以做一些稍微重量级的回收工作，同样不能太耗时

(7) onDestroy：表示Activity即将被销毁

这是Activity生命周期中的最后一个回调，在这里可以做一些回收工作和最终的资源释放


#### 注意点：
(1) 针对一个特定的Activity，第一次启动，回调：onCreate -> onStart -> onResume

(2) 当用户打开新的Activity或者切换到桌面，回调：onPause -> onStop
这里有一种特殊情况，如果新Activity采用了透明主题，那么当前Activity不会回调onStop。

(3) 当用户再次回到原Activity时，回调：onRestart -> onStart -> onResume

(4) 当用户按back键回退时，回调：onPause -> onStop -> onDestroy

(5) 当Activity被系统回收后再次打开，生命周期方法回调过程和(1)一样
注意这里只是生命周期方法调用一样，但是过程会有不一样的地方

(6) 从整个生命周期来说，onCreate和onDestroy是配对的，分别标识着Activity的创建和销毁，并且只可能有一次调用
从Activity是否可见来说，onStart和onStop是配对的，随着用户的操作或者设备屏幕的点亮和熄灭，这两个方法可能被调用多次
从Activity是否在前台来说，onResume和onPause是配对的，随着用户操作或者设备屏幕的点亮和熄灭，这两个方法可能被调用多次

#### 两个问题：
1.onStart和onResume、onPause和onStop有什么实质性的不同？
onStart和onStop是从Activity是否可见这个角度来回调的
onResume和onPause是从Activity是否位于前台这个角度来回调的

2.从Activity A 打开Activity B，那么A的onPause先执行还是B的onResume？
可以从Android源码里得到解释，涉及到Instrumentation，ActivityThread和ActivityManagerService（简称AMS）。
启动Activity的请求会由Instrumentation来处理，然后它通过Binder向AMS发请求，
AMS内部维护着一个ActivityStack并负责栈内的Activity的状态同步，
AMS通过ActivityThread去同步Activity的状态从而完成生命周期方法的调用。

在ActivityStack中的resumeTopActivityInnerLocked方法中，有这么一段代码：
```
// We need to start pausing the current activity so the top one can be resumed...
boolean dontWaitForPause = (next.info.flags & ActivityInfo.FLAG_RESUME_WHILE_PAUSING) != 0;
boolean pausing = mStackSupervisor.pauseBackStacks(userLeaving, true, dontWaitForPause);
if(mResumedActivity != null) {
	pausing != startPausingLocked(userLeaving, false, true, dontWaitForPause);
	if(DEBUG_STATES)
		Slog.d(TAG, "resumeTopActivityLocked: Pausing " + mResumedActivity);
}

```

从上述代码可以看出，在新Activity启动之前，栈顶的Activity需要先onPause后，新Activity才能启动。
最终在ActivityStackSupervisor中的realStartActivityLocked方法会调用如下代码：
```
app.thread.scheduleLaunchActivity(new Intent(r.intent), r.appToken,
		System.identityHashCode(r), r.info, new Configuration(mService.mConfiguration),
		r.compat, r.task.voiceInteractor, app.repProcState, r.icicle, r.persistentState,
		results, newIntents, !andResume, mService.isNextTransitionForward(), profilerInfo);

```

这个app.thread的类型是IApplicationThread，
而IApplicationThread的具体实现是ActivityThread中的ApplicationThread。

所以，这段代码实际上调用到了ActivityThread，即ApplicationThread的scheduleLaunchActivity方法，
而sheduleLaunchActivity方法最终会完成新Activity的onCreate、onStart、onResume的调用过程

因此，是旧的Activity先onPause，然后新的Activity在执行onResume


ApplicationThread的scheduleLaunchActivity方法最终会调handleLaunchActivity方法，
handleLaunchActivity方法会完成onCreate、onStart、onResume的调用过程：
```
private void handleLaunchActivity(ActivityClientRecord r, Intent customIntent) {
	// If we are getting ready to gc after going to the background,
	// well we are back active so skip it.
	unsheduleGcIdler();
	mSomeActivitiesChanged = true;

	if(r.profilerInfo != null) {
		mProfiler.setProfiler(r.profilerInfo);
		mProfiler.startProfiling();
	}

	// Make sure we are running with the most recent config.
	handleConfigurationChanged(null, null);

	if(localLOGV)
		Slog.v(TAG, "Handling lauch of " + r);

	// 这里新Activity被创建处理，其onCreate和onStart会被调用
	Activity a = performLaunchActivity(r, customIntent);

	if(a != null) {
		r.createdConfig = new Configuration(mConfiguration);
		Bundle oldState = r.state;
		// 这里新的Activity的onResume会被调用
		handleResumeActivity(r.token, false, r.isForward,
				!r.activity.mFinished && !r.startsNotResumed);
		// 省略
	}
}
```


### 1.2 异常情况下的生命周期分析
有一些异常情况，比如：资源相关的系统配置发生改变、系统内存不足
在这些异常情况下，Activity就可能被杀死

下面具体分析这两种情况：
#### 情况1：资源相关的系统配置发生改变导致Activity被杀死并重新创建

理解这个问题，首先要对系统的资源加载机制有一定了解，这里简单说明一下系统的资源加载机制：
比如我们把一张图片放在drawable目录后，就可以通过Resources去获取这张图片。
同时为了兼容不同的设备，我们可能还需要在其他一些目录下放置不同的图片，
比如drawable-mdpi, drawale-hdpi, drawable-land等

这样，当应用程序启动时，系统就会根据当前设备的情况去加载合适的Resources资源，
**比如说横屏手机和竖屏手机会拿到两张不同的图片（设定了landscape或者portrait状态下的图片）**
**比如说当前Activity处于竖屏状态，如果突然旋转屏幕，**
由于系统配置发生了改变，在默认情况下，Activity就会被销毁并且重新创建，
当然我们也可以阻止系统重新创建我们的Activity


在默认情况下，如果我们的Activity不做特殊处理，
那么当系统配置发生改变后，Activity会被销毁，onPause, onStop, onDestroy均会被调用
同时由于Activity是在异常情况下终止的，系统会调用onSaveInstanceState来保存当前Activity的状态。

这个方法的调用时机在onStop之前，可能在onPause之前或之后。
需要强调的是：这个方法只会出现在Activity被异常终止的情况下，正常情况下系统不会回调这个方法。

当Activity被重新创建后，系统会调用onRestoreInstanceState，
并把Activity销毁时onSaveInstanceState方法所保存的Bundle对象作为参数同时传递给onRestoreInstanceState和onCreate方法

因此我们可以通过onRestoreInstanceState和onCreate方法来判断Activity是否被重建了，
如果被重建了，那么我们可以取出之前保存的数据并恢复，从时序上来说，onRestoreInstanceState的调用时机在onStart之后


同时，在onSaveInstanceState和onRestoreInstanceState方法中，系统自动为我们做了一定的恢复工作。
当Activity在异常情况下需要重新创建时，系统会默认为我们保存当前Activity的视图结构，并且在Activity重启后为我们恢复这些数据，
比如文本框中用户输入的数据、ListView滚动的位置等，这些View相关的状态系统都能够默认为我们恢复。

具体针对某一个特定的View系统能够为我们恢复哪些数据，我们可以查看View的源码。
和Activity一样，每个View都有onSaveInstanceState和onRestoreInstanceState这两个方法，
从具体实现中就能够看出系统自动为View恢复了哪些数据。


关于保存和恢复View层次结构，系统的工作流程：
1.首先Activity被意外终止时，Activity会调用onSaveInstanceState去保存数据
2.然后Activity会委托Window去保存数据
3.接着Window再委托它上面的顶级容器去保存数据（顶级容器是一个ViewGroup，一般来说它很可能是DecorView）
4.最后顶级容器再去一一通知它的子元素来保存数据，这样整个数据保存过程就完成了

这里分析一下TextView的源码：TextView#onSaveInstanceState
```
@Override
public Parcelable onSaveInstanceState() {
	Parcelable superState = super.onSaveInstanceState();

	// Save state if we are forced to
	boolean save = mFreezesText;
	int start = 0;
	int end = 0;

	if(mText != null) {
		start = getSelectionStart();
		end = getSelectionEnd();
		if(start >= 0 || end >= 0) {
			// Or save state if there is a selection
			save = true;
		}
	}

	if(save) {
		SavedState ss = new SavedState(superState);
		// XXX Should also save the current scroll position!
		ss.selStart = start;
		ss.selEnd = end;

		if(mText instanceof Spanned) {
			Spannable sp = new SpannableStringBuilder(mText);

			if(mEditor != null) {
				removeMisspelledSpans(sp);
				sp.removeSpan(mEditor.mSuggestionRangeSpan);
			}

			ss.text = sp;
		} else {
			ss.text = mText.toString();
		}

		if(isFocused() && start >= 0 && end >= 0) {
			ss.frozenWithFocus = true;
		}

		ss.error = getError();

		return ss;
	}

	return superState;
}
```
从上述源码可以看出，TextView保存了自己的文本选中状态和文本内容，
在onRestoreInstanceState方法的源码中恢复了这些数据


针对onSaveInstanceState方法还有一点需要说明：
**系统只有在Activity异常终止的时候才会调用onSaveInstanceState和onRestoreInstanceState来存储和恢复数据，**
**其他情况下不会触发这个过程。**


#### 情况2：资源内存不足导致低优先级的Activity被杀死

这种情况不好模拟，但是其数据存储和恢复过程和情况1完全一致。
Activity按照优先级从高到低，可以分为如下三种：
(1) 前台Activity————正在和用户交互的Activity，优先级最高
(2) 可见但非前台Activity————比如Activity中弹出了一个对话框，导致Activity可见但是位于后台无法和用户直接交互
(3) 后台Activity————已经被暂停的Activity，比如执行了onStop，优先级最低

当系统内存不足时，系统就会按照上述优先级去杀死目标Activity所在的进程，
并在后续通过onSaveInstanceState和onRestoreInstanceState来存储和恢复数据。

如果一个进程中没有四大组件在执行，那么这个进程将很快被系统杀死。
因此，一些后台工作不适合脱离四大组件而独自运行在后台中，这样进程很容易被杀死。
比较好的方法是将后台工作放入Service中从而保证进程有一定的优先级，这样就不会轻易地被系统杀死。


情况1和情况2都分析完了。

我们知道当系统配置发生改变后，Activity会被重新创建
**如果当某项内容发生改变后，我们不想系统重新创建Activity，可以给Activity指定configChanges属性**
比如不想让Activity在屏幕旋转的时候重新创建，就可以给configChanges属性添加orientation这个值：
android:configChanges="orientation"

如果要指定多个值，可以用“|”连接，比如android:configChanges="orientation|keyboardHidden"
系统配置中所含的项目非常多，在书中的表格里介绍了每个项目的含义。

如果我们没有在Activity的configChanges属性中指定改选项的话，当配置发生改变后就会导致Activity重新创建。
我们常用的有：locale、orientation、keyboardHidden这三个选项，其他很少使用。

需要注意的是screenSize和smallestScreenSize，这两个比较特殊，行为和编译选项有关！与运行环境无关！


## 2. Activity的启动模式

### 2.1 Activity的LaunchMode

当我们多次启动同一个Activity的时候，系统会创建多个实例并把它们一一放入任务栈中，
当我们点击back键，这些Activity会一一回退



(1) standard：标准模式，这也是系统的默认模式。

当我们用ApplicationContext去启动standard模式的Activity的时候会报错：
```
E/AndroidRuntime(674): android.util.AndroidRuntimeException: Calling startActivity 
from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag.
Is this really what you want?
```
因为standard模式的Activity默认会进入启动它的Activity所属的任务栈中，
但是由于非Activity类型的Context（如ApplicationContext）并没有所谓的任务栈，所以出现该问题

解决这个问题的办法就是为待启动的Activity指定FLAG_ACTIVITY_NEW_TASK标记位


(2) singleTop：栈顶复用模式。

这种模式下，如果新Activity已经位于任务栈的栈顶，那么此Activity不会被重新创建
同时它的onNewIntent方法会被调用，通过此方法的参数我们可以取出当前请求的信息

需要注意的是，这个Activity的onCreate、onStart方法不会被系统调用，因为它并没有发生改变

如果新Activity的实例已存在但是不是位于栈顶，那么新Activity仍然会重新创建


(3) singleTask：栈内复用模式。

只要Activity在一个栈中存在，那么多次启动此Activity都不会重新创建实例，和singleTop一样，系统也会回调其onNewIntent

当一个具有singleTask模式的Activity请求启动后，如：ActivityA
-> 系统首先会寻找是否存在A想要的任务栈
-> 如果不存在，就重新创建一个任务栈，然后创建A的实例后把A放到栈中
-> 如果存在A想要的任务栈，这时候要看A是否在栈中有实例存在
-> 	如果有实例存在，那么系统就会把A调到栈顶并调用它的onNewIntent方法，同时clearTop
-> 	如果没有实例存在，那么久创建A的实例并把A压入栈中

一些情况：
- 情况1：比如当前任务栈S1中的情况为ABC，这时候ActivityD以singleTask模式请求启动，其所需要的任务栈为S2
创建任务栈S2，然后再创建D的实例并将其入栈到S2
- 情况2：假设D所需要的任务栈为S1，其他情况如情况1所示
直接创建D的实例并将其入栈到S2
- 情况3：如果D所需的任务栈为S1，并且当前的任务栈S1的情况为ADBC
把D切换到栈顶并调用其onNewIntent方法，同时singleTask默认具有clearTop的效果，S1中最终为AD


(4) singleInstance：单实例模式。

一种加强的singleTask模式，具有此种模式的Activity只能单独地位于一个任务栈中

比如ActivityA是singleInstance模式，当A启动后，系统会为它创建一个新的任务栈
然后A独自在这个新的任务栈中，由于栈内复用的特性，后续的请求均不会创建新的Activity，除非这个独特的任务栈被系统销毁了

**这里书中穿插了个前台任务栈切到后台任务栈的一种情况，蛮重要**


**这里有一个问题。在singleTask中，多次提到某个Activity所需的任务栈，什么是Activity所需要的任务栈呢？**
这里要从一个参数说起：TaskAffinity，可以翻译为任务相关性，标识了一个Activity所需要的任务栈的名字
默认情况下，所有Activity所需的任务栈的名字为应用的包名
TaskAffinity属性主要和singleTask启动模式或者allowTaskReparenting属性配对使用，其他情况下没有意义
**另外，任务栈分为前台任务栈和后台任务栈**
**后台任务栈中的Activity位于暂停状态，用户可以通过切换将后台任务栈再次调到前台**

当TaskAffinity和singleTask启动模式配对使用的时候，它是具有该模式的Activity的目前任务栈的名字
待启动的Activity会运行在名字和TaskAffinity相同的任务栈中

当TaskAffinity和allowTaskReparenting结合的时候，这种情况比较复杂，会产生特殊的效果。
当一个应用A启动了应用B的某个Activity后，如果这个Activity的allowTaskReparenting属性为true的话，
那么当应用B被启动后，此Activity会直接从应用A的任务栈被转移到应用B的任务栈中。
（如应用A和B，A启动了B的一个ActivityC，此时按Home键回到桌面，然后再单击B的桌面图标，
这个时候不是启动B的主Activity，而是重新显示了已经被应用A启动的ActivityC，即C从A的任务栈转移到了B的任务栈中）


给Activity指定启动模式的两种方法：
第一种是通过AndroidManifest为Activity指定启动模式：
```
<activity
	android:name="com.ryg.chapter_1.SecondActivity"
	android:lauchMode="singleTask"
	android:taskAffinity="com.ryg.chapter_1.haha" />
```

第二种是通过在Intent中设置标志位来为Activity指定启动模式：
```
Intent intent = new Intent();
intent.setClass(MainActivity.this, SecondActivity.class);
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
startActivity(intent);
```

**两种方式的区别：**
- 优先级上，第二种方式优先级高于第一种（即两种同时存在时，以第二种方式为准）
- 第一种方式无法直接为Activity设定FLAG_ACTIVITY_CLEAR_TOP标识
- 第二种方式无法为Activity指定singleInstance模式


**通过adb shell dumpsys activity命令，在`ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)`下
的`Running activities (most recent first)`中，可以看到任务栈和任务栈中的Activity**


### 2.2 Activity的Flags

标记位的作用很多
有的标记位可以设定Activity的**启动模式**，比如FLAG_ACTIVITY_NEW_TASK和FLAG_ACTIVITY_SINGLE_TOP等
有的标记位可以影响Activity的**运行状态**，比如FLAG_ACTIVITY_CLEAR_TOP和FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS等

下面介绍几个常用的标记位：

FLAG_ACTIVITY_NEW_TASK
为Activity指定“singleTask”启动模式，效果和在XML中指定相同

FLAG_ACTIVITY_SINGLE_TOP
为Activity指定“singleTop”启动模式，效果和在XML中指定相同

FLAG_ACTIVITY_CLEAR_TOP
（singleTask启动模式默认开启此标记位）
具有此标记位的Activity，当它启动时，在同一个任务栈中的所有位于它上面的Activity都要出栈。
**这个模式一般需要和FLAG_ACTIVITY_NEW_TASK配合使用，在这种情况下被启动的Activity的实例如果已经存在，那么系统会调用它的onNewIntent。**
**如果被启动的Activity采用standard模式启动，那么它连同它之上的Activity都要出栈，系统会创建新的Activity实例并放入栈顶。**

FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
具有这个标记位的Activity不会出现在历史Activity列表中
当某些情况下我们不希望用户通过历史列表回到我们的Activity的时候这个标记位比较有用
它等同于在XML中指定Activity的属性android:excludeFromRecents="true"


## 3. IntentFilter的匹配规则

启动Activity分为两种，显式调用和隐式调用

隐式调用需要Intent能够匹配目标组件的IntentFilter中所设置的过滤信息，如果不匹配将无法启动目标Activity

IntentFilter中的过滤信息有：action、category、data，下面是一个过滤规则的demo：
```
<activity android:name="com.ryg.chapter_1.ThirdActivity" >
	<intent-filter>
		<action android:name="com.ryg.chapter_1.c"/>
		<action android:name="com.ryg.chapter_1.d"/>
		<category android:name="com.ryg.category.c"/>
		<category android:name="com.ryg.category.d"/>
		<category android:name="android.intent.category.DEFAULT"/>
		<data android:mimeType="text/plain"/>
	</intent-filter>
</activity>
```

为了匹配过滤列表，需要同时匹配过滤列表中的action、category、data信息，否则匹配失败

一个过滤列表中的action、category、data可以有多个
所有的action、category、data分别构成不同类别，同一个类别的信息共同约束当前类别的匹配过程
**只有一个Intent同时匹配action类别、category类别、data类别才算完全匹配，只有完全匹配才能成功启动目标Activity**
**另外，一个Activity中可以有多个intent-filter，一个Intent只要能匹配任何一组intent-filter即可成功启动对应的Activity**

例如：
```
<activity android:name="ShareActivity">
	<!-- This activity handles "SEND" actions with text data -->
	<intent-filter>
		<action android:name="android.intent.action.SEND"/>
		<category android:name="android.intent.category.DEFAULT"/>
		<data android:mimeType="text/plain"/>
	</intent-filter>

	<!-- This activity also handles "SEND" and "SEND_MULTIPLE" with media data -->
	<intent-filter>
		<action android:name="android.intent.action.SEND"/>
		<action android:name="android.intent.action.SEND_MULTIPLE"/>
		<category android:name="android.intent.category.DEFAULT"/>
		<data android:mimeType="application/vnd.google.panorama360+jpg"/>
		<data android:mimeType="image/*"/>
		<data android:mimeType="video/*"/>
	</intent-filter>
</activity>
```

详细分析下各种属性的匹配过程：

1.action的匹配规则
action是一个字符串，系统预定义了一些action，我们也可以定义自己的action。

action的匹配规则是Intent中的action和过滤规则中的action字符串值完全一样（区分大小写）
一个过滤规则中可以有多个action，Intent中的action只要能和过滤规则中任何一个action相同即可匹配成功

如果Intent中没有指定action，那么匹配失败

2.category的匹配规则
category是一个字符串，系统预定义了一些category，我们也可以定义自己的category。

匹配规则：
Intent中如果出现了category，那么对于每个category，它必须是过滤规则中已经定义了的category
Intent中如果没有category，这个Intent仍然可以匹配成功

为什么不设置category也可以匹配？
因为系统在调用startActivity或者startActivityForResult时会默认为Intent加上"android.intent.category.DEFAULT"这个category
同时，为了我们的activity能够接收隐式调用，就必须intent-filter中指定"android.intent.category.DEFAULT"这个category
所以这两个就匹配上了

3.data的匹配规则
data的匹配规则和action类似，如果过滤规则中定义了data，那么Intent中必须也要定义可匹配的data

data的结构比较复杂，先了解一下data的结构
data的语法如下：
```
<data anroid:scheme="string"
	android:host="string"
	android:port="string"
	android:path="string"
	android:pathPattern="string"
	android:pathPrefix="string"
	android:mimeType="string" />
```

data由两部分组成：mimeType、URI
mimeType指媒体类型，比如image/jpeg、audio/mpeg4-generic、video/* 等可以表示图片、文本、视频等不同的媒体格式
URI中包含的数据比较多

下面是URI的结构：
`<scheme>://<host>:<port>/[<path>|<pathPrefix>|<pathPattern>]`
比如：
```
content://com.example.project:200/folder/subfolder/etc
http://www.baidu.com:80/search/info
```

scheme：URI的模式，如http、file、content等
如果URI中没有指定scheme，那么整个URI的其他参数无效，也意味着URI无效

host：URI的主机名，比如www.baidu.com
如果host未指定，那么整个URI的其他参数无效，也意味着URI无效

port：URI中的端口号，比如80
仅当URI中指定了scheme和host参数的时候port参数才是有意义的 

path、pathPattern、pathPrefix：路径信息
其中path表示完整的路径信息；
pathPattern也表示完整的路径信息，但是它里面可以包含通配符"*"，"*"表示0个或多个任意字符；
pathPrefix表示路径的前缀信息

**分情况说明data的匹配规则：**
（1）如下过滤规则：
```
<intent-filter>
	<data android:mimeType="image/*"/>
	...
</intent-filter>
```

这种规则指定了媒体类型为所有类型的图片，那么Intent中的mimeType属性必须为"image/* "才能匹配
**这种情况下虽然过滤规则没有指定URI，但是却有默认值，URI的默认值为content和file。**
也就是说，虽然没有指定URI，但是Intent中的URI部分的scheme必须为content或file才能匹配
为了匹配（1）中的规则，我们可以写出如下示例：
`intent.setDataAndType(Uri.parse("file://abc"), "image/png");`

**另外，如果要为Intent指定完整的data，必须要调用setDataAndType方法，不能分别调用setData和setType**
因为这两个方法会彼此清除对方的值，如setData：
```
public Intent setData(Uri data) {
	mData = data;
	mType = null;
	return this;
}
```
setData会把mimeType设置为null；同理setType会把URI设置为null


（2）如下过滤规则：
```
<intent-filter>
	<data android:mimeType="video/mpeg" android:scheme="http" .../>
	<data android:mimeType="audio/mpeg" android:scheme="http" .../>
	...
</intent-filter>
```

这种规则指定了两组data规则，并且每个data都指定了完整的属性值，有URI也有mimeType
为了匹配（1）中的规则，我们可以写出如下示例：
`intent.setDataAndType(Uri.parse("http://abc"), "video/mpeg")`
或者
`intent.setDataAndType(Uri.parse("http://abc"), "audio/mpeg")`


关于data还有一个特殊情况：如下两种写法，它们的作用是一样的：
```
<!-- 写法1 -->
<intent-filter ... >
	<data android:scheme="file" android:host="www.baidu.com" />
	...
</intent-filter>

<!-- 写法2 -->
<intent-filter ... >
	<data android:scheme="file" />
	<data android:host="www.baidu.com" />
	...
</intent-filter>
```

intent-filter的匹配规则对于Service和BroadcastReceiver也是同样的道理
不过系统对于Service的建议是尽量使用显式调用方式来启动服务


当我们通过隐式方式启动一个Activity的时候，可以做一下判断，看是否有Activity能够匹配我们的隐式Intent
如果不做判断就有可能直接发生crash

判断方法有两种：采用PackageManager的resolveActivity方法或者Intent的resolveActivity方法
它们返回最佳匹配的Activity信息，如果它们找不到匹配的Activity就会返回null

另外PackageManager还提供了queryIntentActivities方法，它返回所有匹配的Activity信息

（针对Service和BroadcastReceiver，PackageManager同样提供了类似的方法去获取成功匹配的组件信息）

```
public abstract List<ResolveInfo> queryIntentActivities(Intent intent, int flags);
public abstract ResolveInfo resolveActivity(Intent intent, int flags);
```

第二个参数我们要使用MATCH_DEFAULT_ONLY这个标记位
这个标记位的含义是仅仅匹配那些在intent-filter中声明了
`<category android:name="android.intent.category.DEFAULT"/>`这个category的Activity

如果不使用这个标记位，就可以把intent-filter中category不含DEFAULT的那些Activity匹配出来，
从而导致startActivity可能失败，因为不含DEFAULT这个category的Activity是无法接收隐式Intent的


在action和category中，有一类比较重要：
```
<action android:name="android.intent.action.DEFAULT"/>
<category android:name="android.intent.category.LAUNCHER"/>
```
**这二者共同作用是标明这是一个入口Activity并且会出现在系统的应用列表中，少了任何一个都没有实际意义**




