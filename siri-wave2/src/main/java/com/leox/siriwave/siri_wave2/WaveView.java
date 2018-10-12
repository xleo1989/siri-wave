package com.leox.siriwave.siri_wave2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;

public class WaveView extends View {
//    public static final String TAG = "WaveView";

    public static final int COLOR_BLUE = 0xFF4286F4;
    public static final int COLOR_RED = 0xFFEA4335;
    public static final int COLOR_YELLOW = 0xFFFBBC05;
    public static final int COLOR_GREEN = 0xFF34A853;

    private boolean autoStart = false;

    private boolean isRunning = false;

    private double standByAmplitude;


    private double MAX = 0;

    private double ratio = 3;

    private double controllerAmplitude;
    private double controllerSpeed;

    private double interpolateSpeed;
    private double interpolateAmplitude;

    protected Handler mHandler = null;
    private Paint mPaint;

    private double originAmplitude;

    private float amplitudeFactor = 0.66f;

    private WaveCurve[] waveCurves;

    public WaveView(Context context) {
        this(context, null);
    }

    public WaveView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.WaveView, defStyleAttr, 0);
            autoStart = typedArray.getBoolean(R.styleable.WaveView_autoStart, false);
            controllerAmplitude = typedArray.getFloat(R.styleable.WaveView_amplitude, 0.1f);
            controllerSpeed = typedArray.getFloat(R.styleable.WaveView_speed, 0.2f);
            if (controllerAmplitude > 1 || controllerAmplitude < 0 || controllerSpeed < 0 || controllerSpeed > 1) {
                throw new IllegalArgumentException("amplitude and speed must be between 0 and 1");
            }
            standByAmplitude = typedArray.getFloat(R.styleable.WaveView_standByAmplitude, 0.2f);
            if (standByAmplitude > 1 || standByAmplitude < 0){
                throw new IllegalArgumentException("standByAmplitude and speed must be between 0 and 1");
            }
            originAmplitude = controllerAmplitude;
            ratio = typedArray.getFloat(R.styleable.WaveView_ratio, 3);
            if (ratio <= 0) {
                throw new IllegalArgumentException("ratio can't be smaller than 0 or equal 0 ");
            }
            amplitudeFactor = typedArray.getFloat(R.styleable.WaveView_amplitudeFactor, 0.66f);
            if (amplitudeFactor <= 0) {
                throw new IllegalArgumentException("amplitudeFactor can't be smaller than 0 or equal 0 ");
            }
            typedArray.recycle();
        }
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStrokeWidth(1);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        mPaint.setPathEffect(new CornerPathEffect(60));
        interpolateSpeed = controllerSpeed;
        interpolateAmplitude = controllerAmplitude;
        waveCurves = new WaveCurve[]{
                new WaveCurve(COLOR_YELLOW),
                new WaveCurve(COLOR_RED),
                new WaveCurve(COLOR_GREEN),
                new WaveCurve(COLOR_BLUE)
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public WaveView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        Log.i(TAG, "onMeasure");
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            widthSize = getResources().getDisplayMetrics().densityDpi * 120;
        }
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            heightSize = (int) (widthSize / ratio);
        }
        MAX = heightSize * amplitudeFactor;
