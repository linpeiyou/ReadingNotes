# View的事件体系

关键词：View的事件分发机制、滑动冲突


## 1. View基础知识

主要介绍的内容：
View的位置参数、MotionEvent、TouchSlop、VelocityTracker、GestureDetector、Scroller

### 1.1 什么是View
View是Android中所有控件的基类

### 1.2 View的位置参数
View的位置主要由它的四个顶点来决定，分别对应于View的四个属性：top, left, right, bottom
**注意：这些坐标都是相对于View的父容器来说的，它是相对坐标**

在Android中，x轴和y轴的正方向分别为右和下。所以View的宽高和坐标系的关系为：
width = right - left
height = bottom - top

**View的这四个参数在源码中对应于mLeft, mRight, mTop, mBottom这四个成员变量**
获取方式如下所示：
```
left = getLeft();
right = getRight();
top = getTop();
bottom = getBottom();
```


**从Android3.0开始，View增加了几个参数：x, y, translationX, translationY**
**其中x、y是View的左上角坐标；translationX、translationY是View左上角相对于父容器的偏移量**

所以translationX, translationY的默认值为0
这几个参数的换算关系如下：
```
x = left + translationX
y = top + translationY
```
在View平移的过程中，top和left表示的是原始左上角的位置信息，其值并不会发生改变
此时发生改变的是x、y、translationX、translationY


### 1.3 MotionEvent、TouchSlop

1.MotionEvent
手指接触屏幕后产生的一系列事件中，典型的事件类型：
- ACTION_DOWN 手指刚接触屏幕
- ACTION_MOVE 手指在屏幕上移动
- ACTION_UP 手指从屏幕上松开的一瞬间

正常情况下，一次手指触摸屏幕的行为会触发一系列点击事件，比如：
- 点击屏幕后离开松开，事件序列为：ACTION_DOWN -> ACTION_UP
- 事件屏幕滑动一会再松开，事件序列为：ACTION_DOWN -> ACTION_MOVE -> ... -> ACTION_MOVE -> ACTION_UP

通过MotionEvent对象我们可以得到点击事件发生的x和y坐标
系统提供了两组方法：getX/getY、getRawX/getRawY

getX/getY返回的是相对于当前View左上角的x和y坐标
getRawX/getRawY返回的是相对于手机屏幕左上角的x和y坐标

2.TouchSlop
TouchSlop是系统所能识别出的被认为是滑动的最小距离
当手指在屏幕上滑动时，如果两次滑动事件的距离小于这个常量，那么系统就不认为你是在进行滑动操作
可以通过ViewConfiguration.get(getContext().getScaledTouchSlop())来获取这个常量的值

在处理滑动时，可以利用这个常量来做一些过滤
比如两次滑动事件的滑动距离小于这个值，我们就可以认为它们不是滑动

在源码中可以找到这个常量的定义，在frameworks/base/core/res/res/values/config.xml文件中
```
<!-- Base "touch slop" value used by ViewConfiguration as a movement threshold 
	where scrolling should begin. -->
<dimen name="config_viewConfigurationTouchSlop">8dp</dimen>
```


### 1.4 VelocityTracker、GestrureDetector、Scroller

1.VelocityTracker
速度追踪，用于追踪手指在滑动过程中的速度，包括水平和竖直方向的速度。
使用方法：

首先，在View的onTouchEvent方法中追踪当前单击事件的速度
```
VelocityTracker velocityTracker = VelocityTracker.obtain();
velocityTracker.addMovement(event);
```

接着，当我们先知道当前的滑动速度时，这个时候可以采用如下方式来获得当前的速度
```
velocityTracker.computeCurrentVelocity(1000);
int xVelocity = (int) velocityTracker.getXVelocity();
int yVelocity = (int) velocityTracker.getYVelocity();
```

computeCurrentVelocity这个方法的参数表示的是时间间隔，单位为毫秒（ms）
**计算速度时得到的速度就是指在这个时间间隔内手指在水平或竖直方向上所滑动的像素数**

