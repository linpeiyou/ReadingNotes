# 第2章 IPC机制

首先介绍Android中的多进程概念以及多进程模式中常见的注意事项
接着介绍Android中的序列化机制和Binder
然后详细介绍Bundle、文件共享、AIDL、Messenger、ContentProvider和Socket等进程间通信方式
为了更好地使用AIDL来进行进程间通信，本章还引入了Binder连接池的概念
最后讲解了各种进程间通信方式的优缺点和适用场景

## 1. Android IPC简介

IPC：Inter-Process Communication，含义为进程间通信或者跨进程通信




## 2. Android中的多进程模式

### 2.1 开启多进程模式

### 2.2 多进程模式的运行机制



## 3. IPC基础概念介绍

介绍IPC中一些基础概念：Serializable接口、Parcelable接口、Binder
只有熟悉这三方面的内容后，我们才能更好地理解跨进程通信的各种方式。

Serializable和Parcelable接口可以完成对象的序列化过程，
**当我们需要通过Intent和Binder传输数据时就需要使用Parcelable或者Serializable**
有时候我们需要**把对象持久化到存储设备上**或者**通过网络传输给其他客户端**，
这个时候也需要使用Serializable来完成对象的持久化


### 3.1 Serializable接口

Serializable是Java所提供的一个序列化接口，它是个空接口，为对象提供标准的序列化和反序列化操作
使用Serializable来实现序列化相当简单，只需要在类的声明中指定一个类似下面的标识即可自动实现默认的序列化过程：
private static final long serialVersionUID = 1;
这个serialVersionUID也不是必须的，不声明同样可以实现序列化，但是会对反序列化过程产生影响。

如User类就是一个实现了Serializable接口的类，它是可以被序列化和反序列化的：
```
public class User implements Serializable {
	private static final long serialVersionUID = 1L;

	public int 		userId;
	public String	userName;
	public boolean 	isMale;
}
```

进行对象的序列化和反序列化：
```
// 序列化过程
User user = new User(0, "jake", true);
ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("cache.txt"));
out.writeObject(user);
out.close();

// 反序列化过程
ObjectInputStream in = new ObjectInputStream(new FileInputStream("cache.txt"));
User newUser = (User) in.readObject();
in.close();
```
上述代码演示了采用Serializable方式序列化对象的典型过程，
很简单，只需要把实现了Serializable接口的User对象写到文件中就可以快速恢复了，
恢复后的对象newUser和user的内容完全一样，但是两者并不是同一个对象

**serialVersionUID是用来辅助序列化和反序列化过程的**
**原则上序列化后的数据中的serialVersionUID只有和当前类的serialVersionUID相同才能够正常地被反序列化**
**serialVersionUID的详细工作机制是这样的：**
序列化的时候系统会把当前类的serialVersionUID写入序列化的文件中（也可能是其他的中介），
当反序列化的时候系统会去检测文件中的serialVersionUID，看它是否和当前类的serialVersionUID一致，
如果一致就说明序列化的类的版本和当前类的版本是相同的，这个时候可以成功反序列化；
否则就说明当前类和序列化的类相比发生了某些变换，比如成员变量的数量、类型可能发生了改变，这个时候是无法正常序列化的，因此会报如下错误：
```
java.io.InvalidClassException: Main; local class incompatible: stream
classdesc serialVersionUID = 2, local class serialVersionUID = 1.
```

我们可以手动指定serialVersionUID的值（比如1L），也可以让编译器根据当前类的结构自动去生产它的hash值

我们手动指定serialVersionUID的时候，可以很大程度上避免反序列化过程的失败（从而避免crash）
比如版本升级后，我们可能删除了某个成员变量也可能添加了一些新的成员变量，
这个时候我们的反序列化过程仍然能够成功，程序仍然能够最大限度地恢复数据；
相反如果不指定serialVersionUID的话，程序则会发生crash

不过，如果我们修改了类名，或修改了成员变量的类型，这个时候尽管serialVersionUID验证通过了，
但是反序列化过程还是会失败，因为类结构有了毁灭性的改变，无法从老版本的数据中还原出一个新的类结构的对象

