package com.zzw.myswipbacklibrary;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;


public class SwipeBackHelper {
    private static final String TAG = "SwipeBackHelper";

    private static final int STATE_ACTION_DOWN = 1; //点击事件
    private static final int STATE_ACTION_UP = 2;  //点击结束
    private static final int STATE_BACK_START = 3; //开始滑动，不返回前一个页面
    private static final int STATE_BACK_FINISH = 4;  //结束滑动，不返回前一个页面
    private static final int STATE_FORWARD_START = 5; //开始滑动，返回前一个页面
    private static final int STATE_FORWARD_FINISH = 6;//结束滑动，返回前一个页面

    private final Interpolator mInterpolator = new DecelerateInterpolator(2f);

    private static final int EDGE_SIZE = 20;  //dp 默认拦截手势区间
    private int mEdgeSize;  //px 拦截手势区间
    private boolean mIsSliding; //是否正在滑动
    private boolean mIsSlideAnimPlaying; //滑动动画展示过程中
    private float mDistanceX;  //px 当前滑动距离 （正数或0）
    private float mLastPointX;  //记录手势在屏幕上的X轴坐标
    private boolean mEnableSlideBack = true; //默认启动滑动返回
    private int mTouchSlop;
    private boolean mIsInThresholdArea;
    private Activity mCurrentActivity;
    private ViewManager mViewManager;
    private ValueAnimator mValueAnimator;

    public SwipeBackHelper(Activity currentActivity, @NonNull SlideActivityCallback slideActivityCallback) {
        mCurrentActivity = currentActivity;
        mViewManager = new ViewManager(currentActivity, slideActivityCallback);

        mTouchSlop = ViewConfiguration.get(mCurrentActivity).getScaledTouchSlop();
        final float density = mCurrentActivity.getResources().getDisplayMetrics().density;
        mEdgeSize = (int) (EDGE_SIZE * density + 0.5f); //滑动拦截事件的区域
    }

    public void enableSwipeBack(boolean enable) {
        if (mEnableSlideBack == enable) {
            return;
        }

        mEnableSlideBack = enable;

        if (!mEnableSlideBack && mDistanceX != 0) {
            changeStatus(STATE_BACK_START);
        }
    }