**注意三点：**
a.获取速度之前必须先计算速度，即调用getXVelocity和getYVelocity方法前必须要先调用computeCurrentVelocity
b.这里的速度是指一段时间内手指滑过的像素数
（如时间间隔为1000ms，在1000ms内，手指在水平方向上从左到右滑过100像素，那么水平速度就是100）
c.速度可以为负数

最后，当不需要使用它的时候，需要调用clear方法来重置并回收内存：
```
velocityTracker.clear();
velocityTracker.recycle();
```

2.GestureDetector
手势检测，用于辅助检测用户的单击、滑动、长按、双击等行为。
使用方法：

首先，创建一个GestureDetector对象，并实现OnGestureListener接口
根据需要我们还可以实现OnDoubleTapListener从而能够监听双击行为
```
GestureDetector mGestureDetector = new GestureDetector(this);
// 解决长按屏幕后无法拖动的现象
mGestureDetector.setIsLongpressEnabled(false);
```

接着，接管目标View的onTouchEvent方法，在待监听View的onTouchEvent方法中添加如下实现：
```
boolean consume = mGestureDetector.onTouchEvent(event);
return consume;
```

接着我们可以有选择地实现OnGestureListener和OnDoubleTapListener中的方法了

#### onGestureListener中的方法
1.onDown：手指轻轻触摸屏幕的一瞬间，由1个ACTION_DOWN触发

2.onShowPress：手指轻轻触摸屏幕，尚未松开或拖动，由1个ACTION_DOWN触发
注意：和onDown的区别，它强调的是没有松开或者拖动的状态

3.onSingleTapUp：手指（轻轻触摸屏幕后）松开，伴随着1个MotionEvent ACTION_UP而触发，这是单击行为

4.onScroll：手指按下屏幕并拖动，由一个ACTION_DOWN，多个ACTION_MOVE触发，这是拖动行为

5.onLongPress：用户长久地按着屏幕不放，即长按

6.onFling：用户按下触摸屏、快速滑动后松开，由1个ACTION_DOWN、多个ACTION_MOVE和1个ACTION_UP触发，这是快速滑动行为

#### onDoubleTapListener中的方法
1.onDoubleTap：双击，由2次连续的单击组成，它不可能和onSingleTapConfirmed共存

2.onSingleTapConfirmed：严格的单击行为
注意，它和onSingleTapUp的区别：如果触发了onSingleTapConfirmed，那么后面不可能再紧跟着另一个单击行为，
即这只可能是单击，而不可能是双击中的一次单击

3.onDoubleTapEvent：表示发生了双击行为，在双击的期间，ACTION_DOWN、ACTION_MOVE、ACTION_UP 都会触发此回调

日常开发中，常用的有：onSingleTapUp（单击）、onFling（快速滑动）、onScroll（拖动）、
onLongPress（长按）、onDoubleTap（双击）

实际开发中，可以不使用GestureDetector，完全可以自己在View的onTouchEvent方法中实现所需的监听（看个人喜好）
作者的一个建议：如果是监听滑动相关的，建议自己在onTouchEvent中实现；如果是监听双击这种行为的话，那么就使用GestureDetector



3.Scroller
弹性滑动对象，用于实现View的弹性滑动。

当使用View的scrollTo/scrollBy方法进行滑动时，其过程是瞬间完成的，滑动没有过渡效果用户体验会不好
这个时候可以使用Scroller来实现有过渡效果的滑动，其过程不是瞬间完成的，而是在一定的时间间隔内完成的

Scroller本身无法让View弹性滑动，它需要和View的computeScroll方法配合使用
代码如下：
```
Scroller mScroller = new Scroller(mContext);

// 缓慢滚动到指定位置
private void smoothScrollTo(int destX, int destY) {
	int scrollX = getScrollX();
	int delta = destX - scrollX;
	// 1000ms内滑向destX，效果就是慢慢滑动
	mScroller.startScroll(scrollX, 0, delta, 0, 1000);
	invalidate();
}

@Override
public void computeScroll() {
	if(mScroller.computeScrollOffset()) {
		scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
		postInvalidate();
	}
}
```