不参与序列化过程的变量：
1.静态成员变量（本身就属于类不属于对象）
2.用transient关键字标记的成员变量


### 3.2 Parcelable接口

Parcelable也是一个接口，只要实现这个接口，一个类的对象就可以实现序列化并可以通过Intent和Binder传递

demo：
```
public class User implements Parcelable {

	public int 		userId;
	public String 	userName;
	public boolean  isMale;

	public Book		book;

	public User(int userId, String userName, boolean isMale) {
		this.userId = userId;
		this.userName = userName;
		this.isMale = isMale;
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(userId);
		out.writeString(userName);
		out.writeInt(isMale ? 1 : 0);
		out.writeParcelable(book, 0);
	}

	public static final Parcelable.Creator<User> CREATOR = new Parcelable.Creator<User>() {
		public User createFromParcel(Parcel in) {
			return new User(in);
		}

		public User[] newArray(int size) {
			return new User[size];
		}
	};

	private User(Parcel in) {
		userId = in.readInt();
		userName = in.readString();
		isMale = in.readInt() == 1;
		book = in.readParcelable(Thread.currentThread().getContextClassLoader());
	}
}
```

Parcel内部包装了可序列化的数据，可以在Binder中自由传输。

序列化功能由writeToParcel方法来完成，最终是通过Parcel中的一系列write方法来完成的
反序列化功能由CREATOR来完成，其内部标明了如何创建序列化对象和数组，并通过Parcel的一系列read方法来完成反序列化过程
内容描述功能由describeContents方法来完成，几乎所有情况下这个方法都应该返回0，仅当对象中存在文件描述符时返回1.

要注意的是，在User(Parcel in)方法中，
由于book是另一个可序列化对象，所以它的反序列化过程需要传递当前线程的上下文类加载器，否则会报无法找到类的错误


#### Parcelable的方法说明：

createFromParcel(Parcel in)：从序列化后的对象中创建原始对象

newArray(int size)：创建指定长度的原始对象数组

User(Parcel in)：从序列化后的对象中创建原始对象

writeToParcel(Parcel out, int flags)：将当前对象写入序列化结构中，其中flags标识有两种值：0或者1。
为1时标识当前对象需要作为返回值返回，不能立即释放资源，几乎所有情况都为0。
标记位：PARCELABLE_WRITE_RETURN_VALUE（1）

describeContents()：返回当前对象的内容描述，如果含有文件描述符，返回1；否则返回0，几乎所有情况都返回0。
标记位：CONTENTS_FILE_DESCRIPTOR（1）


系统已经为我们提供了许多实现了Parcelable接口的类，它们都是可以直接序列化的，
如Intent、Bundle、Bitmap等，同时 List 和 Map 也可以序列化，前提是它们里面的每个元素都是可序列化的。


#### Serializable和Parcelable的区别
既然Parcelable和Serializable都能实现序列化并且都可以用于Intent间的数据传递，那么二者要怎么选取呢？

**Serializable是Java中的序列化接口，使用简单，开销很大，序列化和反序列化过程都需要大量I/O操作**
**Parcelable是Android中的序列化方式，更适合用在Android平台上，缺点是使用起来麻烦点，但是效率很高**

首选Parcelable。Parcelable主要用在内存序列化上，
通过Parcelable将对象序列化到存储设备或者将对象序列化后通过网络传输也是可以的，但是过程会比较复杂
所以在这两种情况下建议使用Serializable。

总结：
Parcelable：内存序列化
Serializable：将对象序列化到存储设备、将对象序列化后通过网络传输


### 3.3 Binder

本节不深入探讨Binder的底层细节，因为Binder太复杂了，侧重介绍Binder的使用和上层原理。

Binder是Android中的一个类，它实现了IBinder接口

1.从IPC的角度来说，Binder是Android中的一种跨进程通信方式，
Binder还可以理解为一种虚拟的物理设备，它的设备驱动的/dev/binder，该通信方式在Linux中没有

