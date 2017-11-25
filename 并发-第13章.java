# 显式锁

Java 5.0之前，协调对共享对象的访问时有 synchronized 和 volatile。  
Java 5.0增加了一种新的机制：ReentrantLock  

ReentrantLock不是一种替代内置加锁的方法，而是当内置加锁机制不适用时，作为一种可选择的高级功能。  

## 1. Lock 与 ReentrantLock

与内置加锁机制不同的是，Lock 提供了一种无条件的、可轮询的、定时的以及可中断的锁获取操作，所有加锁和解锁的方式都是显示的。
```
public interface Lock {
	void lock();
	void lockInterruptibly() throws InterruptedException;
	boolean tryLock();
	boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException;
	void unlock();
	Condition newCondition();
}
```

ReentrantLock 实现了 Lock 接口，并提供了与 synchronized 相同的互斥性和内存可见性。
与 synchronized 一样，ReentrantLock 还提供了可重入的加锁语义。


so, 为什么要创建一种与内置所如此相似的新的加锁机制？
-- 在大多数情况下，内置锁都能很好地工作，但在功能上存在一些局限性。例如，无法中断一个正在等待获取锁的线程，或者无法在请求一个锁时无限地等待下去。内置锁必须在获取该锁的代码中释放，这简化了编码工作，并且与异常处理操作实现了很好的交互，但是无法实现非阻塞结构的加锁规则。这些都是使用 synchronized 的原因，但在某些情况下，一种更灵活的加锁机制通常能提供更好的活跃性和性能。


Lock 接口的标准使用形式：
```
Lock lock = new ReentrantLock();
...
lock.lock();
try {
	// 更新对象状态
	// 捕获异常，并在必要时恢复不变性条件
} finally {
	lock.unlock();
}
```

必须在finally块中释放锁，否则如果在被保护的代码中抛出了异常，那么这个锁永远都无法释放。
当使用加锁时，还必须考虑在try块中抛出异常的情况，如果可能使对象处于某种不一致的状态，那么就需要更多的 try-catch 或 try-finally 代码块。


### 1.1 轮询锁与定时锁

可定时的与可轮询的锁获取模式是由tryLock方法实现的，与无条件的锁获取模式相比，它具有更完善的错误恢复机制。

在内置锁中，死锁是一个严重的问题，恢复程序的唯一方法是重启程序，而防止死锁的唯一方法是在构造程序时避免出现不一致的锁顺序。
可定时的与可轮询的锁提供了另一种选择：避免死锁的发生。

```
public boolean transferMoney(Account fromAcct, Account toAcct, DollarAmount amount,
					long timeout, TimeUnit, unit) 
					throws InsufficientFundsException, InterruptedException {
	long fixedDelay = getFixedDelayComponentNanos(timeout, unit);
	long randMod = getRandomDelayModulusNanos(timeout, unit);
	long stopTime = System.nanoTime + unit.toNanos(timeout);

	while(true) {
		if(fromAcct.lock.tryLock()) {
			try {
				if(toAcct.lock.tryLock()) {
					try {
						if(fromAcct.getBalance().compareTo(amount) < 0) 
							throw new InsufficientFundsException();
						else {
							fromAcct.debit(amount);
							toAcct.credit(amount);
							return true;
						}
					} finally {
						toAcct.lock.unlock();
					}
				} 
			} finally {
				fromAcct.lock.unlock();
			}
		}
		if(System.nanoTime() < stopTime)
			return false;
		NANOSECONDS.sleep(fixedDelay + rnd.nextLong() % randMod);
	}
}

```


## 2. 性能考虑因素
在 Java 5.0 中，当从单线程（无竞争）变化到多线程时，内置锁的性能将急剧下降，而 ReentrantLock 的性能下降则更为平缓，因此它具有更好的伸缩性。
但在 Java 6 中，情况就完全不同了，内置锁的性能不会由于竞争而急剧下降，两者的可伸缩性变得基本相当了。


## 3. 公平性

在 ReentrantLock 的构造函数中提供了两种公平性选择：创建一个非公平的锁（默认）或者一个公平的锁。
在公平的锁上，线程将按照它们发出请求的顺序来获得锁，
但在非公平的锁上，则允许“插队”：当一个线程请求非公平的锁时，如果在发出请求的同时该锁的状态变为可用，那么这个线程将跳过队列中所有的等待线程并获取这个锁。