## 2. View的滑动 

通过三种方式可以实现View的滑动：
- 通过View本身提供的scrollTo/scrollBy方法来实现滑动
- 通过动画给View施加平移效果来实现滑动
- 通过改变View的LayoutParams使得View重新布局从而实现滑动

### 2.1 使用scrollTo/scrollBy
**重点：scrollTo和scrollBy只能改变View内容的位置而不能改变View在布局中的位置**
```
/**
 * Set the scrolled position of your view. This will cause a call to
 * {@link #onScrollChanged(int, int, int, int)} and the view will be invalidated.
 * @param x the x position to scroll to
 * @param y the y position to scroll to
 */
public void scrollTo(int x, int y) {
	if(mScrollX != x || mScrollY != y) {
		int oldX = mScrollX;
		int oldY = mScrollY;
		mScrollX = x;
		mScrollY = y;
		invalidateParentCaches();
		onScrollChanged(mScrollX, mScrollY, oldX, oldY);
		if(!awakenScrollBars()) {
			postInvalidateOnAnimation();
		}
	}
}

/**
 * Move the scrolled position of your view. This will cause a call to
 * {@link #onScrollChanged(int, int, int, int)} and the view will be invalidated.
 * @param x the amount of pixels to scroll by horizontally
 * @param y the amount of pixels to scroll by vertically
 */
public void scrollBy(int x, int y) {
	scrollTo(mScrollX + x, mScrollY + y);
}
```

scrollBy实现了基于当前位置的相对滑动；scrollTo实现了基于所传递参数的绝对滑动

**在滑动过程中，mScrollX的值总是等于View左边缘和View内容左边缘在水平方向的距离**
**mScrollY的值总是等于View上边缘和View内容上边缘在竖直方向上的距离**

View边缘是指View的位置，由4个顶点组成
View内容边缘是指View中内容的边缘，scrollTo和scrollBy只能改变View内容的位置而不能改变View在布局中的位置

mScrollX和mScrollY的单位为像素
当View左边缘在View内容左边缘的右边时，mScrollX为正值，反之为负值；
当View上边有在View内容上边缘的下边时，mScrollY为正值，反之为负值。
换句话说，如果从左向右滑动，那么mScrollX为负值，反之为正值；
如果从上到下滑动，那么mScrollY为负值，反之为正值。
即：边缘的坐标值-内容边缘的坐标值


### 2.2 使用动画
通过动画来移动View，主要是操作View的translationX和translationY属性。

可以采用传统的**View动画**，也可以采用**属性动画**
如果采用属性动画的话，为了兼容3.0以下的版本，需要采用开源动画库nineoldandroids


View动画，在100ms内将View从原值位置向右下角移动100个像素，demo：
```
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android:"http://schemas.android.com/apk/res/android"
	android:fillAfter="true"
	android:zAdjustment="normal">

	<translate
		android:duration="100"
		android:fromXDelta="0"
		android:fromYDelta="0"
		android:interpolator="@android:anim/linear_interpolator"
		android:toXDelta="100"
		android:toYDelta="100" />
</set>
```

属性动画，将View在100ms内向右平移100像素，demo：
```
ObjectAnimator.ofFloat(targetView, "translationX", 0, 100).setDuration(100).start();
```

注意：**View动画**是对View的影像做操作，并不能改变View的位置参数，包括宽/高，
并且View动画如果希望动画后的状态得以保留，必须将fillAfter属性设置为true，否则动画完成后其动画结果会消失。
**属性动画**不会存在上述问题（即可以改变位置参数，宽高，动画后的状态可以直接保留）。

