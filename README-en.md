<h4 align="right">
  <strong>简体中文</strong> | <a href="https://github.com/FlyJingFish/EasyRegister/blob/master/README.md">English</a>
</h4>


<p align="center">
  <strong>
    🔥🔥🔥这是一个万能的注册代码的框架插件
  </strong>
</p>

<p align="center">
   <a href="https://central.sonatype.com/artifact/io.github.flyjingfish/easyregister"><img
    src="https://img.shields.io/maven-central/v/io.github.flyjingfish/easyregister"
    alt="Build"
  /></a>
  <a href="https://github.com/FlyJingFish/EasyRegister/stargazers"><img
    src="https://img.shields.io/github/stars/FlyJingFish/EasyRegister.svg"
    alt="Downloads"
  /></a>
  <a href="https://github.com/FlyJingFish/EasyRegister/network/members"><img
    src="https://img.shields.io/github/forks/FlyJingFish/EasyRegister.svg"
    alt="Python Package Index"
  /></a>
  <a href="https://github.com/FlyJingFish/EasyRegister/issues"><img
    src="https://img.shields.io/github/issues/FlyJingFish/EasyRegister.svg"
    alt="Docker Pulls"
  /></a>
  <a href="https://github.com/FlyJingFish/EasyRegister/blob/master/LICENSE"><img
    src="https://img.shields.io/github/license/FlyJingFish/EasyRegister.svg"
    alt="Sponsors"
  /></a>
</p>



# 简述

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; 这是一个轻松注册代码的插件，不仅适配了AGP8，更提高了多数注册代码插件的速度

## 特色功能

1、本库支持提前埋入锚点代码

2、本库支持后置埋入锚点代码

3、本库支持正则表达式和探索继承的方式查找要注册的代码

4、两种埋入锚点的方式相较于 AGP8 均可得到速度提升

