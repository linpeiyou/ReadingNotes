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

(5) 当Activity被系统回收后再次打开，声明周期方法回调过程和(1)一样
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
系统只有在Activity异常终止的时候才会调用onSaveInstanceState和onRestoreInstanceState来存储和恢复数据，
其他情况下不会触发这个过程。


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
如果当某项内容发生改变后，我们不想系统重新创建Activity，可以给Activity指定configChanges属性
比如不想让Activity在屏幕旋转的时候重新创建，就可以给configChanges属性添加orientation这个值：
android:configChanges="orientation"

如果要指定多个值，可以用“|”连接，比如android:configChanges="orientation|keyboardHidden"
系统配置中所含的项目非常多，在书中的表格里介绍了每个项目的含义。

如果我们没有在Activity的configChanges属性中指定改选项的话，当配置发生改变后就会导致Activity重新创建。
我们常用的有：locale、orientation、keyboardHidden这三个选项，其他很少使用。

需要注意的是screenSize和smallestScreenSize，这两个比较特殊，行为和编译选项有关！与运行环境无关！


## 2. Activity的启动模式

### 2.1 Activity的LaunchMode

(1) standard：标准模式，这也是系统的默认模式。

(2) singleTop：栈顶复用模式。

(3) singleTask：栈内复用模式。

(4) singleInstance：单实例模式。


### 2.2 Activity的Flags




## 3. IntentFilter的匹配规则


















