在Android3.0以下无法使用属性动画；
如果在3.0以下要使用属性动画，可以使用动画兼容库nineoldandroids来实现属性动画，
（在Android3.0以下的手机上通过nineoldandroids来实现的属性动画本质上是View动画）


如果不能真正改变View的位置，会带来一个严重的问题：
比如我们通过View动画将Button平移后，点击新的Button位置无法触发onClick事件，点击原始位置才会触发。
因为Button的位置信息（四个顶点和宽高）不会随着动画而改变。

从Android3.0开始，使用属性动画可以解决这个问题
但是如果app要兼容到Android3.0以下，还是会遇到这个问题。可以间接解决这个问题：
在新位置预先创建一个和目标Button一模一样的Button（外观和onClick事件都一样），
目标Button完成平移动画后，就把目标Button隐藏，同时把预先创建的Button显示出来。
（上述办法只是一个参考-.-）


### 2.3 改变布局参数
demo:
```
MarginLayoutParams params = (MarginLayoutParams) mButton1.getLayoutParams();
params.width += 100;
params.leftMargin += 100;
mButton1.requestLayout();
// 或者mButton1.setLayoutParams(params);
```


### 2.4 各种滑动方式的对比

scrollTo/scrollBy：不影响内部元素的单击事件，缺点是只能滑动View的内容，不能滑动View本身

动画：如果是Android3.0以上使用属性动画，没有明显缺点；
如果使用View动画或者在Android3.0以下使用属性动画，均不能改变View本身的属性。

改变布局参数：没有明显缺点


## 3. 弹性滑动

思想：将一次大的滑动分成若干次小的滑动并在一个时间段内完成

实现方式：Scroller、Handler#postDelayed()、Thread#sleep()、动画等

### 3.1 使用Scroller

Scroller典型的使用方法：
```
Scroller mScroller = new Scroller(mContext);

// 缓慢滚动到指定位置
private void smoothScrollTo(int destX, int destY) {
	int scrollX = getScrollX();
	int deltaX = destX - scrollX;
	// 1000ms内滑向destX，效果就是慢慢滑动
	mScroller.startScroll(scrollX, 0, deltaX, 0, 1000);
	invalidate();
}

@Override
public void computeScroll() {
	if(mScroller.computeScrollOffset()) {
		scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
		postInvalidate();
	}
}
```

工作原理：当我们构造一个Scroller对象并且调用它的startScroll方法时，Scroller内部其实什么也没做，
它只是保存了我们传递的几个参数，startScroll源码如下：
```
public void startScroll(int startX, int startY, int dx, int dy, int duration) {
	mMode = SCROLL_MODE;
	mFinished = false;
	mDuration = duration;
	mStartTime = AnimationUtils.currentAnimationTimeMillis();
	mStartX = startX;
	mStartY = startY;
	mFinalX = startX + dx;
	mFinalY = startY + dy;
	mDeltaX = dx;
	mDeltaY = dy;
	mDurationReciprocal = 1.0f / (float) mDuration;
}
```

再次强调：这里的滑动是指View内容的滑动，不是View本身位置的改变

startScroll方法本身不能让View滑动，让View发生弹性滑动的是invalidate方法：
-> invalidate方法导致View重绘 
-> 在View的draw方法中又会去调用computeScroll方法 
-> computeScroll方法在View中是一个空实现，需要自己实现这个方法

整个详细流程是这样的：
-> View重绘后会在draw方法中调用computeScroll方法
-> computeScroll方法向Scroller获取当前的scrollX和scrollY，然后通过scrollTo方法实现滑动
-> 接着又调用postInvalidate方法来进行第二次重绘
-> 这一次重绘的过程和第一次重绘一样，还是会导致computeScroll方法被调用
-> 然后computeScroll方法向Scroller获取当前的scrollX和scrollY，然后通过scrollTo方法实现滑动
-> 如此反复直到整个滑动过程结束

