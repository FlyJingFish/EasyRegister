<h4 align="right">
  <strong>English</strong> | <a href="https://github.com/FlyJingFish/EasyRegister/blob/master/README.md">简体中文</a>
</h4>


<p align="center">
  <strong>
  🔥🔥🔥This is a universal registration code framework plug-in
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

# Brief description

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; This is an easy registration code plug-in, which not only adapts to AGP8, but also improves the speed of most registration code plug-ins

## Features

1. This library supports pre-embedded anchor code

2. This library supports post-embedded anchor code

3. This library supports regular expressions and exploration inheritance to find the code to be registered

4. Both methods of embedding anchors can improve the speed compared to AGP8

5. This framework provides several Router on the market The plugin configuration json of the library can be used quickly [located here](https://github.com/FlyJingFish/EasyRegister/tree/master/routerJson)

### Version restrictions

The version supports AGP8.0 and above

1. If the anchor code is embedded in advance, the version requirement is unlimited

2. If the anchor code is embedded in the back, the version requirement is 7.6 and above

3. If you choose to use the reflection method for the anchor code, similar to WMRouter, the version requirement is unlimited

## Star trend chart

[![Stargazers over time](https://starchart.cc/FlyJingFish/EasyRegister.svg?variant=adaptive)](https://starchart.cc/FlyJingFish/EasyRegister)

---

## Usage steps

**Can you give the project a Star before starting? Thank you very much, your support is my only motivation. Stars and Issues are welcome!**

### 1. Introduce plugins, choose one of the following two methods (required)

Depend on plugins in <code>build.gradle</code> in the <strong>project root directory</strong>

- New version

  ```gradle
  
  plugins {
    //Required item 👇 Note that the apply setting must be true
    id "io.github.flyjingfish.easyregister" version "1.1.0" apply false
  }
  ```

- Or old version

  ```gradle
  buildscript {
    dependencies {
      //Required item 👇
      classpath 'io.github.flyjingfish:easyregister:1.1.0'
    }
  }
  ```

### 2. Introduce plugins in app module (required)

```gradle
plugins {
  id 'easy.register'
}

```

### 3. Configure weaving code

- Add the following configuration to `gradle.properties` in the root directory of the project. Example.json is placed in the same directory as `gradle.properties`. For detailed instructions.

```properties
easyRegister.configJson = example.json
```

- Parameter description:
```json
[
  {
    "wovenClass": "Injected class name",
    "wovenMethod": "Injected method name includes parameter type and return type", //For example, void register(String)
    "createWovenClass": false, //Does the injected class need to be newly created?
    "insertBefore": true,//Whether to insert it before the method
    "searchClass": {
      "regex": "Regular expression", //Class to search, use regular expression to match class name(At least one of regex and extendsClass must be filled in, or both must be filled in)
      "extendsClass": "Inherited class name", //Class to search, inherited class or interface class name
      "callType": "Call searched type, caller, callee", //caller means calling the callMethod of the found class, callee means calling the callMethod of callClass and passing in the found class
      "callClass": "If the called class name is caller, do not fill it in. If it is callee, fill in the corresponding class name of callMethod",
      "callMethod": "The called method name includes parameter type and return type", //For example, void register(String)
      "callMethodValue": "Fill in the parameters of the called method, searchClass is the current data, $n is the parameter of the injection method, n represents the number", // Fill in searchClass to use the found class; fill in $n to use the number of parameters of wovenMethod
      "useType": "Type used, className, new, class, required if callee, otherwise not filled", // If it is caller, it does not need to be filled in. If it is callee, when callMethodValue fills in searchClass, className is the class name string, new is to create an object, and class is the class object of the class
    }

  }
]
```

Use case [click here to view](https://github.com/FlyJingFish/EasyRegister/blob/master/routerJson/)

- Other configurations

```properties
//Whether to enable this plugin
easyRegister.enable = true
//Which mode to enable auto only enables optimization in debug mode, debug means that optimization will always be enabled, and release means that optimization is not enabled
easyRegister.mode = auto //auto, debug, release
```

## Use this library as a class library of your own plugin library instead of a plugin

### 1. In your core Android class library Module import plugin

```gradle
plugins {
  id 'easy.register.library'
}

```

Introducing this plugin can embed anchor code into the aar package. Choosing this method means that you want to write your own plugin. The configurations of the first two steps are replaced by your own. You can still import the plugin of the first step into your own plugin library and reuse the logic inside

### 2. Introduce plugins in the class library of your plugin library

```gradle

dependencies {
  implementation 'io.github.flyjingfish:easyregister:1.1.0'
}
```

### Appreciation

You have read this far. If you like EasyRegister or feel that EasyRegister has helped you, you can click the "Star" in the upper right corner to support it. Your support is my motivation. Thank you~ 😃

If you feel that EasyRegister has saved you a lot of development time and added luster to your project, you can also scan the QR code below to buy the author a cup of coffee ☕

<div>
<img src="https://github.com/FlyJingFish/EasyRegister/blob/master/screenshot/IMG_4075.PNG" width="280" height="350">
<img src="https://github.com/FlyJingFish/EasyRegister/blob/master/screenshot/IMG_4076.JPG" width="280" height="350">
</div>

### Finally, I recommend some other libraries I wrote

- [AndroidAOP helps Android App transform into an AOP architecture framework. Only one annotation is needed to request permissions, switch threads, prohibit multiple clicks, monitor all click events at once, monitor life cycle, etc.](https://github.com/FlyJingFish/AndroidAOP)

- [OpenImage easily realizes the animated zoom effect of clicking on a small image in the application to view a large image](https://github.com/FlyJingFish/OpenImage)

- [ShapeImageView supports displaying any graphics. It can do anything you can think of](https://github.com/FlyJingFish/ShapeImageView)

- [GraphicsDrawable supports displaying any graphics, but is lighter](https://github.com/FlyJingFish/GraphicsDrawable)

- [ModuleCommunication solves the communication needs between modules and has a more convenient router function](https://github.com/FlyJingFish/ModuleCommunication)

- [FormatTextViewLib supports bold, italic, size, underline, and strikethrough for some texts. The underline supports custom distance, color, and line width; it supports adding network or local images](https://github.com/FlyJingFish/FormatTextViewLib)

- [Homepage to view more open source libraries](https://github.com/FlyJingFish)

