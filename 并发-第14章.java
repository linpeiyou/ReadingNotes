# 构建自定义的同步工具

类库包含了许多存在状态依赖性的类，例如 FutureTask, Semaphore, BlockingQueue等。
这些类的一些操作中有基于状态的前提条件，例如，不能从空队列中删除元素，不能获取一个尚未结束的任务的计算结果。

创建状态依赖类的最简单方法通常是在类库中现有状态依赖类的基础上进行构造。
但如果类库中没有提供你需要的功能，那么还可以使用Java语言和类库提供的底层机制来构造自己的同步机制，包括内置的条件队列、显式的 Condition 对象以及 AbstractQueuedSynchronizer 框架。

本章将介绍实现状态依赖性的各种选择，以及在使用平台提供的状态依赖性机制时需要遵守的各项规则。


## 1. 状态依赖性的管理

在单线程程序中调用一个方法时，如果某个基于状态的前提条件未得到满足，那么这个条件永远无法成真。因此，在编写顺序程序中的类时，要使得这些类在它们的前提条件未被满足时就失败。
但在并发程序中，基于状态的条件可能会由于其他线程的操作而改变。对于并发对象上依赖状态的方法，虽然有时候在前提条件不满足的情况下不会失败，但通常有一种更好的选择，即等待前提条件变成真。

通过轮询与休眠等方式来（勉强地）解决状态依赖性问题。

```
acquire lock on object state
while(precondition does not hold) {
	release lock
	wait until precondition might hold
	optionally fail if interrupted or timeout expires
	reacquire lock
}
perform action
	release lock
```

在生产者-消费者的设计中经常会使用像 ArrayBlockingQueue 这样的有界缓存。在有界缓存提供的 put 和 take 操作中都包含有一个前提条件：不能从空缓存中获取元素，也不能将元素放入已满的缓存中。
当前提条件未满足时，依赖状态的操作可以抛出一个异常或返回一个错误状态（使其成为调用者的一个问题），也可以保存阻塞直到对象进入正确的状态。


接下来介绍有界缓存的几种实现，其中将采用不同的方法来处理前提条件失败的问题。
每种实现中都扩展了 BaseBoundedBuffer

程序清单14-2 有界缓存实现的基类
```
@ThreadSafe
public abstract class BaseBoundedBuffer<V> {
	@GuardedBy("this") private final V[] buf;
	@GuardedBy("this") private int tail;
	@GuardedBy("this") private int head;
	@GuardedBy("this") private int count;

	protected BaseBoundedBuffer(int capacity) {
		this.buf = (V[]) new Object[capacity];
	}

	protected synchronized final void doPut(V v) {
		buf[tail] = v;
		if(++tail == buf.length)
			tail = 0;
		++count;
	}

	protected synchronized final V doTake() {
		V v = buf[head];
		buf[head] = null;
		if(++head == buf.length)
			head = 0;
		--count;
		return v;
	}

	public synchronized final boolean isFull() {
		return count == buf.length;
	}

	public synchronized final boolean isEmpty() {
		return count == 0;
	}
}
```


### 1.1 示例：将前提条件的失败传递给调用者

程序清单14-3 当不满足前提条件时，有界缓存不会执行相应的操作
```
@ThreadSafe
public class GrumpyBoundedBuffer<V> extends BaseBoundedBuffer<V> {
	public GrumpyBoundedBuffer(int size) {
		super(size);
	}

	public synchronized void put(V v) throws BufferFullException {
		if(isFull()) 
			throw new BufferFullException();
		doPut(v);
	}
}
```
这种方法实现起来很简单，但使用起来却并非如此。异常应该用于发生异常条件的情况中，“缓存已满”并不是有界缓存的一个异常条件，就像”红灯“并不表示交通信号灯出现了异常。
在实现缓存时得到的简化并不能抵消在使用时存在的复杂性。


程序清单14-4 调用 GrumpyBoundedBuffer 的代码
```
while(true) {
	try {
		V item = buffer.take();
		// 对 item 执行一些操作
		break;
	} catch(BufferEmptyException e) {
		Thread.sleep(SLEEP_GRANULARITY);
	}
}
```


### 1.2 示例：通过轮询与休眠来实现简单的阻塞

程序清单14-5 使用简单阻塞实现的有界缓存
```
@ThreadSafe
public class SleepyBoundedBuffer<V> extends BaseBoundedBuffer<V> {
	public SleepyBoundedBuffer(int size) {
		super(size);
	}

	public void put(V v) throws InterruptedException {
		while(true) {
			synchronized(this) {
				if(!isFull()) {
					doPut(v);
					return;
				}
			}
			Thread.sleep(SLEEP_GRANULARITY);
		}
	}

	public V take() throws InterruptedException {
		while(true) {
			synchronized(this) {
				if(!isEmpty()) {
					return doTake();
				}
			}
			Thread.sleep(SLEEP_GRANULARITY);
		}
	}
}
```


