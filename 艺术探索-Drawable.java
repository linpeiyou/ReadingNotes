# Android的Drawable

本章内容：
首先描述Drawable的层次关系
接着介绍Drawable的分类
最后介绍自定义Drawable的知识

Drawable的优点：
使用简单，比自定义View成本低；
非图片类型的Drawable占用空间较小，对减小apk大小有帮助


## 1. Drawable简介

Drawable是一个抽象基类，它是所有Drawable对象的基类

Drawable的内部宽/高，通过getInstrinsicWidth和getInstrinsicHeight这两个方法可以获取到
**但并不是所有Drawable都有内部宽/高**
**比如一张图片形成的Drawable，内部宽/高为图片的宽/高；但是一个颜色形成的Drawable没有内部宽/高的概念**

Drawable的内部宽/高不等同于它的大小。
一般来说，Drawable没有大小的概念，当用作View的背景时，Drawable会被拉伸至View的同等大小


## 2. Drawable的分类

### 2.1 BitmapDrawable
表示一张图片。在开发中，可以直接引用原始图片；也可以通过XML的方式来描述它
通过XML来描述的BitmapDrawable可以设置更多的效果，如下所示：
```
<?xml version="1.0" encoding="utf-8"?>
<bitmap
	xmlns:android="http://schemas.android.com/apk/res/android"
	android:src="..."
	android:antialias="..."
	android:gravity="..."
	... />
```

下面是各个属性的含义：
android:src
图片的资源id

android:antialias
是否开启图片抗锯齿功能
开启后图片会变得平滑，也会轻微降低图片清晰度，建议开启

android:dither
是否开启抖动效果
当图片的像素配置与手机屏幕像素配置不一致时，开启这个选项可以让高质量图片在低质量屏幕上还能保持较好的显示效果，建议开启

android:filter
是否开启过滤效果
当图片尺寸被拉伸或压缩时，开启过滤效果可以保持较好的显示效果，建议开启

android:gravity
当图片小于容器的尺寸时，设置此选项可以对图片进行定位

android:mipMap
这是一种图像相关的处理技术，也叫纹理映射，默认值为false

android:tileMode
平铺模式。有这几个选项：["diabled"|"clamp"|"repeat"|"mirror"]，开启平铺模式后gravity属性会失效
disabled表示关闭平铺模式（默认值）；repeat表示在水平方向和竖直方向上的平铺效果；
mirror表示在水平和竖直方向上的镜面投影效果；clamp表示图片四周的像素会扩展到周围区域

#### NinePathDrawable
表示一张.9格式的图片，.9图片可以自动根据所需的宽/高进行相应的缩放并保证不失真
和BitmapDrawable一样，在实际使用中可以直接引用图片；也可以通过XML来描述.9图，如：
```
<?xml version="1.0" encoding="utf-8"?>
<nine-patch
	xmlns:android="http://schemas.android.com/apk/res/android"
	android:src="..."
	android:xxx="..."
	... />
```

### 2.2 ShapeDrawable
通过颜色来构造的图形，可以纯色也可以具有渐变效果

ShapeDrawable的语法较为复杂：
```
<?xml version="1.0" encoding="utf-8"?>
<shape
	xmlns:android="http://schemas.android.com/apk/res/android"
	android:shape=["rectangle" | "oval" | "line" | "ring"] >

	<corners
		android:radius="integer"
		android:topLeftRadius="integer"
		android:topRightRadius="integer"
		android:bottomLeftRadius="integer"
		android:bottomRightRadius="integer"/>

	<gradient
		android:angle="integer"
		android:centerX="integer"
		android:centerY="integer"
		android:startColor="integer"
		android:centerColor="integer"
		android:endColor="integer"
		android:gradientRadius="integer"
		android:type=["linear" | "radial" | "sweep"]
		android:useLevel=["true" | "false"] />

	<padding
		android:left="integer"
		android:top="integer"
		android:right="integer"
		android:bottom="integer" />

	<size
		android:width="integer"
		android:height="integer" />

	<solid
		android:color="color" />

	<stroke
		android:width="integer"
		android:color="color"
		android:dashWidth="integer"
		android:dashGap="integer" />
</shape>
```

