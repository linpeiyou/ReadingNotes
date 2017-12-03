# View的工作原理

View的三大流程
View常见的回调方法（如构造方法、onAttach、onVisibilityChanged、onDetach）
View的滑动、View的滑动冲突


## 1. 初识ViewRoot和DecorView

**ViewRoot是接口，实现类是ViewRootImpl，它是连接WindowManager和DecorView的纽带**
**View的三大流程都是通过ViewRoot来完成的**
**在ActivityThread中，当Activity对象被创建完毕后，会将DecorView添加到Window中**
**同时创建ViewRootImpl对象，并将ViewRootImpl和DecorView建立关联：**
```
root = new ViewRootImpl(view.getContext(), display);
root.setView(view, wparams, panelParentView);
```

View的绘制流程是从ViewRoot的performTraversals方法开始的
-> performTraversals会依次调用performMeasure、performLayout、performDraw
-> 这三个方法分别完成顶级View的measure、layout、draw三大流程
-> performTraversals中会调用measure，在measure中调用onMeasure方法，在onMeasure中对所有的子元素进行measure
-> 接着子元素会重复父容器的measure过程，如此反复完成整个View树的遍历
（performLayout和performDraw的传递流程和performMeasure类似，
唯一不同的是performDraw的传递是在draw方法中通过dispatchDraw来实现的，本质上没有区别）


...
...


## 2. 理解MeasureSpec

### 2.1 MeasureSpec

MeasureSpec是一个32位的int值，高2位代表SpecMode，低30位代表SpecSize

SpecMode：测量模式
SpecSize：某种测量模式下的规格大小

```
private static final int MODE_SHIFT = 30;
private static final int MODE_MASK = 0x3 << MODE_SHIFT;
public static final int UNSPECIFIED = 0 << MODE_SHIFT;
public static final int EXACTLY = 1 << MODE_SHIFT;
public static final int AT_MOST = 2 << MODE_SHIFT;

public static int makeMeasureSpec(int size, int mode) {
	if(sUseBrokenMakeMeasureSpec) {
		return size + mode;
	} else {
		return (size & ~MODE_MASK) | (mode & MODE_MASK);
	}
}

public static int getMode(int measureSpec) {
	return (measureSpec & MODE_MASK);
}

public staitc int getSize(int measureSpec) {
	return (measureSpec & ~MODE_MASK);
}
```

SpecMode的三类：
1.UNSPECIFIED
父容器不对View有任何限制，要多大给多大，这种情况一般用于系统内部，表示一种测量的状态
2.EXACTLY
父容器已经检测出View所需要的精确大小，这个时候View的最终大小就是SpecSize所指定的值
它对应于LayoutParams中的match_parent和具体的数值这两种模式
3.AT_MOST
父容器指定了一个可用大小即SpecSize，View的大小不能大于这个值，具体值要看不同的View的具体实现
它对应于LayoutParams中的wrap_content


### 2.2 MeasureSpec和LayoutParams

在View测量的时候，系统会将LayoutParams在父容器的约束下转换成对应的MeasureSpec，然后再根据这个MeasureSpec来确定View测量后的宽/高

**LayoutParams和父容器一起决定了View的MeasureSpec**

对于DecorView，其MeasureSpec由窗口的尺寸和其自身的LayoutParams共同决定；
对于普通View，其MeasureSpec由父容器的MeasureSpec和自身的LayoutParams来共同决定
MeasureSpec一旦确定后，onMeasure中就可以确定View的测量宽/高


对于DecorView，在ViewRootImpl中的measureHierarchy方法中有如下代码
desiredWindowWidth和desiredWindowHeight是屏幕的尺寸，DecorView的MeasureSpec创建过程：
```
// ViewRootImpl#measureHierarchy()
childWidthMeasureSpec = getRootMeasureSpec(desiredWindowWidth, lp.width);
childHeightMeasureSpec = getRootMeasureSpec(desiredWindowHeight, lp.height);
performMeasure(childWidthMeasureSpec, childHeightMeasureSpec);
```

