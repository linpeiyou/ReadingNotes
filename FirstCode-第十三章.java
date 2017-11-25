# 继续进阶——你还应该掌握的高级技巧

## 1. 全局获取Context的技巧

## 2. 使用Intent传递对象

## 3. 定制自己的日志工具

## 4. 调试Android程序

## 5. 创建定时任务

Android中的定时任务一般有两种实现方式
一种是使用Java API里提供的 Timer 类
一种是使用Android的 Alarm 机制

Timer有一个明显的短板，它并不太适用于那些需要长期在后台运行的定时任务。

为了能让电池更加耐用，每种手机都会有自己的休眠策略
Android手机就会在长时间不操作的情况下自动让CPU进入到睡眠状态
这就有可能导致Timer中的定时任务无法正常运行

而Alarm则具有唤醒CPU的功能，它可以保证在大多数情况下需要执行定时任务的时候CPU都能正常工作。


### 5.1 Alarm机制

Alarm机制主要就是借助了AlarmManger类来实现的。
这个类和NotificationManager有点类似，都是通过调用Context的getSystemService()方法来获取实例的
这里要传入Context.ALRAM_SERVICE：
```
AlarmManager manager = (AlarmManager) getSystemService(Context.ALRAM_SERVICE);
```

demo:设定一个任务在10秒钟后执行：
```
long triggerAtTime = SystemClock.elapsedRealtime() + 10 * 1000;
manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, pendingIntent);
```

**第一个参数：**
指定了AlarmManager的工作类型，有4种类型可选：
ELAPSED_REALTIME：让定时任务的触发时间从系统开机开始算起，但不会唤醒CPU
ELAPSED_REALTIME_WAKEUP：触发时间同上，会唤醒CPU
RTC：让定时任务的触发时间从1970年1月1日0点开始算起，但不会唤醒CPU
RTC_WAKEUP：触发时间同上，会唤醒CPU

使用SystemClock.elapsedRealtime()方法可以获取到系统开机至今所经历时间的毫秒数
使用System.currentTimeMills()方法可以获取到1970年1月1日0点至今所经历时间的毫秒数

**第二个参数：**
定时任务触发时间，单位为毫秒

**第三个参数：**
这里我们一般会调用getService()方法或者getBroadcast()方法来获取一个能够执行服务或广播的PendingIntent。
这样当定时任务被触发的时候，服务的onStartCommand()方法或广播接收器的onReceive()方法就能得到执行。


Demo：实现一个长时间在后台定时运行的服务
首先新建一个普通的服务，然后将定时触发任务的代码写到onStartCommand()方法中
```
public class LongRunningService extends Service {
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				// 在这里执行具体的逻辑操作
			}
		}).start();
		AlarmManager manager = (AlarmManager) getSystemService(ALRAM_SERVICE);
		int anHour = 60 * 60 * 1000; // 一小时的毫秒数
		long triggerAtTime = SystemClock.elapsedRealtime() + anHour;
		Intent i = new Intent(this, LongRunningService.class);
		PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
		manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtTime, pi);
		return super.onStartCommand(intent, flags, startId);
	}
}
```

最后，只要在想要启动定式服务的时候调用如下代码即可：
```
Intent intent = new Intent(context, LongRunningService.class);
context.startService(intent);
```

需要注意的是，从Android 4.4系统开始，Alarm任务的触发时间将会变得不准确，有可能会延迟一段时间后任务才能得到执行
这并不是个bug，而是系统在耗电性方面进行的优化。
系统会自动检测目前有多少Alarm任务存在，然后将触发时间相近的几个任务放在一起执行，
这就可以大幅度地减少CPU被唤醒的次数，从而有效延长电池的使用时间。

如果要求Alarm任务的执行时间必须准确无误，可以使用AlarmManager的**setExact()**方法来替代set()方法


### 5.2 Doze模式
为了解决后台服务泛滥、手机电量消耗过快的问题
在Android 6.0系统中，Google加入了一个全新的Doze模式，可以大幅度地延长电池的使用寿命

Doze模式：
当用户的设备是Android 6.0或以上系统时，
如果该设备未插接电源，处于静止状态（Android 7.0中删除了这一条件），
且屏幕关闭了一段时间之后，就会进入到Doze模式。
在Doze模式下，系统会对CPU、网络、Alarm等活动进行限制，从而延长了电池的使用寿命。

当然，系统不会一直处于Doze模式，而是会间歇性地退出Doze模式一小段时间，
在这段时间中，应用就可以去完成它们的同步操作、Alarm任务等。

随着设备进入Doze模式的时间越长，间歇性地退出Doze模式的时间间隔也会越长。
因为设备长时间不使用的话，是没必要频繁退出Doze模式来执行同步等操作的。

Doze模式下的功能限制：
1、网络访问被禁止
2、系统忽略唤醒CPU或者屏幕操作
3、系统不再执行WIFI扫描
4、系统不再执行同步服务
5、Alarm任务将会在下次退出Doze模式的时候执行

注意最后一条，在Doze模式下，我们的Alarm任务将会变得不准时。

如果要使得Alarm任务即使在Doze模式下也必须正常运行，
调用AlarmManager的setAndAllowWhileIdle()或setExactAndAllowWhileIdle()方法，
就能让定时任务即使在Doze模式下也能正常执行了。

这两个方法之间的区别和set()、setExact()方法之间的区别是一样的。






































## 6. 多窗口模式编程

## 7. Lambda表达式

## 8. 总结