<shape>标签创建的Drawable，其实体类实际上是GradientDrawable

android:shape
图形的形状，四个选项：rectangle（矩形，默认）、oval（椭圆）、line（横线）、ring（圆环）
其中line和ring这两个选项要通过<stroke>标签来指定线的宽度和颜色

针对ring这个形状，有5个特殊的属性：
android:innerRadius
圆环内半径，和innerRadiusRatio同时存在时，以innerRadius为准

android:thickness
圆环厚度（即外半径-内半径），和thicknessRatio同时存在时，以thickness为准

android:innerRadiusRatio
内半径占整个Drawable宽度的比例，默认9。如果为n，那么内半径=宽度/n

android:thicknessRatio
圆环厚度占整个Drawable宽度的比例，默认3。如果为n，那么厚度=宽度/n

android:useLevel
一般为false，否则有可能无法达到预期显示效果，除非它被当做LevelListDrawable

#### <corners>
表示shape的四个角的角度，只适用于矩形。这里角度指圆角的程度，单位px
android:radius="integer"
android:topLeftRadius="integer"
android:topRightRadius="integer"
android:bottomLeftRadius="integer"
android:bottomRightRadius="integer"

android:radius————为四个角同时设定相同的角度，优先级较低，会被其他四个属性覆盖

#### <solid>
纯色填充，通过android:color指定shape中填充颜色

#### <gradient>
和solid标签互相排斥，gradient表示渐变效果，有如下几个属性：
- android:angle —— 渐变角度，默认0表示从左往右，值必须为45的倍数，90表示从下到上
- android:centerX —— 渐变的中心点的横坐标
- android:centerY —— 渐变的中心点的纵坐标
- android:startColor —— 渐变起始色
- android:centerColor —— 渐变中间色
- android:endColor —— 渐变的结束色
- android:gradientRadius —— 渐变半径，仅当android:type="radial"时有效
- android:useLevel —— 一般为false，当Drawable作为StateListDrawable使用时为true
- android:type —— 渐变的类别，有linear（线性渐变，默认）、radial（径向渐变）、sweep（扫描线渐变）三种

#### <stroke>
Shape的描边，有如下几个属性：
- android:width —— 描边的宽度
- android:color —— 描边的颜色
- android:dashWidth —— 组成虚线的线段的宽度
- android:dashGap —— 组成虚线的线段之间的间隔
如果dashWidth和dashGap有任何一个为0，那么虚线效果将不能生效

#### <padding>
表示包含这个shape的View的空白

#### <size>
表示shape的宽/高（与最终显示大小无关，shape有可能被拉伸或缩小）

### 2.3 LayerDrawable
表示一种层次化的Drawable集合，通过将不同的Drawable放置在不同的层上面从而达到一种叠加后的效果
```
<?xml version="1.0" encoding="utf-8"?>
<layer-list
	xmlns:android="http://schemas.android.com/apk/res/android" >
	<item
		android:drawable="@[package:]drawable/drawable_resource"
		android:id="@[+][package:]id/resource_name"
		android:top="dimension"
		android:right="dimension"
		android:bottom="dimension"
		android:left="dimension" />
</layer-list>
```

一个layer-list中可以包含多个item，每个item表示一个Drawable
top、bottom、left、right属性表示Drawable相对于View的上下左右偏移量，单位为像素

我们可以通过drawable属性来直接引用一个已有的drawable资源；也可以在item中自定义Drawable

默认情况下，layer-list中的所有的Drawable都会被缩放至View的大小，
对于bitmap来说，需要使用android:gravity属性才能控制图片的显示效果