2.从Android Framework角度来说，
Binder是ServiceManager连接各种Manager（ActivityManager、WindowManager等）和相应ManagerService的桥梁

3.从Android应用层来说，Binder是客户端和服务端进行通信的媒介，
当bindService的时候，服务端会返回一个包含了服务端业务调用的Binder对象，
通过这个Binder对象，客户端就可以获取服务端提供的服务或者数据，这里的服务包括普通服务和基于AIDL的服务


Android开发中，Binder主要用在Service中，包括AIDL和Messenger
其中普通Service中的Binder不涉及进程间通信，所以较为简单，无法触及Binder的核心，
而Messenger的底层其实是AIDL，所以这里选择用AIDL来分析Binder的工作机制

为了分析Binder的工作机制，我们需要新建一个AIDL示例，
SDK会自动为我们生成AIDL所对应的Binder类，然后我们就可以分析Binder的工作流程。

Demo：新建Java包com.ryg.chapter_2.aidl
然后新建三个文件Book.java, Book.aidl, IBookManager.aidl，代码如下：
```
// Book.java
package com.ryg.chapter_2.aidl;

import android.os.Parcel;
import android.os.Parcelable;

public class Book implements Parcelable {
	public int bookId;
	public String bookName;

	public Book(int bookId, String bookName) {
		this.bookId = bookId;
		this.bookName = bookName;
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(bookId);
		out.writeString(bookName);
	}

	public static final Parcelable.Creator<Book> CREATOR = new Parcelable.Creator<Book>() {
		public Book createFromParcel(Parcel in) {
			return new Book(in);
		}

		public Book[] newArray(int size) {
			return new Book[size];
		}
	};

	private Book(Parcel in) {
		bookId = in.readInt();
		bookName = in.readString();
	}
}

// Book.aidl
package com.ryg.chapter_2.aidl;

parcelable Book;

// IBookManager.aidl
package com.ryg.chapter_2.aidl;

import com.ryg.chapter_2.aidl.Book;

interface IBookManager {
	List<Book> getBookList();
	void addBook(in Book book);
}
```

上面三个文件中，
Book.java 是一个表示图书信息的类，它实现了Parcelable接口
Book.aidl 是Book类在AIDL中的声明
IBookManager.aidl 是我们定义的一个接口，里面有两个方法：getBookList()和addBook()
其中getBookList()用于从远程服务端获取图书列表，而addBook()用于往图书列表中添加一本书

我们可以看到，尽管Book类和IBookManager位于相同的包中，
但是在IBookManager中仍然要导入Book类，这就是AIDL的特殊之处。