在激烈竞争的情况下，非公平锁的性能高于公平锁的性能的一个原因是：**在恢复一个被挂起的线程与该线程真正开始运行之间存在着严重的延迟。**
假设线程A持有一个锁，并且线程B请求这个锁。由于这个锁已被线程A持有，因此B将被挂起。当A释放锁时，B将被唤醒，因此会再次尝试获取锁。与此同时，如果C也请求这个锁，那么C很可能会在B被完全唤醒之前获得、使用以及释放这个锁。这样的情况是一种“双赢”的局面：B获得锁的时刻并没有推迟，C更早地获得了锁，并且吞吐量也获得了提高。

与默认的 ReentrantLock 一样，内置锁并不会提供确定的公平性保证。


## 4. 在 synchronized 和 ReentrantLock 之间进行选择

在一些内置锁无法满足需求的情况下，ReentrantLock可以作为一种高级工具。当需要一些高级功能时才应该使用 ReentrantLock，这些功能包括：可定时的、可轮询的与可中断的锁获取操作，公平队列，以及非块结构的锁。否则，还是应该优先使用 synchronized。


## 5. 读写锁

```
public interface ReadWriteLock {
	Lock readLock();
	Lock writeLock();
}
```

这两个锁只是同一个对象的不同视图。

在读-写锁实现的加锁策略中，允许多个读操作同时进行，但每次只允许一个写操作。

读-写锁是一种性能优化策略，在一些特定的情况下能实现更高的并发性。在实际情况中，对于在多处理器系统上被频繁读取的数据结构，读-写锁能够提高性能。而在其他情况下，读-写锁的性能比独占锁的性能要略差一些，这是因为它的复杂性更高。


在读取锁和写入锁之间的交互可以采用多种实现方式。ReadWriteLock中的一些可选实现包括：
- 释放优先。当一个写入操作释放写入锁时，并且队列中同时存在读线程和写线程，那么应该优先选择读线程，写线程，还是最先发出请求的线程？
- 读线程插队。如果锁是由读线程持有，但有写线程正在等待，那么新到达的读线程能否立即获得访问权，还是应该在写线程后面等待？如果允许读线程插队到写线程之前，那么将提高并发性，但却可能造成写线程发生饥饿问题。
- 重入性。读取锁和写入锁是否是可重入的？
- 降级。如果一个线程持有写入锁，那么它能否在不释放该锁的情况下获得读取锁？这可能会使得写入锁被“降级”为读取锁，同时不允许其他写线程修改被保护的资源。
- 升级。读取锁能否优先于其他正在等待的读线程和写线程而升级为一个写入锁？在大多数的读-写锁视线中并不支持升级，因为如果没有显式的升级操作，那么很容易造成死锁。（如果两个读线程试图同时升级为写入锁，那么二者都不会释放读取锁。）


ReentrantReadWriteLock为这两种锁都提供了可重入的加锁语义。
与ReentrantLock类似，ReentrantReadWriteLock在构造时也可以选择是一个非公平的锁（默认）还是一个公平的锁。
在公平的锁中，等待时间最长的线程将优先获得锁。如果这个锁由读线程持有，而另外一个线程请求写入锁，那么其他读线程都不能获得读取锁，直到写线程使用完并且释放了写入锁。
在非公平的锁中，线程获取访问许可的顺序是不确定的。写线程降级为读线程是可以的，但从读线程升级为写线程则是不可以的（这样做会导致死锁）。


在Java5.0中，读取锁的行为更类似于一个Semaphore而不是锁，它只维护活跃的读线程的数量，而不考虑它们的标识。在Java 6中修改了这个行为：记录哪些线程已经获得了读取锁。
做出这个修改的原因：在Java5.0的锁实现中，无法区别一个线程是首次请求读取锁，还是请求可重入锁，从而可能使公平的读-写锁发生死锁。


## 小结
1. 与内置锁相比，显式的Lock提供了一些扩展功能，在处理锁的不可用性方面有着更高的灵活性。但 ReentrantLock 不能完全替代 synchronized，只有在 synchronized 无法满足需求时，才应该使用它。
2. 读-写锁允许多个读线程并发地访问被保护的对象，当访问以读取操作为主的数据结构时，它能提高程序的可伸缩性。