ViewRootImpl#getRootMeasureSpec()的实现：
```
// ViewRootImpl#getRootMeasureSpec()
private static int getRootMeasureSpec(int windowSize, int rootDimension) {
	int measureSpec;
	switch(rootDimension) {
		case ViewGroup.LayoutParams.MATCH_PARENT:
			// Window can't resize. Force root view to be windowSize.
			measureSpec = MeasureSpec.makeMeasureSpec(windowSize, MeasureSpec.EXACTLY);
			break;
		case ViewGroup.LayoutParams.WRAP_CONTENT:
			// Window can resize. Set max size for root view.
			measureSpec = MeasureSpec.makeMeasureSpec(windowSize, MeasureSpec.AT_MOST);
			break;
		default:
			// Window wants to be an exact size. Force root view to be that size.
			measureSpec = MeasureSpec.makeMeasureSpec(rootDimension, MeasureSpec.EXACTLY);
			break;
	}
	return measureSpec;
}
```

对于普通的View而言，View的measure过程由ViewGroup传递而来
分析下ViewGroup的measureChildWithMargins方法：
```
// ViewGroup#measureChildWidthMargins()
protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
		int parentHeightMeasureSpec, int heightUsed) {
	final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
	final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
			mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin + widthUsed, lp.width);
	final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
			mPaddingTop + mPaddingBottom + lp.topMargin + lp.topMargin + heightUsed, lp.height);

	child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
}
```

在调用子元素的measure方法前会先通过ViewGroup#getChildMeasureSpec()来得到子元素的MeasureSpec
```
// ViewGroup#getChildMeasureSpec()
// 根据父容器的MeasureSpec和View本身的LayoutParams来确定子元素的MeasureSpec
// padding是指父容器中已占用的控件大小，因此子元素可用的空间大小为父容器的尺寸减去padding
public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
	int specMode = MeasureSpec.getMode(spec);
	int specSize = MeasureSpec.getSize(spec);

	int size = Math.max(0, specSize - padding);

	int resultSize = 0;
	int resultMode = 0;

	switch(specMode) {
		// Parent has imposed an exact size on us
		case MeasureSpec.EXACTLY:
			if(childDimension >= 0) {
				resultSize = childDimension;
				resultMode = MeasureSpec.EXACTLY;
			} else if (childDimension == LayoutParams.MATCH_PARENT) {
				// Child wants to be our size. So be it.
				resultSize = size;
				resultMode = MeasureSpec.EXACTLY;
			} else if (childDimension == LayoutParams.WRAP_CONTENT) {
				// Child wants to determine its own size. It can't be bigger than us.
				resultSize = size;
				resultMode = MeasureSpec.AT_MOST;
			}
			break;

		// Parent has imposed an maximum size on us
		case MeasureSpec.AT_MOST:
			if(childDimension >= 0) {
				// Child wants a specific size... so be it
				resultSize = childDimension;
				resultMode = MeasureSpec.EXACTLY;
			} else if (childDimension == LayoutParams.MATCH_PARENT) {
				// Child wants to be our size, but our size is not fixed.
				// Constrain child to not be bigger than us.
				resultSize = size;
				resultMode = MeasureSpec.AT_MOST;
			} else if (childDimension == LayoutParams.WRAP_CONTENT) {
				// Child wants to determine its own size.
				// It can't be bigger than us.
				resultSize = size;
				resultMode = MeasureSpec.AT_MOST;
			}
			break;

		// Parent asked to see how big we want to be
		case MeasureSpec.UNSPECIFIED:
			if(childDimension >= 0) {
				// Child wants a specific size... let him have it
				resultSize = childDimension;
				resultMode = MeasureSpec.EXACTLY;
			} else if (childDimension == LayoutParams.MATCH_PARENT) {
				// Child wants to be our size... find out how big it should be
				resultSize = 0;
				resultMode = MeasureSpec.UNSPECIFIED;
			} else if (childDimension == LayoutParams.WRAP_CONTENT) {
				// Child wants to determine its own size... find out how big it should be
				resultSize = 0;
				resultMode = MeasureSpec.UNSPECIFIED;
			}
			break;
	}
	return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
}
```


## 3. View的工作流程

measure确定View的测量宽/高
layout确定View的最终宽/高和四个顶点的位置
draw将View绘制到屏幕上

### 3.1 measure过程

1.View通过measure方法完成自身测量过程
2.ViewGroup除了完成自己的测量过程，还会遍历去调用所有子元素的measure方法，子元素再递归去执行这个流程

#### 1.View的measure过程

View的measure过程由其measure方法完成，measure方法是final的不能重写
View#measure()会调用View#onMeasure()：
```
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	// setMeasureDimension方法会设置View宽/高的测量值
	setMeasureDimension(
		getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
		getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
}
```