**Layer-list有层次的概念，下面的item会覆盖上面的item**

一个demo：
```
<?xml version="1.0" encoding="utf-8"?>
<layer-list
	xmlns:android="http://schemas.android.com/apk/res/android" >
	<item>
		<shape android:shape="rectangle" >
			<solid android:color="#0AC39E" />
		</shape>
	</item>

	<item android:bottom="6dp">
		<shape android:shape="rectangle" >
			<solid android:color="#FFFFFF" />
		</shape>
	</item>

	<item
		android:bottom="1dp"
		android:left="1dp"
		android:right="1dp">
		<shape android:shape="rectangle" >
			<solid android:color="#FFFFFF" />
		</shape>
	</item>
</layer-list>
```

### 2.4 StateListDrawable
对应标签<selector>
表示Drawable的集合，每个Drawable都对应着View的一种状态，系统会根据View的状态来选择合适的Drawable

StateListDrawable主要用于设置可单击的View的背景，最常见的是Button：
```
<?xml version="1.0" encoding="utf-8"?>
<selector 
	xmlns:android="http://schemas.android.com/apk/res/android"
	android:constantSize=["true" | "false"]
	android:dither=["true" | "false"]
	android:variablePadding=["true" | "false"] >

	<item
		android:drawable="@[package:]drawable/drawable_resource"
		android:state_pressed=["true" | "false"]
		android:state_focused=["true" | "false"]
		android:state_hovered=["true" | "false"]
		android:state_selected=["true" | "false"]
		android:state_checkable=["true" | "false"]
		android:state_checked=["true" | "false"]
		android:state_enabled=["true" | "false"]
		android:state_activated=["true" | "false"]
		android:state_window_focused=["true" | "false"]
</selector>
```

#### android:constantSize
StateListDrawable的固有大小是否不随着其状态的改变而改变（默认false）
状态改变会导致StateListDrawable切换到具体的Drawable，而不同的Drawable具有不同的固有大小
**true表示StateListDrawable的固有大小保持不变，这时它的固有大小是内部所有Drawable的固有大小的最大值**
**false则会随着状态的改变而改变**

#### android:dither
是否开启抖动效果，开启可以让图片在低质量的屏幕上仍然获得较好的显示效果，默认true

#### android:variablePadding
StateListDrawable的padding是否随着状态改变而改变（默认false，不建议开启此选项）
true表示会随着状态改变而改变
false表示StateListDrawable的padding是内部所有Drawable的padding的最大值

#### <item>标签
<item>标签表示一个具体的Drawable。
其中android:drawable是一个已有Drawable资源id，剩下的属性表示View的状态

常见状态如下：
android:state_pressed
表示按下状态，比如Button被按下后仍没有松开的状态

android:state_focused
表示View已经获取了焦点

android:state_selected
表示用户选择了View

android:state_checked
表示用户选中了View，一般适用于CheckBox这类在选中和非选中状态之间进行切换的View

android:state_enabled
表示View当前处于可用状态


**系统会根据View当前的状态，从selector中选择对应的item，按照从上到下的顺序查找，直到找到第一条匹配的item**
**一般来说，默认的item都应该放在最后一条并且不附带任何状态，这样当上面的item都不匹配的时候，系统就会选择默认的item**

### 2.5 LevelListDrawable
对应<level-list>标签，同样表示一个Drawable集合，集合中的每个Drawable都有一个等级（level）概念
根据不同的等级，LevelListDrawable会切换为对应的Drawable
```
<?xml version="1.0" encoding="utf-8"?>
<level-list xmlns:android="http://schemas.android.com/apk/res/android">

	<item
		android:drawable="@drawable/drawable_resource"
		android:maxLevel="integer"
		android:minLevel="integer" />
</level-list>
```