    public boolean processTouchEvent(MotionEvent ev) {
        if (!mEnableSlideBack) { //不支持滑动返回，则手势事件交给View处理
            return false;
        }
        if (mIsSlideAnimPlaying) {  //正在滑动动画播放中，直接消费手势事件
            return true;
        }
        final int action = ev.getAction() & MotionEvent.ACTION_MASK;
        if (action == MotionEvent.ACTION_DOWN) {
            mLastPointX = ev.getRawX();
            mIsInThresholdArea = mLastPointX >= 0 && mLastPointX <= mEdgeSize;
        }
        if (!mIsInThresholdArea) {  //不满足滑动区域，不做处理
            return false;
        }
        final int actionIndex = ev.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                changeStatus(STATE_ACTION_DOWN);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mIsSliding) {  //有第二个手势事件加入，而且正在滑动事件中，则直接消费事件
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                final float curPointX = ev.getRawX();
                boolean originSlideStatus = mIsSliding;
                if (!originSlideStatus) {
                    if (Math.abs(curPointX - mLastPointX) < mTouchSlop) { //判断是否满足滑动
                        return false;
                    } else {
                        mIsSliding = true;
                    }
                }
                if (actionIndex == 0) {  //开始滑动
                    final float distanceX = curPointX - mLastPointX;
                    mLastPointX = curPointX;
                    setTranslationX(mDistanceX + distanceX);
                    if (originSlideStatus == mIsSliding) {
                        return true;
                    } else {
                        MotionEvent cancelEvent = MotionEvent.obtain(ev); //首次判定为滑动需要修正事件：手动修改事件为 ACTION_CANCEL，并通知底层View
                        cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                        mCurrentActivity.getWindow().superDispatchTouchEvent(cancelEvent);
                        return true;
                    }
                } else {
                    return true;
                }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_OUTSIDE:
                if (mDistanceX == 0) { //没有进行滑动
                    mIsSliding = false;
                    changeStatus(STATE_ACTION_UP);
                    return false;
                }
                if (mIsSliding && actionIndex == 0) { // 取消滑动 或 手势抬起 ，而且手势事件是第一手势，开始滑动动画
                    mIsSliding = false;
                    changeStatus(STATE_ACTION_UP);
                    return true;
                } else if (mIsSliding) {
                    return true;
                }
                break;
            default:
                mIsSliding = false;
                break;
        }
        return false;
    }

    public void finishSwipeImmediately() {
        if (mValueAnimator != null) {
            mValueAnimator.cancel();
        }
    }

    /**
     * 处理事件（滑动事件除外）
     *
     * @param status
     */
    private void changeStatus(int status) {
        switch (status) {
            case STATE_ACTION_DOWN:
                // hide input method
                InputMethodManager inputMethod = (InputMethodManager) mCurrentActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                View view = mCurrentActivity.getCurrentFocus();
                if (view != null && inputMethod != null) {
                    inputMethod.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                if (!mViewManager.prepareViews()) return;
                break;
            case STATE_ACTION_UP:
                final int width = mCurrentActivity.getResources().getDisplayMetrics().widthPixels;
                if (mDistanceX == 0) {
                    mViewManager.clearViews(false);
                } else if (mDistanceX > width / 4) {
                    changeStatus(STATE_FORWARD_START);
                } else {
                    changeStatus(STATE_BACK_START);
                }
                break;
            case STATE_BACK_START:
                mIsSlideAnimPlaying = true;
                startBackAnim();
                break;
            case STATE_BACK_FINISH:
                mDistanceX = 0;
                mIsSliding = false;
                mViewManager.clearViews(false);
                break;
            case STATE_FORWARD_START:
                mIsSlideAnimPlaying = true;
                startForwardAnim();
                break;
            case STATE_FORWARD_FINISH:
                mViewManager.clearViews(true);
                if (mOnSlideFinishListener != null) {
                    mOnSlideFinishListener.onFinish();
                }
                break;
            default:
                break;
        }
    }

    private void setTranslationX(float x) {
        final int width = mCurrentActivity.getResources().getDisplayMetrics().widthPixels;
        mDistanceX = x;
        mDistanceX = Math.max(0, mDistanceX);
        mDistanceX = Math.min(width, mDistanceX);

        mViewManager.translateViews(mDistanceX, width);
    }

    private void startBackAnim() {
        final int maxValue = 150;
        mValueAnimator = new ValueAnimator();
        mValueAnimator.setInterpolator(mInterpolator);
        mValueAnimator.setIntValues(0, maxValue);
        mValueAnimator.setDuration(maxValue);
        mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Integer value = (Integer) animation.getAnimatedValue();
                float currentDistanceX = mDistanceX * (maxValue - value) / maxValue;
                setTranslationX(currentDistanceX);
            }
        });

        mValueAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                animation.removeListener(this);
                mIsSlideAnimPlaying = false;
                changeStatus(STATE_BACK_FINISH);
            }
        });
        mValueAnimator.start();
    }

    private void startForwardAnim() {
        final int maxValue = 300;
        mValueAnimator = new ValueAnimator();
        mValueAnimator.setInterpolator(mInterpolator);
        mValueAnimator.setIntValues(0, maxValue);
        mValueAnimator.setDuration(maxValue);
        mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Integer value = (Integer) animation.getAnimatedValue();
                int width = mCurrentActivity.getResources().getDisplayMetrics().widthPixels;
                float currentDistanceX = mDistanceX + (width - mDistanceX) * value / maxValue;
                setTranslationX(currentDistanceX);
            }
        });

        mValueAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                animation.removeListener(this);
                changeStatus(STATE_FORWARD_FINISH);
            }
        });
        mValueAnimator.start();
    }

    private static class ViewManager {
        private Activity mCurrentActivity;
        private WeakReference<Activity> mPreviousActivity;
        private SlideActivityCallback mSlideActivityCallback;

        private ViewGroup mCurrentContentView;
        private View mDisplayView;
        private View mPreviousDisplayView;

        private TemporaryViewGroup mTemporaryViewGroup;

        private ViewManager(Activity currentActivity, @NonNull SlideActivityCallback slideActivityCallback) {
            mCurrentActivity = currentActivity;
            mSlideActivityCallback = slideActivityCallback;
        }

        /**
         * Remove view from previous Activity and add into current Activity
         *
         * @return Is view added successfully
         */
        private boolean prepareViews() {
            mCurrentContentView = (ViewGroup) mCurrentActivity.findViewById(Window.ID_ANDROID_CONTENT);

            //previous Activity not support to be swipeBack...
            Activity previousActivity = mSlideActivityCallback.getPreviousActivity();
            if (previousActivity == null || (previousActivity instanceof SlideBackManager && !((SlideBackManager) previousActivity).canBeSlideBack())) {
                mCurrentContentView = null;
                return false;
            }

            // init display view, maybe null
            mDisplayView = mCurrentContentView.getChildAt(0);

            // init previousView from previous Activity, which can not be null
            ViewGroup previousContentView = (ViewGroup) previousActivity.findViewById(Window.ID_ANDROID_CONTENT);
            mPreviousDisplayView = previousContentView.getChildAt(0);
            if (mPreviousDisplayView == null) {
                mCurrentContentView = null;
                mDisplayView = null;
                return false;
            }

            // Cache the previous Activity, make sure return view to the right Activity!
            mPreviousActivity = new WeakReference<>(previousActivity);

            // remove view from previous Activity.
            // as a child to TemporaryViewGroup.
            previousContentView.removeView(mPreviousDisplayView);
            mTemporaryViewGroup = new TemporaryViewGroup(mCurrentActivity);
            mTemporaryViewGroup.addPreviousView(mCurrentContentView, previousContentView, mPreviousDisplayView);
            mCurrentContentView.addView(mTemporaryViewGroup, 0);
            return true;
        }

        private void clearViews(boolean forward) {
            if (mCurrentContentView == null) {
                return;
            }

            // recover the content view from previous Activity
            mPreviousDisplayView.setX(0);
            ViewGroup previewContentView = null;
            if (mPreviousActivity != null && mPreviousActivity.get() != null && !mPreviousActivity.get().isFinishing()) {
                previewContentView = (ViewGroup) mPreviousActivity.get().findViewById(Window.ID_ANDROID_CONTENT);
            }
            mTemporaryViewGroup.clearPreviousView(previewContentView, mPreviousDisplayView);

            // in forward case, temporaryView should cache the previous view.
            if (!forward) {
                mCurrentContentView.removeView(mTemporaryViewGroup);
            }

            mTemporaryViewGroup = null;
            mPreviousDisplayView = null;
            mCurrentContentView = null;
            mDisplayView = null;
        }

        private void translateViews(float x, int screenWidth) {
            if (mCurrentContentView == null) {
                return;
            }

            mTemporaryViewGroup.setDistanceX(x);
            mPreviousDisplayView.setX((-screenWidth + x) / 3);
            if (mDisplayView != null) {
                mDisplayView.setX(x);
            }
        }
    }

    public interface SlideBackManager {
        /**
         * 是否支持滑动返回
         *
         * @return
         */
        boolean supportSlideBack();

        /**
         * 能否滑动返回至当前Activity
         *
         * @return
         */
        boolean canBeSlideBack();
    }

    private OnSlideFinishListener mOnSlideFinishListener;

    public void setOnSlideFinishListener(OnSlideFinishListener onSlideFinishListener) {
        mOnSlideFinishListener = onSlideFinishListener;
    }

    public interface OnSlideFinishListener {
        void onFinish();
    }
}