### 1.3 条件队列

“条件队列”这个名字来源于：它使得一组线程（称之为等待线程集合）能够通过某种方式来等待特定的条件变成真。传统队列的元素是一个个数据，而与之不同的是，条件队列中的元素是一个个正在等待相关条件的线程。

Object.wait会自动释放锁，并请求操作系统挂起当前线程，从而使其他线程能够获得这个锁并修改对象的状态。当被挂起的线程醒来时，它将在返回之前重新获取锁。

在程序清单14-6的BoundedBuffer中使用了wait和notifyAll来实现一个有界缓存。
这比使用“休眠”的有界缓存更简单，并且更高效（当缓存状态没有发生变化时，线程醒来的次数将更少），响应性也更高（当发生特定状态变化时将立即醒来）。
这是一个较大的改进，但要注意：与使用“休眠”的有界缓存相比，条件队列并没有改变原来的语义。它只是在多个方面进行了优化：CPU效率、上下文切换开销和响应性等。

程序清单14-6 使用条件队列实现的有界缓存
```
@ThreadSafe
public class BoundedBuffer<V> extends BaseBoundedBuffer<V> {
	// 条件谓词：not-full (!isFull())
	// 条件谓词：not-empty (!isEmpty())

	public BoundedBuffer(int size) {
		super(size);
	}

	// 阻塞并直到：not-full
	public synchronized void put(V v) throws InterruptedException {
		while(isFull())
			wait();
		doPut(v);
		notifyAll();
	}

	// 阻塞并直到：not-empty
	public synchronized V take() throws InterruptedException {
		while(isEmpty())
			wait();
		V v = doTake();
		notifyAll();
		return v;
	}
}
```

BoundedBuffer变得足够好了，不仅简单易用，而且实现了明晰的状态依赖性管理。在产品的正式版本中还应包括限时版本的put和take，这样当阻塞操作不能在预定时间内完成时，可以因超时而返回。通过使用定时版本的Object.wait，可以很容易实现这些方法。



## 2. 使用条件队列

条件队列使构建高效以及高可响应性的状态依赖类变得更容易，但同时也很容易被不正确地使用。
虽然许多规则都能确保正确地使用条件队列，但在编译器或系统平台上却并没有强制要求遵循这些规则。（这也是为什么要尽量基于 LinkedBlockingQueue, Latch, Semaphore 和 FutureTask 等类来构造程序的原因之一，如果能避免使用条件队列，那么实现起来将容易许多。）


### 2.1 条件谓词
要想正确地使用条件队列，关键是找出对象在哪个条件谓词上等待。
条件谓词将在等待与通知等过程中导致许多困惑，因为在API中没有对条件谓词进行实例化的方法，并且在Java语言规范或JVM实现中也没有任何信息可以确保正确地使用它们。

在条件等待中存在一种重要的三元关系，包括**加锁**、**wait方法**和一个**条件谓词**。
在条件谓词中包含多个状态变量，而状态变量由一个锁来保护，因此在测试条件谓词之前必须先持有这个锁。锁对象与条件队列对象（即调用 wait 和 notify 等方法的对象）必须是同一个对象。

wait方法将释放锁，阻塞当前线程，并等待直到超时，然后线程被中断或者通过一个通知被唤醒。
在唤醒进程后，wait在返回前还要重新获取锁。当线程从wait方法中被唤醒时，它在重新请求锁时不具有任何特殊的优先级，而要与任何其他尝试进入同步代码块的线程一起正常地在锁上竞争。

每一次wait调用都会隐式地与特定的条件谓词关联起来。当调用某个特定条件谓词的wait时，调用者必须已经持有与条件队列相关的锁，并且这个锁必须保护着构成条件谓词的状态变量。


### 2.2 过早唤醒

**wait方法的返回并不一定意味着线程正在等待的条件谓词已经变成真了**

内置条件队列可以与多个条件谓词一起使用。当一个线程由于调用notifyAll而醒来时，并不意味该线程正在等待的条件谓词已经变成真了。

另外，wait方法还可以“假装”返回，而不是由于某个线程调用了notify。——虚假唤醒

基于这些原因，每当线程从wait中唤醒时，都必须再次测试条件谓词，如果条件谓词不为真，那么就继续等待（或者失败）。由于线程在条件谓词不为真的情况下也可以反复地醒来，因此必须在一个循环中调用wait，并且每次迭代中都测试谓词。