**每个item代表一个Drawable，有对应的等级范围，在minLevel和maxLevel之间的等级会对应此item**
当他作为View的背景时，可以通过Drawable的setLevel方法来设置不同的等级从而切换具体的Drawable
如果作为ImageView的前景Drawable，可以通过ImageView的setImageLevel方法来切换Drawable

Drawable的level范围为0~10000，最小为0（默认值），最大为10000

### 2.6 TransitionDrawable
对应<transition>标签，实现两个Drawable之间的淡入淡出效果
```
<?xml version="1.0" encoding="utf-8"?>
<transition xmlns:android="http://schemas.android.com/apk/res/android">
	<item
		android:drawable="@[package:]drawable/drawable_resource"
		android:id="@[+][package:]id/resource_name"
		android:top="dimension"
		android:right="dimension"
		android:bottom="dimension"
		android:left="dimension" />
</transition>
```
top、bottom、left、right表示的是Drawable四周的偏移量

一个Demo
首先定义TransitionDrawable：
```
// res/drawable/transition_drawable.xml
<?xml version="1.0" encoding="utf-8"?>
<transition xmlns:android="http://schemas.android.com/apk/res/android">
	<item android:drawable="@drawable/drawable1" />
	<item android:drawable="@drawable/drawable2" />
</transition>
```

然后将其设为View的背景
```
<TextView
	android:id="@+id/test_transition"
	android:layout_height="wrap_content"
	android:layout_width="wrap_content"
	android:backgroud="@drawable/transition_drawable" />
```

最后，可以通过startTransition来实现淡入淡出效果，或通过reverseTransition实现逆过程
```
TextView textView = (TextView) findViewById(R.id.test_transition);
TransitionDrawable drawable = (TransitionDrawable) textView.getBackground();
drawable.startTransition(1000);
```

### 2.7 InsetDrawable
对应<inset>标签。它可以将其他Drawable内嵌到自己当中，并可以在四周留出一定的间距。

当一个View希望自己的背景比自己实际区域小的时候，可以采用InsetDrawable来实现（通过LayerDrawable也可以）

```
<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
	android:drawable="@drawable/drawable_resource"
	android:insetTop="dimension"
	android:insetRight="dimension"
	android:insetBottom="dimension"
	android:insetLeft="dimension" />
```
insetTop、insetBottom、insetRight、insetLeft表示内凹的大小

inset中的shape距离View的边界15dp，demo：
```
<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
	android:insetTop="15dp"
	android:insetRight="15dp"
	android:insetBottom="15dp"
	android:insetLeft="15dp" >

	<shape android:shape="rectangle" >
		<solid android:color="#FF0000" />
	</shape>
</inset>
```

### 2.8 ScaleDrawable
对应<scale>标签，可以根据自己的等级（level）将指定的Drawable缩放到一定比例

```
<?xml version="1.0" encoding="utf-8"?>
<scale xmlns:android="http://schemas.android.com/apk/res/android"
	android:drawable="@drawable/drawable_resource"
	android:scaleGravity=["top" | "bottom" | "left" | "right" | "center_vertical" | "fill_vertical" | "center_horizontal" | "fill_horizontal" | "center" | "fill" | "clip_vertical" | "clip_horizontal"]
	android:scaleHeight="percentage"
	android:scaleWidth="percentage" />
```

android:scaleGravity相当于shape中的android:gravity
android:scaleWidth和android:scaleHeight分别表示对指定Drawable宽和高的缩放比例，以百分比的形式表示（如25%）

ScaleDrawable规则比较复杂
**等级对ScaleDrawable的影响：**
等级0时ScaleDrawable不可见（默认值）；要让ScaleDrawable可见，要设置等级不为0
```
public void draw(Canvas canvas) {
	if(mScaleState.mDrawable.getLevel() != 0) {
		mScaleState.mDrawable.draw(canvas);
	}
}
```