看下View#getDefaultSize()方法：
```
// View#getDefaultSize()
public static int getDefaultSize(int size, int measureSpec) {
	int result = size;
	int specMode = MeasureSpec.getMode(measureSpec);
	int specSize = MeasureSpec.getSize(measureSpec);

	switch(specMode) {
		case MeasureSpec.UNSPECIFIED:
			result = size;
			break;

		case MeasureSpec.AT_MOST:
		case MeasureSpec.EXACTLY:
			result = specSize;
			break;
	}
	return result;
}
```
对于AT_MOST和EXACTLY这两种情况，getDefaultSize()就是返回measureSpec中的specSize
这个specSize就是View测量后的大小（View最终的大小在layout阶段确定）

至于UNSPECIFIED这种情况，一般用于系统内部的测量过程
这时View的大小为getDefaultSize的第一个参数size，即getSuggestedMinimumWidth()和getSuggestedMinimumHeight()的返回值
```
protected int getSuggestedMinimumWidth() {
	return (mBackground == null) ? mMinWidth : max(mMinWidth, mBackground.getMinimumWidth());
}

protected int getSuggestedMinimumHeight() {
	return (mBackground == null) ? mMinHeight : max(mMinHeight, mBackground.getMinimumHeight());
}
```

分析下getSuggestedMinimumWidth()的实现：
如果没有设置背景，那么View的宽度为mMinWidth，mMinWidth对应android:minWidth这个属性的值，默认为0
如果设置了背景，那么View的宽度为max(mMinWidth, mBackground.getMinimumWidth())

看下Drawable#getMinimumWidth()方法：
```
// 返回Drawable的原始高度，没有原始高度则返回0
public int getMinimumWidth() {
	final int intrinsicWidth = getIntrinsicWidth();
	return intrinsicWidth > 0 ? intrinsicWidth : 0;
}
```
Drawable在何时有原始高度？
举个例子，ShapeDrawable无原始宽/，BitmapDrawable有原始宽/高（图片的尺寸），详细见第6章


从getDefaultSize方法的实现来看，View的宽/高由specSize决定
**所以可以得到结论：直接继承View的自定义控件需要重写onMeasure方法并设置wrap_content时的自身大小，
否则在布局中使用wrap_content就相当于使用match_parent**

分析：如果View在布局中使用wrap_content，那么它的specMode是AT_MOST，在这种模式下，它的宽/高等于specSize
从ViewGroup#getChildMeasureSpec()方法中可知，这种情况下View的specSize是parentSize（父容器中目前可以使用的大小）
所以这个时候使用wrap_content和使用match_parent是一致的

所以要自己处理这个问题：
```
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
	int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
	int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
	int heightSpecSize = MeasureSpec.getMode(heightMeasureSpec);

	if(widthSpecMode == MeasureSpec.AT_MOST && heightSpecMode == MeasureSpec.AT_MOST) {
		setMeasureDimension(mWidth, mHeight);
	} else if (widthSpecMode == MeasureSpec.AT_MOST) {
		setMeasureDimension(mWidth, heightSpecSize);
	} else if (heightSpecMode == MeasureSpec.AT_MOST) {
		setMeasureDimension(widthMeasureSpec, mHeight);
	}
}
```

在上面的代码中，我们只需要给View指定一个默认的内部宽/高（mWidth和mHeight），并在wrap_content时设置此宽/即可
对于非wrap_content情形，沿用系统的测量值既可，
这个默认的内部宽/高的指定没有固定的依据，可以根据需要灵活指定
（可以看TextView、ImageView等的源码，它们都对wrap_content情形下的onMeasure方法做了处理）


**上面流程的一个总结：**
在ViewGroup#measure()中调用了ViewGroup#measureChildWithMargins()
在ViewGroup#measureChildWithMargins()中
通过 ViewGroup#getChildMeasureSpec()构造了子View的MeasureSpec
然后 执行child.measure(widthMeasureSpec, heightMeasureSpec)
在View#measure()中调用View#onMeasure()
在View#onMeasure()中调用了View#setMeasureDimension()


#### 2.ViewGroup的measure过程

**除了完成自己的measure过程，还会遍历调用所有子元素的measure方法，各个子元素再去递归执行这个过程**