看一下Scroller#computeScrollOffset()方法的实现：
```
// Scroller#computeScrollOffset()
/**
 * Call this when you want to know the new location. 
 * If it returns true, the animation is not yet finished.
 */
public boolean computeScrollOffset() {
	...
	int timePassed = (int)(AnimationUtils.currentAnimationTimeMillis() - mStartTime);

	if(timePassed < mDuration) {
		switch(mMode) {
			case SCROLL_MODE:
				final float x = mInterpolator.getInterpolation(timePassed * mDurationReciprocal);
				mCurrX = mStartX + Math.round(x * mDeltaX);
				mCurrY = mStartY + Math.round(x * mDeltaY);
				break;
			...
		}
	}
	return true;
}
```

其实就是根据时间流逝的百分比计算出mCurrX和mCurrY的值
这个方法返回true表示滑动还未结束，false则表示滑动已经结束


总结一下Scroller的工作原理：
Scroller本身不能实现View的滑动，需要配合View#computeScroll()方法实现弹性滑动
这个方法使View不断重绘，而每一次重绘距滑动起始时间会有一个时间间隔
通过这个时间间隔Scroller就可以得出View当前的滑动位置，知道了滑动位置就可以通过scrollTo方法来完成View的滑动
这样每一次重绘都会导致View进行小幅度滑动，多次小幅度滑动就组成了弹性滑动
（Scroller的设计思想很赞，它对View没有丝毫引用，甚至内部连计时器都没有）


### 3.2 使用动画

让一个View在100ms内向左移动100像素：
```
ObjectAnimator.ofFloat(targetView, "translationX", 0, 100).setDuration(100).start();
```

利用动画的特性，来实现一些动画不能实现的效果
如，模仿Scroller来实现View的弹性滑动：
```
final int startX = 0;
final int deltaX = 100;
ValueAnimator animator = ValueAnimator.ofInt(0, 1).setDuration(1000);
animator.addUpdateListener(new AnimatorUpdateListener() {
	@Override
	public void onAnimationUpdate(ValueAnimator animator) {
		float fraction = animator.getAnimatedFraction();
		mButton1.scrollTo(startX + (int)(deltaX * fraction), 0);
	}
});
```

**上面的代码中，动画本质上没有作用于任何对象上，只是在1000ms内完成了整个动画过程。**
**利用动画的特性，我们可以在动画每一帧到来时获取动画完成的比例，然后根据这个比例去执行我们想要的操作。**
比如上面的代码根据这个比例计算出当前View要滑动的距离并滑动。
**这个方法能实现很多的动画效果，我们可以在onAnimationUpdate方法中加上我们想要的其他操作。**


### 3.3 使用延时策略

核心思想：通过发送一系列延时消息从而达到一种渐进式的效果
具体来说可以通过Handler或View的postDelayed方法，也可以使用线程的sleep方法

对于postDelayed方法来说，我们可以通过它来延时发送一个消息，然后在消息中来进行View的滑动，
接连不断地发送这种延时消息，那么就可以实现弹性滑动的效果；
对于sleep方法来说，通过在while循环中不断地滑动View和sleep，就可以实现弹性滑动的效果


使用Handler，在大约1000ms内将View的内容向左移动100px，demo：
**之所以说大约1000ms，是因为这种方式无法精确地定时，因为系统的消息调度是需要时间的，并且所需时间不定**
```
private static final int MESSAGE_SCROLL_TO = 1;
private static final int FRAME_COUNT = 30;
private static final int DELAYED_TIME = 33;

private int mCount = 0;

@SuppressLint("HandlerLeak")
private Handler mHandler = new Handler() {
	public void handleMessage(Message msg) {
		switch(msg.what) {
			case MESSAGE_SCROLL_TO: {
				mCount++;
				if(mCount <= FRAME_COUNT) {
					float fraction = mCount / (float) FRAME_COUNT;
					int scrollX = (int) (fraction * 100);
					mButton1.scrollTo(scrollX, 0);
					mHandler.sendEmptyMessageDelayed(MESSAGE_SCROLL_TO, DELAYED_TIME);
				}
				break;
			}

			default:
				break;
		}
	};
};
```