**ScaleDrawable的大小和等级and缩放比例的关系**
```
// ScaleDrawable#onBoundsChange()
protected void onBoundsChange(Rect bounds) {
	final Rect r = mTmpRect;
	final boolean min = mScaleState.mUseInstrinsicSizeAsMin;
	int level = getLevel();
	int w = bounds.width();
	if(mScaleState.mScaleWidth > 0) {
		final int iw = min ? mScaleState.mDrawable.getInstrinsicWidth() : 0;
		w -= (int) ((w - iw) * (10000 - level) * mScaleState.mScaleWidth / 10000);
	}
	int h = bounds.height();
	if(mScaleState.mScaleHeight > 0) {
		final int ih = min ? mScaleState.mDrawable.getInstrinsicHeight() : 0;
		h -= (int) ((h - ih) * (10000 - level) * mScaleState.mScaleHeight / 10000);
	}
	final int layoutDirection = getLayoutDirection();
	Gravity.apply(mScaleState.mGravity, w, h, bounds, r, layoutDirection);

	if(w > 0 && h > 0) {
		mScaleState.mDrawable.setBounds(r.left, r.top, r.right, r.bottom);
	}
}
```

这里分析一下mDrawable的宽度和等级and缩放比例的关系：
```
final int iw = min ? mScaleState.mDrawable.getInstrinsicWidth() : 0;
w -= (int) ((w - iw) * (10000 - level) * mScaleState.mScaleWidth / 10000);
```

iw一般都为0，上面的代码可以简化为：
`w -= (int) (w * mScaleState.mScaleWidth * (10000 - level) / 10000)`
如果ScaleDrawable的级别为最大值10000，那么就没有缩放效果；
如果ScaleDrawable的级别越大，那么内部的Drawable看起来就越大；
如果ScaleDrawable的XML中定义的缩放比例越大（即mScaleState.mScaleWidth），那么内部的Drawable看起来就越小


将一张图片近似地缩小为原大小的30%，demo：
```
<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
	android:drawable="@drawable/image1"
	android:scaleHeight="70%"
	android:scaleWidth="70%"
	android:scaleGravity="center" />

// 为了让ScaleDrawable可见，还必须设置ScaleDrawable的level在(0, 10000]区间
View testScale = findViewById(R.id.test_scale);
ScaleDrawable testScaleDrawable = (ScaleDrawable) testScale.getBackground();
testScaleDrawable.setLevel(1);
```

对于level的值，系统内部约定Drawable等级范围为0~10000
虽然设置大于10000的值也能正常工作，但是不推荐这么做

### 2.9 ClipDrawable
对应<clip>标签。可以根据自己当前的level来裁剪另一个Drawable
裁剪方向通过android:clipOrientation和android:gravity这两个属性来共同控制

```
<?xml version="1.0" encoding="utf-8"?>
<scale 
	xmlns:android="http://schemas.android.com/apk/res/android"
	android:drawable="@drawable/drawable_resource"
	android:clipOrientation=["horizontal" | "vertical"]
	android:gravity=["top" | "bottom" | "left" | "right" | "center_vertical" | "fill_vertical" | "center_horizontal" | "fill_horizontal" | "center" | "fill" | "clip_vertical" | "clip_horizontal"] />
```

clipOrientation表示裁剪方向，有水平和竖直两个方向
gravity比较复杂，要合clipOrientation一起才能发挥作用，gravity可以通过"|"来组合使用

#### ClipDrawable的gravity属性
top
将内部的Drawable放在容器顶部，不改变它的大小。如果为竖直裁剪，那么从底部开始裁剪

bottom
将内部的Drawable放在容器底部，不改变它的大小。如果为竖直裁剪，那么从顶部开始裁剪

left（默认值）
将内部的Drawable放在容器左边，不改变它的大小。如果为水平裁剪，那么从右边开始裁剪

right
将内部的Drawable放在容器右边，不改变它的大小。如果为水平裁剪，那么从左边开始裁剪

center_vertical
使内部的Drawable在容器中竖直居中，不改变它的大小。如果为竖直裁剪，那么从上下同时开始裁剪