**和View不同的是，ViewGroup是一个抽象类，它没有重写View的onMeasure方法，但是提供了measureChildren方法：**
```
// ViewGroup#measureChildren()
protected void measureChildren(int widthMeasureSpec, int heightMeasureSpec) {
	final int size = mChildrenCount;
	final View[] children = mChildren;
	for(int i = 0; i < size; ++i) {
		final View child = children[i];
		if((child.mViewFlags & VISIBILITY_MASK) != GONE) {
			measureChild(child, widthMeasureSpec, heightMeasureSpec);
		}
	}
}
```

从上面的代码看，ViewGroup在measure时，会对每一个子元素进行measure
```
// ViewGroup#measureChild()
protected void measureChild(View child, int parentWidthMeasureSpec, int parentHeightMeasureSpec) {
	final LayoutParams lp = child.getLayoutParams();
	final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
			mPaddingLeft + mPaddingRight, lp.width);
	final int childHeightMeasureSpec = getChildMeasureSpec(parentHeightMeasureSpec,
			mPaddingTop + mPaddingBottom, lp.height);
	child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
}
```

通过ViewGroup#getChildMeasureSpec()方法
传入ViewGroup的MeasureSpec和子View的LayoutParams来创建子View的MeasureSpec对象
ViewGroup#getChildMeasureSpec()方法的源码在上面分析了


问题：为什么ViewGroup不像View一样对其onMeasure方法做统一的实现呢？
答：因为不同的ViewGroup子类有不同的布局特性，所以它们的测量细节各不相同
如LinearLayout和RelativeLayout两者的布局特性显然不同，导致ViewGroup无法做统一实现

以下分析LinearLayout的onMeasure方法：
```
protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	if(mOrientation == VERTICAL) {
		measureVertical(widthMeasureSpec, heightMeasureSpec);
	} else {
		measureHorizontal(widthMeasureSpec, heightMeasureSpec);
	}
}
```

我们选择measureVertical的部分代码来看看：
```
// LinearLayout#measureVertical()
// See how tall everyone is. Also remember max width.
for(int i = 0; i < count; ++i) {
	final View child = getVerticalChildAt(i);
	...
	// Determine how big this child would like to be. If this or
	// pervious children have given a weight, then we allow it to 
	// use all available space (and we will shrink things later if neeeded).
	measureChildBeforeLayout(child, i, widthMeasureSpec, 0, heightMeasureSpec,
			totalWeight == 0 ? mTotalLength : 0);

	if(oldHeight != Integer.MIN_VALUE) {
		lp.height = oldHeight;
	}

	final int childHeight = child.getMeasuredHeight();
	final int totalLength = mTotalLength;
	mTotalLength = Math.max(totalLength, 
			totalLength + childHeight + lp.topMargin + lp.bottomMargin + getNextLocationOffset(child));
}
```

遍历子元素并对每个子元素执行measureChildBeforeLayout方法，这个方法内部会调用子元素的measure方法
这样各个子元素就开始依次进入measure方法

mTotalLength用来存储LinearLayout在竖直方向的初步高度
每测量一个元素，mTotalLength就会增加，增加的部分包括子元素的高度和在竖直方向上的margin
子元素测量完毕后，LinearLayout会测量自己大小，源码如下：
```
// LinearLayout#measureVertical()
// Add in our padding
mTotalLength += mPaddingTop + mPaddingBottom;
int heightSize = mTotalLength;
// Check against our minimum height
heightSize = Math.max(heightSize, getSuggestedMinimumHeight());
// Reconcile our calculated size with the heightMeasureSpec
int heightSizeAndState = resolveSizeAndState(heightSize, heightMeasureSpec, 0);
heightSize = heightSizeAndState & MEASURED_SIZE_MASK;
...
setMeasureDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState), heightSizeAndState);
```

上述代码中，子元素测量完毕后，LinearLayout会根据子元素的情况来测量自己的大小
对竖直的LinearLayout而言，它在水平方向的测量过程遵循View的测量过程；在竖直方向的测量过程和View有所不同。
如果它布局中高度采用的是match_parent或者具体数值，那么它的测量过程和View一致，即高度为specSize；
如果它布局中高度采用的是wrap_content，那么它的高度是所有子元素所占用高度的总和，
但是仍然不能超过它的父容器的剩余空间，当然它的最终高度还要考虑其在竖直方向上的padding

