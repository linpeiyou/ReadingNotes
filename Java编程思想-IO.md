# Java I/O系统

I/O设计的难点：
1.各种I/O源端和想要与之通信的接收端（文件、控制台、网络链接等）
2.不同的通信方式（顺序、随机存取、缓冲、二进制、按字符、按行、按字等）

从Java 1.0版本以来，Java的I/O类库发生了明显改变，在原来面向字节的类中添加了面向字符和基于Unicode的类
JDK 1.4中，添加了nio类，改进了性能和功能

## 1. File类

**File类既能代表一个特定文件的名称，又能代表一个目录下的文件的名称**

当File为文件集时，调用list()方法会返回一个字符数组

本节介绍这个类的用法，包括与之相关的FilenameFilter接口

### 1.1 目录列表器
FilenameFilter接口：
```
public interface FilenameFilter {
	boolean accept(File dir, String name);
}
```

File.list(FilenameFilter filter)：list()方法会回调accept()，进而决定哪些文件包含在列表中
这是一个策略模式的例子，因为list()实现了基本的功能，
而且按照FilenameFilter的形式提供了这个策略，以便完善list()在提供服务时所需的算法

### 1.2 目录实用工具



### 1.3 目录的检查及创建





## 2. 输入和输出


## 3. 添加属性和有用的接口



## 4. Reader和Writer























