程序清单14-7 状态依赖方法的标准形式
```
void stateDependentMethod() throws InterruptedException {
	// 必须通过一个锁来保护条件谓词
	synchronized(lock) {
		while(!conditionPredicate()) {
			lock.wait();
		}
		// 现在对象处于合适的状态
	}
}
```

当使用条件等待时（例如 Object.wait 或 Condition.await）：
- 通常都有一个条件谓词——包括一些对象状态的测试，线程在执行前必须首先通过这些测试。
- 在调用 wait 之前测试条件谓词，并且从 wait 中返回时再次进行测试。
- 在一个循环中调用 wait。
- 确保使用与条件队列相关的锁来保护构成条件谓词的各个状态变量。
- 当调用 wait, notify 或 notifyAll 等方法时，一定要持有与条件队列相关的锁。
- 在检查条件谓词之后以及开始执行响应的操作之前，不要释放锁


### 2.3 丢失的信号

第10章曾经讨论过活跃性故障，例如死锁和活锁。另一种形式的活跃性故障是丢失的信号。
丢失的信号是指：线程必须等待一个已经为真的条件，但在开始等待之前没有检查条件谓词。现在，线程将等待一个已经发生过的事件。

这就好比在启动了烤面包机后出去拿报纸，当你还在屋外时烤面包机的铃声响了，但你没有听到，因此还会坐在厨房的桌子前等着烤面包机的铃声。你可能会等待很长的时间。

如果线程A通知了一个等待队列，而线程B随后在这个等待队列上等待，那么线程B将不会立即醒来，而是需要另外一个通知来唤醒它。如果出现了编码错误（例如，没有在调用 wait 之前检测条件谓词）就会导致信号的丢失。


### 2.4 通知

每当在等待一个条件时，一定要确保在条件谓词变成真时通过某种方式发出通知。

在条件队列API中有两个发出通知的方法，即notify和notifyAll。无论调用哪一个，都必须持有与条件队列对象相关联的锁。
在调用notify时，JVM会从这个条件队列上等待的多个线程中选择一个来唤醒，而调用notifyAll则会唤醒所有在这个条件队列上等待的线程。
由于在调用notify和notifyAll时必须持有条件队列对象的锁，而如果这些等待中线程此时不能重新获得锁，那么无法从wait返回，因此发出通知的线程应该尽快地释放锁，从而确保正在等待的线程尽可能快地解除阻塞。

由于多个线程可以基于不同的条件谓词在同一个条件队列上等待，因此如果使用notify而不是notifyAll，那么将是一种危险的操作，因为单一的通知很容易导致类似于信号丢失的问题。


只有同时满足以下两个条件时，才能使用单一的notify而不是notifyAll：
1. 所有等待线程的类型都相同。只有一个条件谓语与条件队列相关，并且每个线程在从wait返回后将执行相同的操作。
2. 单进单出。在条件变量上的每次通知，最多只能唤醒一个线程来执行。

BoundedBuffer满足“单进单出”的条件，但不满足“所有等待线程的类型都相同”的条件，因为正在等待的线程可能在等待“非满”，也可能在等待“非空”。
在第5章的TestHarness中使用的“开始阀门”闭锁（单个事件释放一组线程）并不满足“单进单出”的需求，因为这个“开始阀门”将使得多个线程开始执行。


在BoundedBuffer的put和take方法中采用的通知机制是保守的：每当将一个对象放入缓存或者从缓存中移走一个对象时，就执行一次通知。
我们可以对其进行优化：首先，仅当缓存从空变为非空，或者从满转为非满时，才需要释放一个线程。并且，仅当put或take影响到这些状态转换时，才发出通知。

程序清单14-8 在BoundedBuffer.put中使用条件通知
```
public synchronized void put(V v) throws InterruptedException {
	while(isFull()) 
		wait();
	boolean wasEmpty = isEmpty();
	doPut(v);
	if(wasEmpty) 
		notifyAll();
}
```

### 2.5 示例：阀门类



### 2.6 子类的安全问题



### 2.7 封装条件队列



### 2.8 入口协议与出口协议





## 3. 显式的 Condition 对象





## 4. Synchronizer 剖析




## 5. AbstractQueuedSynchronizer



## 6. java.util.concurrent 同步器类中的 AQS


### 6.1 ReentrantLock


### 6.2 Semaphore 与 CountDownLatch


### 6.3 FutureTask


### 6.4 ReentrantReadWriteLock





## 小结






