View#resolveSizeAndState()源码：
```
// View#resolveSizeAndState()
public static int resolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
	int result = size;
	int specMode = MeasureSpec.getMode(measureSpec);
	int specSize = MeasureSpec.getSize(measureSpec);
	switch(specMode) {
		case MeasureSpec.UNSPECIFIED:
			result = size;
			break;

		case MeasureSpec.AT_MOST:
			if(specSize < size) {
				result = specSize | MEASURED_STATE_TOO_SMALL;
			} else {
				result = size;
			}
			break;

		case MeasureSpec.EXACTLY:
			result = specSize;
			break;
	}
	return result | (childMeasuredState & MEASURED_STATE_MASK);
}
```

measure完成后，通过getMeasuredWidth()、getMeasuredHeight()方法可以获得View的测量宽/高
某些极端情况下，系统可能要多次measure才能确定最终的测量宽/高，这种情况下，在onMeasure方法中拿到的宽/高很可能是不准确的
一个比较好的习惯是在onLayout方法中取获取View的测量宽/高


问：在Activity启动的时候怎么获取某个View的宽/高？
分析：View的measure过程和Activity的生命周期方法不是同步执行的，因此无法保证Activity执行了onCreate、
onStart、onResume时某个View已经测量完毕了，如果View还没测量完毕，那么获得的宽/高就是0

解决办法：
(1) Activity/View#onWindowFocusChanged
onWindowFocusChanged方法的含义是：View已经初始化完毕了，宽/高已经准备好了，这个时候去获取宽高是没问题的
要注意的是：onWindowFocusChanged会被调用多次，当Activity的窗口得到焦点、失去焦点均会被调用一次
具体来说，当Activity继续执行和暂停执行时，onWindowFocusChanged均会被调用
典型代码如下：
```
public void onWindowFocusChanged(boolean hasFocus) {
	super.onWindowFocusChanged(hasFocus);
	if(hasFocus) {
		int width = view.getMeasuredWidth();
		int height = view.getMeasuredHeight();
	}
}
```

(2) view.post(runnable)
通过post可以将一个runnable投递到消息队列的尾部，然后等待Looper调用此runnable的时候，View也已经初始化好了
典型代码如下：
```
protected void onStart() {
	super.onStart();
	view.post(new Runnable() {

		@Override
		public void run() {
			int width = view.getMeasuredWidth();
			int height = view.getMeasuredHeight();
		}
	});
}
```

(3) ViewTreeObserver
使用ViewTreeObserver的众多回调可以完成这个功能
如使用OnGlobalLayoutListener这个接口，当View树的状态发生改变或者View树内部的View的可见性发生改变时，
onGlobalLayout方法将被回调，因此这是获取View的宽/高一个很好的时机
要注意的是：伴随着View树的状态改变，onGlobalLayout会被调用多次
典型代码如下：
```
protected void onStart() {
	super.onStart();

	ViewTreeObserver observer = view.getViewTreeObserver();
	observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

		@SuppressWarnings("deprecation")
		@Override
		public void onGlobalLayout() {
			view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
			int width = view.getMeasuredWidth();
			int height = view.getMeasuredHeight();
		}
	});
}
```

(4) view.measure(int widthMeasureSpec, int heightMeasureSpec)
通过手动对View进行measure来得到View的宽/高
这种方法比较复杂，要分情况处理，根据View的LayoutParams来分：
**情况1：match_parent**
直接放弃，无法measure出具体的宽/高
原理很简单，根据ViewGroup#getChildMeasureSpec()方法，构造此种MeasureSpec需要知道parentSize
这个时候我们不知道parentSize（父容器的剩余空间）的大小，所以理论上不可能测量出View的大小

**情况2：具体的数值（dp/px）**
比如宽高都是100px，如下measure：
```
int widthMeasureSpec = MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY);
int heightMeasureSpec = MeasureSpec.makeMeasureSpec(100, MeasureSpec.EXACTLY);
view.measure(widthMeasureSpec, heightMeasureSpec);
```

**情况3：wrap_content**
如下measure：
```
int widthMeasureSpec = MeasureSpec.makeMeasureSpec((1 << 30) - 1, MeasureSpec.AT_MOST);
int heightMeasureSpec = MeasureSpec.makeMeasureSpec((1 << 30) - 1, MeasureSpec.AT_MOST);
view.measure(widthMeasureSpec, heightMeasureSpec);
```
View的尺寸使用30位二进制表示，也就是(1 << 30) - 1
所以上述代码中，用View理论上能支持的最大值去构造MeasureSpec是合理的


### 3.2 layout过程