fill_vertical
使内部的Drawable在竖直方向上填充容器。如果为竖直裁剪，那么仅当ClipDrawable的等级为0时，才能有裁剪行为
（0表示ClipDrawable被完全裁剪，即不可见）

center_horizontal
使内部的Drawable在容器中水平居中，不改变它的大小。如果为水平裁剪，那么从左右同时开始裁剪

fill_horizontal
使内部的Drawable在水平方向上填充容器。如果为水平裁剪，那么仅当ClipDrawable的等级为0时，才能有裁剪行为
（0表示ClipDrawable被完全裁剪，即不可见）

center
使内部的Drawable在容器中水平和竖直方向都居中，不改变它的大小。
如果为竖直裁剪，那么从上下同时开始裁剪；如果为水平裁剪，那么从左右同时开始裁剪

fill
使内部的Drawable在水平和竖直方向上同时填充容器。仅当ClipDrawable的等级为0时，才能有裁剪行为

clip_vertical
附加选项，表示竖直方向的裁剪，较少使用

clip_horizontal
附加选项，表示水平方向的裁剪，较少使用


一张图片从上往下进行裁剪，demo：
```
// 首先定义ClipDrawable
<?xml version="1.0" encoding="utf-8"?>
<scale 
	xmlns:android="http://schemas.android.com/apk/res/android"
	android:drawable="@drawable/image1"
	android:clipOrientation="vertical"
	android:gravity="bottom" />

// 将ClipDrawable设置给ImageView 或者 作为普通View的背景
<ImageView 
	android:id="@+id/test_clip"
	android:layout_width="100dp"
	android:layout_height="100dp"
	android:src="@drawable/clip_drawable"
	android:gravity="center" />

// 接着再代码中设置ClipDrawable的等级
ImageView testClip = (ImageView) findViewById(R.id.test_clip);
ClipDrawable testClipDrawable = (ClipDrawable) testClip.getDrawable();
testClipDrawable.setLevel(8000);	// 裁剪20%的区域
```

Drawable的level范围为0~10000
对于ClipDrawable来说，0表示完全裁剪；10000表示不裁剪


## 3. 自定义Drawable
Drawable的使用范围很单一：一是作为ImageView中的图像显示；二是作为View的背景
大多数情况下Drawable都是以View的背景这种形式出现

Drawable的工作原理核心就是draw方法，我们可以重写Drawable#draw()来自定义Drawable

通常没必要自定义Drawable，因为自定义的Drawable无法在XML中使用

自定义Drawable来绘制一个圆形的Drawable，并且半径会随着View的变化而变化，Demo：
```
public CustomDrawable extends Drawable {
	private Paint mPaint;

	public CustomDrawable(int color) {
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPaint.setColor(color);
	}

	@Override
	public void draw(Canvas canvas) {
		final Rect r = getBounds();
		float cx = r.exactCenterX();
		float cy = r.exactCenterY();
		canvas.drawCircle(cx, cy, Math.min(cx, cy), mPaint);
	}

	@Override 
	public void setAlpha(int alpha) {
		mPaint.setAlpha(alpha);
		invalidateSelf();
	}

	@Override 
	public void setColorFilter(ColorFilter cf) {
		mPaint.setColorFilter(cf);
		invalidateSelf();
	}

	@Override
	public int getOpacity() {
		// not sure, so be safe
		return PixelFormat.TRANSLUCENT;
	}
}
```

上面的几个方法都是必须要实现的

另外，getInstrinsicWidth和getInstrinsicHeight这两个方法，
当自定义的Drawable有固定大小时最好重写这两个方法，因为它会影响到View的wrap_content布局。
（上面的例子中Drawable没有固定大小，因此不用重写这两个方法）

注意，内部大小不等于Drawable实际区域大小，实际区域大小可以通过Drawable#getBounds()方法得到