5、本框架提供了市面上几个 Router 库的插件配置 json ，可快速使用[位于此处](https://github.com/FlyJingFish/EasyRegister/tree/master/routerJson)

### 版本限制

版本支持AGP8.0以上

1、如果提前埋入锚点代码，版本要求无限制

2、如果后置埋入锚点代码，版本要求 7.6 以上

3、如果您选择锚点代码采用反射的方式，类似于WMRouter那种，版本要求无限制

## Star趋势图

[![Stargazers over time](https://starchart.cc/FlyJingFish/EasyRegister.svg?variant=adaptive)](https://starchart.cc/FlyJingFish/EasyRegister)

---

## 使用步骤

**在开始之前可以给项目一个Star吗？非常感谢，你的支持是我唯一的动力。欢迎Star和Issues!**


### 1、引入插件，下边两种方式二选一（必须）


在<strong>项目根目录</strong>的 <code>build.gradle</code> 里依赖插件

- 新版本

  ```gradle
  
  plugins {
      //必须项 👇 注意 apply 设置必须为 true 
      id "io.github.flyjingfish.easyregister" version "1.1.0" apply false
  }
  ```

- 或者老版本

  ```gradle
    buildscript {
        dependencies {
            //必须项 👇
            classpath 'io.github.flyjingfish:easyregister:1.1.0'
        }
    }
    ```

### 2、在 app 模块引入插件（必须）

```gradle
plugins {
    id 'easy.register'
}

```

### 3、配置织入代码

- 在项目根目录的 `gradle.properties` 中加入以下配置，example.json 放在和 `gradle.properties` 的同级目录下

```properties
easyRegister.configJson = example.json
```

- 参数说明：
```json
[
  {
    "wovenClass": "注入的类名",
    "wovenMethod": "注入的方法名包括参数类型和返回类型",//例如void register(String)
    "createWovenClass": false,//注入的类是否要新建出来
    "insertBefore": true,//是否插入到方法前边
    "searchClass": {
      "regex": "正则表达式",//查找的类，使用正则表达式来 匹配类名（regex和extendsClass必须至少填写一个，或者都写）
      "extendsClass": "继承的类名", //查找的类，继承类或接口的类名
      "callType": "调用搜索到的类型，caller，callee",//caller表示调用查找到的类的 callMethod，callee表示调用 callClass 的 callMethod 传入查找到的类
      "callClass": "调用的的类名如果是caller不填，如果是callee就填 callMethod 相应的类类名",
      "callMethod": "调用的的方法名包括参数类型和返回类型",//例如void register(String)
      "callMethodValue": "调用的的方法填写参数,searchClass 就是当前数据，$n就是注入方法的参数,n代表第几个",// 填写 searchClass 就是使用查找到的类；填写 $n 就是使用 wovenMethod 的第几个参数
      "useType": "使用的类型，className，new，class，如果是callee必填，否则不填",//如果是 caller 不需要填写，如果是callee，当callMethodValue填写searchClass时，className就是类名字符串，new 就是创建对象，class就是类的class对象
    }

  }
]
```

使用案例[点此查看](https://github.com/FlyJingFish/EasyRegister/blob/master/routerJson/)

- 其他配置

```properties
#是否启用本插件
easyRegister.enable = true
#启用什么模式 auto 只在debug模式启用优化，debug 是指会一直启用优化，release 是指不启用优化
easyRegister.mode = auto //auto、debug、release
# easyRegister.mode = release 或 auto(release) 时设置为 true 可以增量快速编译
easyRegister.releaseMode.fastDex = true
```



## 使用本库作为自己插件库的类库而不是插件

### 1、在 你的核心Android类库 模块引入插件


```gradle
plugins {
    id 'easy.register.library'
}

```

引入此插件可以预埋锚点代码到 aar 包中，选择使用这种方式，意味着你想自己编写插件，前两步的配置换成你自己的，你依旧可以在自己的插件库中引入第一步的插件，复用里边的逻辑

### 2、在 你的插件库的类库中引入插件


```gradle

dependencies {
    implementation 'io.github.flyjingfish:easyregister:1.1.0'
}
```

### 赞赏

都看到这里了，如果您喜欢 EasyRegister，或感觉 EasyRegister 帮助到了您，可以点右上角“Star”支持一下，您的支持就是我的动力，谢谢～ 😃

如果感觉 EasyRegister 为您节约了大量开发时间、为您的项目增光添彩，您也可以扫描下面的二维码，请作者喝杯咖啡 ☕


<div>
<img src="https://github.com/FlyJingFish/EasyRegister/blob/master/screenshot/IMG_4075.PNG" width="280" height="350">
<img src="https://github.com/FlyJingFish/EasyRegister/blob/master/screenshot/IMG_4076.JPG" width="280" height="350">
</div>


### 最后推荐我写的另外一些库

- [AndroidAOP 帮助 Android App 改造成AOP架构的框架，只需一个注解就可以请求权限、切换线程、禁止多点、一次监测所有点击事件、监测生命周期等等](https://github.com/FlyJingFish/AndroidAOP)

- [OpenImage 轻松实现在应用内点击小图查看大图的动画放大效果](https://github.com/FlyJingFish/OpenImage)

- [ShapeImageView 支持显示任意图形，只有你想不到没有它做不到](https://github.com/FlyJingFish/ShapeImageView)

- [GraphicsDrawable 支持显示任意图形，但更轻量](https://github.com/FlyJingFish/GraphicsDrawable)

- [ModuleCommunication 解决模块间的通信需求，更有方便的router功能](https://github.com/FlyJingFish/ModuleCommunication)

- [FormatTextViewLib 支持部分文本设置加粗、斜体、大小、下划线、删除线，下划线支持自定义距离、颜色、线的宽度；支持添加网络或本地图片](https://github.com/FlyJingFish/FormatTextViewLib)

- [主页查看更多开源库](https://github.com/FlyJingFish)