## 4. View的事件分发机制

View滑动冲突的解决办法的理论基础就是事件分发机制

### 4.1 点击事件的传递规则

分析的对象是MotionEvent
所谓的点击事件的分发，其实就是对MotionEvent事件的分发过程
**当一个MotionEvent产生了以后，系统需要把这个事件传递给一个具体的View，这个传递的过程就是分发过程**

点击事件的分发过程由三个很重要的方法来共同完成：
dispatchTouchEvent、onInterceptTouchEvent、onTouchEvent


public boolean dispatchTouchEvent(MotionEvent event)
用来进行事件的分发。如果事件能够传递给当前View，那么此方法一定会被调用，
返回结果受当前View的onTouchEvent和下级View的dispatchTouchEvent方法的影响，表示是否消耗当前事件

public boolean onInterceptTouchEvent(MotionEvent event)
在上述方法内部调用，用来判断是否拦截某个事件，如果当前View拦截了某个事件，
那么在同一个事件序列中，此方法不会再被调用，返回结果表示是否拦截当前事件

public boolean onTouchEvent(MotionEvent event)
在dispatchTouchEvent方法中调用，用来处理点击事件，返回结果表示是否消耗当前事件，
如果不消耗，则在同一事件序列中，当前View无法再次接收到事件


上面三个方法的区别、关系，可以用如下伪代码表示：
```
public boolean dispatchTouchEvent(MotionEvent event) {
	boolean consume = false;
	if(onInterceptTouchEvent(event)) {
		consume = onTouchEvent(event);
	} else {
		consume = child.dispatchTouchEvent(event);
	}

	return consume;
}
```
即传递规则为：
对一个根ViewGroup来说，点击事件产生后
-> 首先会传递给它，这时它的dispatchTouchEvent会被调用
-> 如果这个ViewGroup的onInterceptTouchEvent返回false就表示它不拦截当前事件
-> 这时当前事件就会继续传递给它的子元素，接着子元素的dispatchTouchEvent就会被调用
-> 如此反复直到事件被最终处理（即onInterceptTouchEvent返回true）


当一个View需要处理事件时
-> 如果它设置了OnTouchListener，那么OnTouchListener中的onTouch方法会被回调
-> 这时事件如何处理要看onTouch的返回值，如果返回true，那么onTouchEvent方法不会被调用
-> 如果返回false，则当前View的onTouchEvent方法会被调用
-> 在onTouchEvent方法中，如果当前有设置OnClickListener，那么它的onClick方法会被调用

所以优先级：OnTouchListener#onTouch() > View#onTouchEvent() > OnClickListener#onClick()


当一个点击事件产生后，传递过程如下：Activity -> Window -> 顶级View
顶级View接收到事件后，就会按照事件分发机制去分发事件
如果一个View的onTouchEvent返回false，那么它的父容器的onTouchEvent将会被调用，以此类推
如果所有的元素都不处理这个事件，那么这个事件将会最终传递给Activity处理，即Activity的onTouchEvent方法会被调用


事件传递机制的一些结论：
(1)同一个事件序列是指从手指接触屏幕的那一刻起，到手指离开屏幕的那一刻结束
在这个过程中所产生的一系列事件，这个事件序列以down事件开始，中间含有数量不定的move事件，最终以up事件结束

(2)正常情况下，一个事件序列只能被一个View拦截且消耗
一旦一个元素拦截了某此事件，那么同一个事件序列内的所有事件都会直接交给它处理，
因此同一个事件序列中的事件不能分别由两个View同时处理，
但是通过特殊手段可以做到，比如一个View将本该自己处理的事件通过onTouchEvent强行传递给其他View处理