layout的作用是ViewGroup用来确定子元素的位置
当ViewGroup的位置被确定后，它在onLayout中会遍历所有的子元素并调用其layout方法，在layout方法中onLayout又被调用...

**layout方法确定View本身的位置，而onLayout方法则会确定所有子元素的位置**
```
// View#layout()
public void layout(int l, int t, int r, int b) {
	if((mPrivateFlags3 & PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT) != 0) {
		onMeasure(mOldWidthMeasureSpec, mOldHeightMeasureSpec);
		mPrivateFlags3 &= ~PFLAG3_MEASURE_NEEDED_BEFORE_LAYOUT;
	}

	int oldL = mLeft;
	int oldT = mTop;
	int oldB = mBottom;
	int oldR = mRight;

	boolean changed = isLayoutModeOptical(mParent) ? 
			setOpticalFrame(l, t, r, b) : setFrame(l, t, r, b);

	if(changed || (mPrivateFlags & PFLAG_LAYOUT_REQUIRED) == PFLAG_LAYOUT_REQUIRED) {
		onLayout(changed, l, t, r, b);
		mPrivateFlags &= ~PFLAG_LAYOUT_REQUIRED;

		ListenerInfo li = mListenerInfo;
		if(li != null && li.mOnLayoutChangeListeners != null) {
			ArrayList<OnLayoutChangeListener> listenersCopy = 
					(ArrayList<OnLayoutChangeListener>) li.mOnLayoutChangeListeners.clone();
			int numListeners = listenersCopy.size();
			for(int i = 0; i < numListeners; ++i) {
				listenersCopy.get(i).onLayoutChange(this, l, t, r, b, oldL, oldT, oldR, oldB);
			}
		}
	}

	mPrivateFlags &= ~PFLAG_FORCE_LAYOUT;
	mPrivateFlags3 |= PLAG3_IS_LAID_OUT;
}
```

layout方法大致流程如下：
-> 首先通过setFrame方法来设定四个View的顶点位置，即初始化mLeft, mRight, mTop, mBottom这四个值，
View的四个顶点一旦确定，那么View在父容器中的位置也就确定了
-> 接着会调用onLayout方法，这个方法的用途是父容器确定子元素的位置，
和onMeasure方法类似，onLayout的具体实现和具体的布局有关，所以View和ViewGroup均没有真正实现onLayout方法

看一下LinearLayout的onLayout方法：
```
// LinearLayout#onLayout()
public void onLayout(boolean changed, int l, int t, int r, int b) {
	if(mOrientation == VERTICAL) {
		layoutVertical(l, t, r, b);
	} else {
		layoutHorizontal(l, t, r, b);
	}
}
```

继续看layoutVertical方法（部分源码）：
```
// LinearLayout#layoutVertical()
void layoutVertical(int left, int top, int right, int bottom) {
	...
	final int count = getVirtualChildCount();
	for(int i = 0; i < count; ++i) {
		final View child = getVirtualChildAt(i);
		if(child == null) {
			childTop += measureNullChild(i);
		} else if (child.getVisibility() != GONE) {
			final int childWidth = child.getMeasuredWidth();
			final int childHeight = child.getMeasuredHeight();

			final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
			...
			if(hasDividerBeforeChildAt(i)) {
				childTop += mDividerHeight;
			}

			childTop += lp.topMargin;
			setChildFrame(child, childLeft, childTop + getLocationOffset(child), childWidth, childHeight);
			childTop += childHeight + lp.bottomMargin + getNextLocationOffset(child);

			i += getChildrenSkipCount(child, i);
		}
	}
}
```

layoutVertical代码逻辑：
遍历所有子元素并调用setChildFrame方法为子元素指定相应的位置


```
// LinearLayout#setChildFrame()
```


```
// View#setFrame()
```

### 3.3 draw过程

View的绘制过程遵循以下几个步骤：
(1) 绘制背景 background.draw(canvas)
(2) 绘制自己 onDraw
(3) 绘制children dispatchDraw
(4) 绘制装饰 onDrawScrollBars