下面我们看一下系统为IBookManager.aidl生成的Binder类，根据这个Binder类来分析Binder的工作原理：
```
// IBookManager.java
/**
 * This file is auto-generated. DO NOT MODIFY.
 * Original file: E:\\workspace\\Chapter_2\\src\\com\\ryg\\chapter_2\\aidl\\IBookManager.aidl
 */
package com.ryg.chapter_2.aidl;

public interface IBookManager extends android.os.IIterface {

	/** Local-side IPC implementation stub class. */

	public static abstract class Stub extends android.os.Binder 
		implements com.ryg.chapter_2.aidl.IBookManager {

		private static final java.lang.String DESCRIPTOR = "com.ryg.chapter_2.aidl.IBookManager";

		/** Constructor the stub at attach it to the interface. */
		public Stub() {
			this.attachInterface(this, DESCRIPTOR);
		}

		/**
		 * Cast an IBinder object into an com.ryg.chapter_2.aidl.IBookManager interface,
		 * generating a proxy if needed.
		 */
		public static com.ryg.chapter_2.aidl.IBookManager asInterface(android.os.IBinder obj) {
			if((obj == null)) {
				return null;
			}
			android.os.IIterface iin = obj.queryLocalInterface(DESCRIPTOR);
			if(((iin != null) && (iin instanceof com.ryg.chapter_2.aidl.IBookManager))) {
				return ((com.ryg.chapter_2.aidl.IBookManager) iin);
			}
			return new com.ryg.chapter_2.aidl.IBookManager.Stub.Proxy(obj);
		}

		@Override
		public android.os.IBinder asBinder() {
			return this;
		}

		@Override
		public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) 
			throws android.os.RemoteException {

			switch(code) {
				case INTERFACE_TRANSACTION: {
					reply.writeString(DESCRIPTOR);
					return true;
				}

				case TRANSACTION_getBookList: {
					data.enforceInterface(DESCRIPTOR);
					java.util.List<com.ryg.chapter_2.aidl.Book> _result = this.getBookList();
					reply.writeNoException();
					reply.writeTypedList(_result);
					return true;
				}

				case TRANSACTION_addBook: {
					data.enforceInterface(DESCRIPTOR);
					com.ryg.chapter_2.aidl.Book _arg0;
					if((0 != data.readInt())) {
						_arg0 = com.ryg.chapter_2.aidl.Book.CREATOR.createFromParcel(data);
					} else {
						_arg0 = null;
					}
					this.addBook(_arg0);
					reply.writeNoException();
					return true;
				}
			}
			return super.onTransact(code, data, reply, flags);
		}

		private static class Proxy implements com.ryg.chapter_2.aidl.IBookManager {
			private android.os.IBinder mRemote;

			Proxy(android.os.IBinder remote) {
				mRemote = remote;
			}

			@Override
			public android.os.IBinder asBinder() {
				return mRemote;
			}

			public java.lang.String getInterfaceDescriptor() {
				return DESCRIPTOR;
			}

			@Override
			public java.util.List<com.ryg.chapter_2.aidl.Book> getBookList()
				throws android.os.RemoteException {

				android.os.Parcel _data = android.os.Parcel.obtain();
				android.os.Parcel _reply = android.os.Parcel.obtain();
				java.util.List<com.ryg.chapter_2.aidl.Book> _result;
				try {
					_data.writeInterfaceToken(DESCRIPTOR);
					mRemote.transact(Stub.TRANSACTION_getBookList, _data, _reply, 0);
					_reply.readException();
					_result = _reply.createTypedArrayList(com.ryg.chapter_2.aidl.Book.CREATOR);
				} finally {
					_reply.recycle();
					_data.recycle();
				}
			}

			@Override
			public void addBook(com.ryg.chapter_2.aidl.Book book)
				throws android.os.RemoteException {

				android.os.Parcel _data = android.os.Parcel.obtain();
				android.os.Parcel _reply = android.os.Parcel.obtain();

				try {
					_data.writeInterfaceToken(DESCRIPTOR);
					
				}
			}
		}

		static final int TRANSACTION_getBookList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);

		static final int TRANSACTION_addBook = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
	}

	public java.util.List<com.ryg.chapter_2.aidl.Book> getBookList()
		throws android.os.RemoteException;

	public void addBook(com.ryg.chapter_2.aidl.Book book)
		throws android.os.RemoteException;
}
```

根据IBookManager.aidl系统为我们生成了IBookManager.java这个接口，它继承了IInterface这个接口
所有可以在Binder中传输的接口都需要继承IInterface接口

这个类声明了两个方法：getBookList() 和 addBook()
同时声明了两个整型的id分别用于标识这两个方法，这两个id用于标识在transact过程中客户端所请求的到底是哪个方法

接着声明了一个内部类Stub，这个Stub就是一个Binder类
当客户端和服务端都位于同一个进程时，方法调用不会走跨进程的transact过程
当两者位于不同的进程时，方法调用需要走transact过程，这个逻辑由Stub的内部代理类Proxy来完成







































## 4. Android中的IPC方式


### 4.1 使用Bundle


### 4.2 使用文件共享


### 4.3 使用Messenger


### 4.4 使用AIDL


### 4.5 使用ContentProvider


### 4.6 使用Socket



## 5. Binder连接池


## 6. 选用合适的IPC方式














































