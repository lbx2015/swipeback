# swipeback
android gesture swipe back for activity
activity 页面侧滑返回 就用这个库，我自己也用。

利用滑动手势退出当前Activity


# Getting started

**Firstly,add the following lines to your app/build.gradle.**
```gradle
dependencies {  
      implementation 'com.github.lbx2015:swipeback:1.0.1'
}
```
**Secondly, add the following lines to your application.**
``` java
public class CustomApplication extends Application{

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(ActivityLifecycleHelper.build());
    }

}
```
**Finally, set the activity which need to swipe extends the SwipeBackActivity.if you don't want extends the SwipeBackActivity then you can copy the code in SwipeBackActivity into your base activity**
``` java
public class BaseActivity extends SwipeBackActivity {

}
```
欢迎加我qq交流：1933772028