//        Log.i(TAG, "onMeasure: height--" + heightSize + "--width--" + widthSize);
        setMeasuredDimension(widthSize,
                heightSize);

    }

    @Override
    protected void onDraw(Canvas canvas) {
//        Log.i(TAG, "onDraw");
        Drawable background = getBackground();
        if (background != null) {
            background.draw(canvas);
        }
        if (isRunning) {
            drawLines(canvas);
        }
        if (mHandler != null && isRunning) {
            mHandler.sendEmptyMessageDelayed(CREATE_PATH, 0);
        }
    }


    private void clear() {
        if (mHandler != null) {
            mHandler.removeMessages(CREATE_PATH);
            mHandler = null;
        }
        //clear curves
        for (WaveCurve waveCurve : waveCurves) {
            waveCurve.clearPath();
        }
    }

    private void drawLines(Canvas canvas) {
//        Log.i(TAG, "drawLines: ");
        for (WaveCurve waveCurve : waveCurves) {
            Pair<Path, Shader>[] curves = waveCurve.getCurves();
            if (curves != null && curves.length >= 2) {
                Pair<Path, Shader> curve = curves[1];
                Pair<Path, Shader> curve1 = curves[0];
                mPaint.setShader(curve.second);
                mPaint.setDither(true);
                canvas.drawPath(curve.first, mPaint);
                mPaint.setShader(curve1.second);
                canvas.drawPath(curve1.first, mPaint);
            }
        }
    }


    private synchronized void start() {
        if (isRunning)
            return;
//        Log.i(TAG, "control-- start: ");
        clear();
        if (getHeight() == 0) {
            measure(0, 0);
        }
        HandlerThread curveCreator = new HandlerThread("curveCreator");
        curveCreator.start();
        mHandler = new Handler(curveCreator.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg == null) {
                    return;
                }
                if (msg.what == CREATE_PATH) {
//                            phase = (phase + Math.PI * controllerSpeed) % (2 * Math.PI);
                    for (WaveCurve waveCurve : waveCurves) {
                        waveCurve.drawLine();
                    }
                    interpolate();
                    postInvalidate();
                } else if (msg.what == CLEAR_CURVE) {
                    for (WaveCurve waveCurve : waveCurves) {
                        waveCurve.clearPath();
                    }
                }
            }
        };
        mHandler.obtainMessage(CREATE_PATH).sendToTarget();
        isRunning = true;
    }

    //平滑变化 （amplitude 或 speed 变化时）
    private void interpolate() {
//        Log.i(TAG, "interpolate: ");
        //speed
        double speedInterpolationSpeed = 0.001;
        if (Math.abs(interpolateSpeed - controllerSpeed) <= speedInterpolationSpeed) {
            controllerSpeed = interpolateSpeed;
        } else {
            if (interpolateSpeed > controllerSpeed) {
                controllerSpeed += speedInterpolationSpeed;
            } else {
                controllerSpeed -= speedInterpolationSpeed;
            }
        }
        //amplitude
        double amplitudeInterpolationAmplitude = 0.05;
        if (Math.abs(interpolateAmplitude - controllerAmplitude) <= amplitudeInterpolationAmplitude) {
            controllerAmplitude = interpolateAmplitude;
        } else {
            if (interpolateAmplitude > controllerAmplitude) {
                controllerAmplitude += amplitudeInterpolationAmplitude;
            } else {
                controllerAmplitude -= amplitudeInterpolationAmplitude;
            }
        }
    }

    public synchronized void stop() {
        currentSpeechState = SPEECH_IDLE;
        if (isRunning) {
//            Log.i(TAG, "control-- stop: ");
            clear();
            isRunning = false;
            postInvalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (autoStart) {
            start();
        }
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        return super.onSaveInstanceState();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }


    // =====set and get=====

    public void setControllerAmplitude(double controllerAmplitude) {
        this.interpolateAmplitude = controllerAmplitude;
    }

    public void setControllerSpeed(double controllerSpeed) {
        this.interpolateSpeed = controllerSpeed;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }


    public static final int CREATE_PATH = 0x10fc;
    public static final int CLEAR_CURVE = 0x11ee;

    /**
     * 单个curve图像
     */
    class WaveCurve {
        private double seed = 0.001;
        private double openClass;
        private double amplitude = 0.9f;
        private int mColor;
        private Pair<Path, Shader>[] curves;
        private double tick = 0;

        Pair<Path, Shader>[] getCurves() {
            return curves;
        }


        WaveCurve(int color) {
            mColor = color;
            tick = 0;
            respawn();
        }

        private void drawLine() {
            Pair<Path, Shader> curve1 = drawLine(-1);
            Pair<Path, Shader> curve2 = drawLine(1);
            curves = new Pair[]{curve1, curve2};
        }

        /**
         * 创建单个curve
         * @param m 1 || -1
         */
        private Pair<Path, Shader> drawLine(double m) {
            tick += controllerSpeed * (1 - 0.5 * Math.sin(this.seed * Math.PI));
//            Log.i(TAG, "drawLine== obj:" + this.toString() + "==m:" + m);
            int width = getWidth();
            int height = getHeight();
            Path path = new Path();
            float x_base = (float) (width / 2 + (-width / 4 + this.seed * width / 2));
            float y_base = height / 2;

            float x, y, x_init = -1;
            double i = -3;
            while (i <= 3) {
                x = (float) (x_base + i * width / 4);
                y = (float) (y_base + m * equation(i));
//                Log.i(TAG, "lineTo:" + x + ";" + y);
                if (x_init == -1) {
                    x_init = x;
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
                i += 0.3;
            }
            path.lineTo(x_init, y_base);
            path.close();
            return new Pair<>(path, createShader(x_base, y_base));
        }

        private double equation(double i) {
            double y = -1 * Math.abs(Math.sin(tick)) * amplitude * controllerAmplitude * MAX * Math.pow(1 / (1 + Math.pow(openClass * i, 2)), 2);
            if (Math.abs(y) < 0.001) {
                respawn();
            }
//            Log.i(TAG, "equation: " + y);
            return y;
        }

        /**
         * 重置随机参数
         */
        private void respawn() {
//            Log.i(TAG, "respawn: obj==" + this.toString());
            this.amplitude = 0.3 + Math.random() * 0.7;
            this.seed = Math.random();
            this.openClass = 2 + Math.random() * 3;
        }

        /**
         * 创建当前curve对应得RadialGradient（放射渐变效果）
         * @param x_base
         * @param y_base
         * @return
         */
        private Shader createShader(float x_base, float y_base) {
//            Log.i(TAG, "createShader: ");
            float radius = 1.45f * (float) Math.abs(equation(0));
            return new RadialGradient(x_base, y_base, radius == 0 ? (float) MAX / 2 : radius,
                    mColor & 0x41FFFFFF, mColor & 0x82FFFFFF
                    , Shader.TileMode.CLAMP);
        }

        void clearPath() {
            curves = null;
            tick = 0;
        }

        void release() {
            curves = null;
        }
    }

    public static final int SPEECH_IDLE = 0;
    public static final int SPEECH_STARTED = 1;
    public static final int SPEECH_ENDED = 2;
    public static final int SPEECH_PAUSED = 3;

    private int currentSpeechState = SPEECH_IDLE;

    public void speechStarted() {
        if (currentSpeechState == SPEECH_STARTED) {
            return;
        }
        currentSpeechState = SPEECH_STARTED;
//        Log.i(TAG, "control-- speechStarted: ");
        setControllerAmplitude(originAmplitude);
        if (!isRunning) {
            start();
        }
    }

    public void speechEnded() {
        if (currentSpeechState == SPEECH_STARTED || currentSpeechState ==SPEECH_ENDED) {
            return;
        }
        currentSpeechState = SPEECH_ENDED;
//        Log.i(TAG, "control-- speechEnded: ");
        setControllerAmplitude(standByAmplitude);
        if (!isRunning) {
            start();
        }
    }

    public void speechPaused() {
        if (currentSpeechState == SPEECH_IDLE || currentSpeechState == SPEECH_PAUSED) {
            return;
        }
        currentSpeechState = SPEECH_PAUSED;
//        Log.i(TAG, "control-- speechPaused: ");
        setControllerAmplitude(0.1f);
        if (!isRunning) {
            start();
        }
    }

    public void destroy() {
        stop();
        for (WaveCurve waveCurve : waveCurves) {
            waveCurve.release();
        }
        waveCurves = null;
    }
}