(3)某个View一旦决定拦截，那么这一事件序列都只能由它来处理（如果事件序列能够传递给它的话），并且它的onInterceptTouchEvent不会再被调用。
这条很好理解，就是说当一个View决定拦截一个事件后，那么系统会把同一个事件序列内的其他方法都直接交给它来处理，
因此就不用再调用这个View的onInterceptTouchEvent去询问它是否要拦截了

(4)某个View一旦开始处理事件，如果它不消耗ACTION_DOWN事件（onTouchEvent返回了false），
那么同一事件序列中的其他事件都不会再交给它来处理，并且事件将重新交由它的父元素去处理，即父元素的onTouchEvent会被调用
也就是说事件一旦交给一个View处理，那么它就必须消耗掉，否则同一事件序列中剩下的事件就不再交给他处理了

(5)如果View不消耗除ACTION_DOWN以外的其他事件，那么这个点击事件会消失，那么这个点击事件会消失，
此时父元素的onTouchEvent并不会被调用，并且当前View可以持续收到后续的事件，
最终这些消失的点击事件会传递给Activity处理

(6)ViewGroup默认不拦截任何事件。Android源码中ViewGroup的onInterceptTouchEvent方法默认返回false

(7)View没有onInterceptTouchEvent方法，一旦有点击事件传递给它，那么它的onTouchEvent方法就会被调用

(8)View的onTouchEvent默认都会消耗事件（返回true），除非它是不可点击的（clickable和longClickable同时为false）
View的longClickable属性默认为false，
clickable属性要分情况，比如Button的clickable属性默认为true，而TextView的clickable属性默认为false

(9)View的enable属性不影响onTouchEvent的默认返回值
如果一个View是disable的，它的clickable或者longClickable中有一个为true，那么它的onTouchEvent就返回true

(10)onClick会发生的前提是当前View是可点击的，并且它收到了down和up的事件

(11)事件传递过程是由外向内的，即事件总是先传递给父元素，然后再由父元素分发给子View
通过requestDisallowInterceptTouchEvent方法可以在子元素中干预父元素的事件分发过程，但是ACTION_DOWN事件除外


### 4.2 事件分发的源码解析

#### 1. Activity对点击事件的分发过程
**点击操作发生时，事件最先传递给当前Activity，由Activity#dispatchTouchEvent()进行事件分发**
具体是由Activity内部的Window来完成的，Window将事件传递给DecorView
```
// Activity#dispatchTouchEvent()
public boolean dispatchTouchEvent(MotionEvent event) {
	if(event.getAction() == MotionEvent.ACTION_DOWN) {
		onUserInteraction();
	}
	if(getWindow().superDispatchTouchEvent(event)) {
		// 已被处理，事件循环结束
		return true;
	}
	// 事件没人处理，Activity#onTouchEvent()被调用
	return onTouchEvent(event);
}
```

#### 2. Window对点击事件的分发过程
Window接口的唯一实现是PhoneWindow，这里的getWindow()就是得到PhoneWindow
看一下PhoneWindow的superDispatchTouchEvent方法：
```
// PhoneWindow#superDispatchTouchEvent()
public boolean superDispatchTouchEvent(MotionEvent event) {
	return mDecor.superDispatchTouchEvent(event);
}
```

PhoneWindow直接将事件传递给了DecorView（顶级View）

#### 3. 顶级View对点击事件的分发过程

点击事件到达顶级View（一般是一个ViewGroup）以后，会调用ViewGroup的dispatchTouchEvent方法
-> 如果顶级ViewGroup拦截事件（即onInterceptTouchEvent返回true），则事件由ViewGroup处理
**这时如果ViewGroup的mOnTouchListener被设置，则onTouch会被调用，否则onTouchEvent会被调用**
**也就是说都提供的话，onTouch会屏蔽掉onTouchEvent**
**同时只有onTouchEvent被调用了，并且设置了mOnClickListener时，onClick才会被调用**

-> 如果顶级ViewGroup不拦截事件，则事件会传递给它所在的点击事件链上的子View，这时子View的dispatchTouchEvent会被调用
-> 如此循环，完成整个事件的分发