```
// View#draw()
public void draw(Canvas canvas) {
	final int privateFlags = mPrivateFlags;
	final boolean dirtyOpaque = (privateFlags & PFLAG_DIRTY_MASK) ==
			PFLAG_DIRTY_OPAQUE && (mAttachInfo == null || !mAttachInfo.mIgnoreDirtyState);
	// 取消DIRTY标志位，加上DRAWN标志位
	mPrivateFlags = (privateFlags & ~PFLAG_DIRTY_MASK) | PFLAG_DRAWN;

	/**
	 * Draw traversal performs several drawing steps which must be 
	 * executed in the appropriate order:
	 * 1. Draw the background
	 * 2. If necessary, save the canvas' layers to prepare for fading
	 * 3. Draw view's content
	 * 4. Draw children
	 * 5. If necessary, draw the fading edges and restore layers
	 * 6. Draw decorations (scrollbars for instance)
	 */

	// Step 1, draw the background, if needed
	int saveCount;

	if(!dirtyOpaque) {
		drawBackground(canvas);
	}

	// skip step 2 & 5 if possible (common case)
	final int viewFlags = mViewFlags;
	boolean horizontalEdges = (viewFlags & FADING_EDGE_HORIZONTAL) != 0;
	boolean verticalEdges = (viewFlags & FADING_EDGE_VERTICAL) != 0;
	if(!verticalEdges && !horizontalEdges) {
		// Step 3, draw the content
		if(!dirtyOpaque)
			onDraw(canvas);

		// Step 4, draw the children
		dispatchDraw(canvas);

		// Step 6, draw decorations (scrollbars)
		onDrawScrollBars(canvas);

		if(mOverlay != null && !mOverlay.isEmpty()) {
			mOverlay.getOverlayView().dispatchDraw(canvas);
		}

		// we're done...
		return;
	}
	...
}
```

View的绘制过程的传递是通过dispatchDraw来实现的，dispatchDraw会遍历调用所有子元素的draw方法

View有一个特殊的方法：setWillNotDraw
```
// View#setWillNotDraw()
/**
 * If this view doesn't do any drawing on its own, set this flag to 
 * allow further optimizations. By default, this flag is not set on View,
 * but could be set on some View subclasses such as ViewGroup
 *
 * Typically, if you override {@link #onDraw(android.graphics.Canvas)}
 * you should clear this flag.
 * 
 * @param willNotDraw whether or not this View draw on its own
 */
public void setWillNotDraw(boolean willNotDraw) {
	setFlags(willNotDraw ? WILL_NOT_DRAW : 0, DRAW_MASK);
}
```

**从注释可以看出，如果一个View不需要绘制任何内容，那么设置这个标记位设置为true，系统会进行相应的优化**
**默认情况下，View不启用这个优化标记位，但是ViewGroup会默认启用这个优化标记位**

这个标记位对实际开发的意义：当我们的自定义控件继承了ViewGroup并且本身不具备绘制功能时，
可以开启这个标记位从而便于系统进行后续的优化。

当然，如果明确知道一个ViewGroup需要通过onDraw来绘制内容时，要显示关闭这个标记位


## 4. 自定义View

### 4.1 自定义View的分类

(1) 继承View重写onDraw方法
需要自己支持wrap_content，并且padding也需要自己处理

(2) 继承ViewGroup派生的特殊Layout
需要合理处理ViewGroup的测量、布局这两个过程，并同时处理子元素的测量、布局过程

(3) 继承特定的View（如TextView）

(4) 继承特定的ViewGroup（比如LinearLayout）


### 4.2 自定义View须知

(1) 让View支持wrap_content
直接继承View或者ViewGroup的控件，如果不在onMeasure中对wrap_content做处理，
那么在使用wrap_content属性时和使用match_parent是一样的效果

(2) 如果有必要，让你的View支持padding
直接继承View的控件，如果不在draw方法中处理padding，那么padding属性是无法起作用的
直接继承ViewGroup的控件，需要在onMeasure和onLayout中考虑padding和子元素的margin对其造成的影响，
不然将导致padding和子元素的margin失效

(3) 尽量不要在View中使用Handler，没必要
View内部本身就提供了post系列的方法，完全可以替代Handler的作用
当然除非你很明确地要使用Handler来发送消息

(4) View中如果有线程或者动画，需要及时停止，参考View#onDetachedFromWindow()
当有线程或者动画需要停止时，那么onDetachedFromWindow是一个很好的时机
当包含此View的Activity退出或者当前View被remove时，View的onDetachedFromWindow方法会被调用

同时，当View变得不可见的时候我们也需要停止线程和动画，如果不及时处理可能会造成内存泄漏

(5) View带有滑动嵌套情形时，需要处理好滑动冲突



### 4.3 自定义View示例

### 4.4 自定义View的思想


















