首先看ViewGroup对点击事件的分发过程，方法长，分段说明
当前View是否拦截点击事件的逻辑：
```
// ViewGroup#dispatchTouchEvent()

// Check for interception
final boolean intercepted;
if(actionMasked == MotionEvent.ACTION_DOWN || mFirstTouchTarget != null) {
	final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
	if(!disallowIntercept) {
		intercepted = onInterceptTouchEvent(event);
		// restore action in case it was changed
		ev.setAction(action);
	} else {
		intercepted = false;
	}
} else {
	// There are no touch targets and this action is not an initial down
	// so this view group continus to intercept touches.
	intercepted = true;
}
```

当事件被ViewGroup的子元素成功处理时，mFirstTouchTarget会被赋值并指向子元素
即当ViewGroup不拦截事件并将事件交给子元素处理时，mFirstTouchTarget != null成立
反过来，一旦事件由当前ViewGroup拦截时，mFirstTouchTarget != null就不成立

// ....
// 待补充
/// ...

从上面的源码分析，我们可以得出结论：
当ViewGroup决定拦截事件后，那么后续的点击事件将会默认交给它处理，并且不在调用它的onInterceptTouchEvent方法





当ViewGroup不拦截事件的时候，事件会向下分发交给它的子View进行处理：
```
final View[] children = mChildren;
for(int i = childrenCount - 1; i >= 0; i--) {
	final int childIndex = customOrder ? getChildrenDrawingOrder(childrenCount, i) : i;
	final View child = (preorderedList == null) ? children[childIndex] : preorderedList.get(childIndex);
	// 是否能够接收到点击事件
	if(!canViewReceivePointerEvents(child) 
			|| !isTransformedTouchPointInView(x, y, child, null)) {
		continue;
	}

	newTouchTarget = getTouchTarget(child);
	if(newTouchTarget != null) {
		// Child is already receiving touch within its bounds.
		// Give it the new pointer in addition to the ones it is handling.
		newTouchTarget.pointerIdBits |= idBitsToAssign;
		break;
	}

	resetCancelNextUpFlag(child);
	if(dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)) {
		// Child wants to receive touch within its bounds.
		mLastTouchDownTime = ev.getDownTime();
		if(preorderedList != null) {
			// childIndex points into presorted list, find original index
			for(int j = 0; j < childrenCount; j++) {
				if(children[childIndex] == mChildren[j]) {
					mLastTouchDownIndex = j;
					break;
				}
			}
		} else {
			mLastTouchDownIndex = childIndex;
		}
		mLastTouchDownX = ev.getX();
		mLastTouchDownY = ev.getY();
		newTouchTarget = addTouchTarget(child, idBitsToAssign);
		alreadyDispatchedNewTouchTarget = true;
		break;
	}
}
```

dispatchTransformedTouchEvent实际上调用的就是子元素的dispatchTouchEvent方法，
内部的一段代码如下：
```
if(child == null) {
	handled = super.dispatchTouchEvent(event);
} else {
	handled = child.dispatchTouchEvent(event);
}
```

如果子元素的dispatchTouchEvent返回true，那么mFirstTouchTarget就会被赋值同时跳出上面代码中的for循环：
```
newTouchTarget = addTouchTarget(child, idBitsToAssign);
alreadyDispatchedNewTouchTarget = true;
break;
```
这几行代码完成了mFirstTouchTarget的赋值，并终止了对子元素的遍历
```
private TouchTarget addTouchTarget(View child, int pointerIdBits) {
	TouchTarget target = TouchTarget.obtain(child, pointerIdBits);
	target.next = mFirstTouchTarget;
	mFirstTouchTarget = target;
	return target;
}
```

如果子元素的dispatchTouchEvent返回了false，ViewGroup就把事件分发给下一个元素


## 5. View的滑动冲突



### 5.1 常见的滑动冲突场景





### 5.2 滑动冲突的处理规则





### 5.3 滑动冲突的解决方式















































