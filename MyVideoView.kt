package demo.kjt.surfaceviewdemo.widget

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Environment
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import demo.kjt.surfaceviewdemo.AudioMngHelper
import demo.kjt.surfaceviewdemo.R
import demo.kjt.surfaceviewdemo.ScreenBrightnessHelper
import kotlin.math.abs
import kotlin.properties.Delegates
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT as LL_WRAP_CONTENT
import android.widget.RelativeLayout.LayoutParams.MATCH_PARENT as RL_MATCH_PARENT
import android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT as RL_WRAP_CONTENT

//更新播放进度信息
const val UPDATE_TIME = 0x12

//隐藏播放控制器
const val HIDE_CONTROLLER = 0x13

//log标记
const val TAG = "MyVideoView"

class MyVideoView : RelativeLayout {

    private var mContext: Context

    //返回菜单
    private lateinit var llBack: LinearLayout
    private lateinit var btnBack: ImageButton

    //视频播放器
    private lateinit var surfaceView: SurfaceView

    //音量控制器
    private lateinit var llVolume: LinearLayout
    private lateinit var imgVolume: ImageView
    private lateinit var volume: ProgressBar

    //亮度控制器
    private lateinit var llBrightness: LinearLayout
    private lateinit var imgBrightness: ImageView
    private lateinit var brightness: ProgressBar

    //播放控制器
    private lateinit var llController: LinearLayout
    private lateinit var btnPlay: ImageButton
    private lateinit var tvCurrentTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvTotalTime: TextView

    //视频播放的核心操作类
    private lateinit var mMediaPlayer: MediaPlayer

    //音量控制辅助类
    private lateinit var mAudioHelper: AudioMngHelper

    //屏幕亮度辅助类
    private lateinit var mBrightnessHelper: ScreenBrightnessHelper

    //含有屏幕宽高像素的类
    private var mPoint = resources.displayMetrics

    //播放控制器是否显示的flag
    private var mControllerIsShowing = false

    //音量控制器是否显示的flag
    private var mVolumeIsShowing = false

    //亮度控制器是否显示flag
    private var mBrightnessIsShowing = false

    //当前播放视频长度（毫秒）
    private var mMovieDuration: Int = 0

    //当前视频已播放长度（毫秒）
    private var mCurrentPosition = 0

    //视频播放状态
    private var mMediaIsPause = true

    //视频控件是否第一次加载
    private var mIsFirstInit = true


    private val mHandler = Handler(Handler.Callback { msg ->
        when (msg?.what) {
            UPDATE_TIME -> {
                autoUpdateTime()
            }
            HIDE_CONTROLLER -> {
                hideController()
            }
        }
        true
    })

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        mContext = context
        initSurfaceView()
        initWindow()
        addAllModule()
        initViewListener()
        initMediaPlayer()
        initManagerHelpers()
    }

    /*
   * 初始化Activity屏幕方向、亮度等
   * */
    private fun initWindow() {
        //视频加载成功后修改
        requestDisallowInterceptTouchEvent(false)
        setBackgroundColor(Color.BLACK)
        onConfigurationChanged(resources.configuration)
        val activity = mContext as Activity
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        //屏幕亮度=-1表示自动亮度，跟随环境亮度自动变化。
        //手动把当前Activity的亮度调到一半，否则手势调节亮度时是从-1开始算起的。
        if (activity.window.attributes.buttonBrightness == -1f)
            activity.window.attributes.screenBrightness = 0.5f

    }

    /*
    * 初始化添加所有控件
    * */
    private fun addAllModule() {
        initAndAddBackModule()
        initAndAddVolumeModule()
        initAndAddBrightnessModule()
        initAndAddMediaControllerModule()
    }

    /*
    * 初始化并添加SurfaceView
    * */
    private fun initSurfaceView() {
        surfaceView = SurfaceView(mContext)
        val surfaceViewLayoutParams = LayoutParams(RL_MATCH_PARENT, RL_MATCH_PARENT)
        surfaceViewLayoutParams.addRule(CENTER_IN_PARENT)
        addView(surfaceView, surfaceViewLayoutParams)
        surfaceView.setZOrderOnTop(true)
        surfaceView.setZOrderMediaOverlay(true)
        surfaceView.requestFocus()
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder?) {
                Log.i(TAG, "++++++++++++++surfaceCreated++++++++++++++")
                mMediaPlayer.setDisplay(holder)
//                mMediaPlayer.prepare()
                //网络视频使用异步
                if (mIsFirstInit)
                    mMediaPlayer.prepareAsync()
                //屏幕锁屏解锁后SurfaceView会重新创建走这个方法，并且在onResume后执行
                //所以将画面切到暂停时的位置
                if (mCurrentPosition != 0) {
                    mMediaPlayer.seekTo(mCurrentPosition)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
                Log.i(TAG, "++++++++++++++surfaceChanged++++++++++++++")
                //surfaceCreated()方法执行后会走surfaceChanged()方法
                //这个方法最少会执行一次
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                //锁屏等操作执行SurfaceView销毁，走这个方法
                Log.i(TAG, "++++++++++++++surfaceDestroyed++++++++++++++")
            }
        })
    }

    /*
    * 初始化并添加返回菜单所有控件
    * */
    private fun initAndAddBackModule() {
        llBack = LinearLayout(mContext)
        llBack.setPadding(0, dp2px(5), 0, dp2px(10))
        llBack.setBackgroundResource(R.drawable.transparent_up_down)
        val llBackLayoutParams = LayoutParams(RL_MATCH_PARENT, RL_WRAP_CONTENT)
        llBackLayoutParams.addRule(ALIGN_PARENT_TOP)
        llBack.visibility = View.GONE
        addView(llBack, llBackLayoutParams)
        //返回按钮
        btnBack = ImageButton(mContext)
        btnBack.layoutParams = LinearLayout.LayoutParams(LL_WRAP_CONTENT, LL_WRAP_CONTENT)
        btnBack.setPadding(dp2px(10), dp2px(10), dp2px(20), dp2px(10))
        btnBack.background = null
        btnBack.setImageResource(R.mipmap.back)
        llBack.addView(btnBack)
    }

    /*
    * 初始化并添加音量控制器所有控件
    * */
    private fun initAndAddVolumeModule() {
        llVolume = LinearLayout(mContext)
        llVolume.setBackgroundResource(R.drawable.transparent_corner_shape)
        llVolume.gravity = Gravity.CENTER_VERTICAL
        val llVolumeLayoutParams = LayoutParams(RL_WRAP_CONTENT, RL_WRAP_CONTENT)
        llVolumeLayoutParams.addRule(CENTER_IN_PARENT)
        llVolume.visibility = View.GONE
        addView(llVolume, llVolumeLayoutParams)
        //音量图标
        imgVolume = ImageView(mContext)
        imgVolume.layoutParams = LinearLayout.LayoutParams(LL_WRAP_CONTENT, LL_WRAP_CONTENT)
        imgVolume.setImageResource(R.mipmap.volume)
        imgVolume.setPadding(0, 0, dp2px(10), 0)
        llVolume.addView(imgVolume, 0)
        //音量百分比
        volume = ProgressBar(mContext, null, android.R.attr.progressBarStyleHorizontal)
        volume.layoutParams = LinearLayout.LayoutParams(dp2px(150), LL_WRAP_CONTENT)
        llVolume.addView(volume, 1)
    }

    /*
    * 初始化并添加亮度控制器所有控件
    * */
    private fun initAndAddBrightnessModule() {
        llBrightness = LinearLayout(mContext)
        llBrightness.setBackgroundResource(R.drawable.transparent_corner_shape)
        llBrightness.gravity = Gravity.CENTER_VERTICAL
        val llBrightnessLayoutParams = LayoutParams(RL_WRAP_CONTENT, RL_WRAP_CONTENT)
        llBrightnessLayoutParams.addRule(CENTER_IN_PARENT)
        llBrightness.visibility = View.GONE
        addView(llBrightness, llBrightnessLayoutParams)
        //亮度图标
        imgBrightness = ImageView(mContext)
        imgBrightness.layoutParams = LinearLayout.LayoutParams(LL_WRAP_CONTENT, LL_WRAP_CONTENT)
        imgBrightness.setImageResource(R.mipmap.brightness)
        imgBrightness.setPadding(0, 0, dp2px(10), 0)
        llBrightness.addView(imgBrightness, 0)
        //亮度百分比
        brightness = ProgressBar(mContext, null, android.R.attr.progressBarStyleHorizontal)
        brightness.layoutParams = LinearLayout.LayoutParams(dp2px(150), LL_WRAP_CONTENT)
        llBrightness.addView(brightness, 1)
    }

    /*
    * 初始化并添加播放控制器所有控件
    * */
    private fun initAndAddMediaControllerModule() {
        llController = LinearLayout(mContext)
        llController.setBackgroundResource(R.drawable.transparent_down_up)
        val dp10 = dp2px(10)
        llController.setPadding(dp10, dp10, dp10, dp10)
        llController.gravity = Gravity.CENTER_VERTICAL
        val llControllerLayoutParams = LayoutParams(RL_MATCH_PARENT, RL_WRAP_CONTENT)
        llControllerLayoutParams.addRule(ALIGN_PARENT_BOTTOM)
        llController.visibility = View.GONE
        addView(llController, llControllerLayoutParams)
        //暂停/播放按钮
        btnPlay = ImageButton(mContext)
        btnPlay.background = null
        btnPlay.layoutParams = LinearLayout.LayoutParams(LL_WRAP_CONTENT, LL_WRAP_CONTENT)
        llController.addView(btnPlay, 0)
        //已播放时间长度
        tvCurrentTime = TextView(mContext)
        tvCurrentTime.setTextColor(Color.WHITE)
        tvCurrentTime.setPadding(dp10, 0, dp10, 0)
        tvCurrentTime.layoutParams = LinearLayout.LayoutParams(LL_WRAP_CONTENT, LL_WRAP_CONTENT)
        llController.addView(tvCurrentTime, 1)
        //播放进度条
        seekBar = SeekBar(mContext)
        seekBar.layoutParams = LinearLayout.LayoutParams(0, LL_WRAP_CONTENT, 1f)
        llController.addView(seekBar, 2)
        //视频长度
        tvTotalTime = TextView(mContext)
        tvTotalTime.setTextColor(Color.WHITE)
        tvTotalTime.setPadding(dp2px(5), 0, 0, 0)
        tvTotalTime.layoutParams = LinearLayout.LayoutParams(LL_WRAP_CONTENT, LL_WRAP_CONTENT)
        llController.addView(tvTotalTime, 3)
    }

    /*
   * 设置View的各种监听事件
   * */
    private fun initViewListener() {
        //设置播放进度条的监听事件
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                Log.i(TAG, "seekBar scroll is from user? -------$fromUser")
                if (fromUser) {
                    //seekTo的参数为指定播放位置的毫秒数
                    mMediaPlayer.seekTo((progress / 100f * mMovieDuration).toInt())
                    tvCurrentTime.text = formatLongTime2Str(mMediaPlayer.currentPosition)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                //拖动进度条的时候清除handler的更新任务
                mHandler.removeMessages(UPDATE_TIME)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                //停止拖动后发送更新进度的任务
                mHandler.sendEmptyMessage(UPDATE_TIME)
            }
        })
        //设置播放按钮的点击事件
        btnPlay.setOnClickListener { v ->
            playOrPause()
        }
    }

    /*
   * 初始化MediaPlayer
   **/
    private fun initMediaPlayer() {
        mMediaPlayer = MediaPlayer()
//        mMediaPlayer = MediaPlayer.create(this, Uri.fromFile(File(getSourcePath())), surfaceView.holder)
        mMediaPlayer.setOnPreparedListener { mp ->
            Log.i(TAG, "+++++++++++++++++MediaPlayer OnPreparedListener++++++++++++++")
            initTimeForView()
            if (mIsFirstInit) {
                playOrPause()
                mIsFirstInit = false
            }
            requestDisallowInterceptTouchEvent(true)
        }
        //视频加载错误的回调
        mMediaPlayer.setOnErrorListener { mp, what, extra ->

            val errorMsg = when (extra) {
                //文件或网络相关的操作错误。
                MediaPlayer.MEDIA_ERROR_IO -> "读取视频文件失败"
                //比特流不符合相关的编码标准或文件规范。
                MediaPlayer.MEDIA_ERROR_MALFORMED -> "视频已损坏"
                //比特流符合相关的编码标准或文件规范，但媒体框架不支持该功能。
                MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "不支持该格式"
                //某些操作需要很长时间才能完成，通常需要3-5秒以上。
                MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "视频加载失败"
                //视频已流式传输，并且其容器无效，无法进行逐行播放
                //播放，即视频的索引（例如moov atom）不在文件的开头。
                MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> "播放失败"
                else -> ""
            }
            if (errorMsg.isNotEmpty()) {
                val toast = Toast.makeText(mContext, errorMsg, Toast.LENGTH_LONG)
                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()
            } else
                Log.i(TAG, "OnErrorListener-------------->what = $what,extra = $extra")
            false
        }
        //播放器信息警告回调
        mMediaPlayer.setOnInfoListener { mp, what, extra ->
            val infoMsg = when (what) {
                MediaPlayer.MEDIA_INFO_UNKNOWN -> "未知错误"
                MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING -> "视频解码复杂，可能只有音频可以正常播放"
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> "只渲染了第一帧"
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> "暂停播放，开始缓冲"
                MediaPlayer.MEDIA_INFO_BUFFERING_END -> "缓冲结束，恢复播放"
                MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING -> "错误的交错"
                MediaPlayer.MEDIA_INFO_NOT_SEEKABLE -> "资源不存在"
                MediaPlayer.MEDIA_INFO_METADATA_UPDATE -> "一组新的数据源可以用"
                MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE -> "不支持字幕播放"
                MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT -> "读取字幕超时"
                else -> ""
            }
            Log.i(TAG, "OnInfoListener---------------->$infoMsg")
            false
        }
        //循环播放
        mMediaPlayer.isLooping = true
        //设置资源路径
        mMediaPlayer.setDataSource(getSourcePath())
    }

    /*
    * 初始化音量控制辅助类
    * */
    private fun initManagerHelpers() {
        mAudioHelper = AudioMngHelper(mContext)
        mBrightnessHelper = ScreenBrightnessHelper(mContext as Activity)
    }

    /*
   * 设置显示片长
   * */
    private fun initTimeForView() {
        mMovieDuration = mMediaPlayer.duration
        Log.i(TAG, "The movie milliseconds duration is $mMovieDuration")
        tvTotalTime.text = formatLongTime2Str(mMovieDuration)
    }

    /*
    *  控制视频暂停或播放
    * */
    private fun playOrPause() {
        if (mMediaPlayer.isPlaying) {
            mMediaPlayer.pause()
            mMediaIsPause = true
            mCurrentPosition = mMediaPlayer.currentPosition
            btnPlay.setImageResource(R.mipmap.play)
//            mHandler.removeMessages(UPDATE_TIME)
        } else {
            mMediaPlayer.seekTo(mCurrentPosition)
            mMediaPlayer.start()
            mMediaIsPause = false
            btnPlay.setImageResource(R.mipmap.pause)
            mHandler.sendEmptyMessage(UPDATE_TIME)
        }
    }

    /*
    * 获取资源路径地址
    * */
    private fun getSourcePath(): String {
        //视频路径
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/预告片.mp4"
    }

    /*
    * 更新视频播放进度条和当前播放时间
    * 加参数是为了方便手势快进快退使用
    * */
    private fun updateTime(currentTime: Int) {
        tvCurrentTime.text = formatLongTime2Str(currentTime)
//        Log.i(TAG, "MediaPlayer current position is ${mMediaPlayer.currentPosition}")
        seekBar.progress = (currentTime * 100f / mMovieDuration).toInt()
    }

    /*
    * 自动更新播放进度条和当前播放时间
    * */
    private fun autoUpdateTime() {
        updateTime(mMediaPlayer.currentPosition)
        if (!mMediaIsPause)
        //间隔半秒刷新一次进度
            mHandler.sendEmptyMessageDelayed(UPDATE_TIME, 500)
    }

    /*
    * 隐藏控制模块
    * */
    private fun hideController() {
//        llController.animate().setDuration(0).translationY(llController.height.toFloat()).start()
        llBack.visibility = View.GONE
        llController.visibility = View.GONE
        mControllerIsShowing = false
    }

    /*
    * 显示控制模块
    * */
    private fun showController() {
//        llController.animate().setDuration(300).translationY(0f).start()
        llBack.visibility = View.VISIBLE
        llController.visibility = View.VISIBLE
        mControllerIsShowing = true
    }

    /*
    * 显示音量
    * */
    private fun showVolume() {
        llVolume.visibility = View.VISIBLE
        mVolumeIsShowing = true
    }

    /*
    * 隐藏音量
    * */
    private fun hideVolume() {
        llVolume.visibility = View.GONE
        mVolumeIsShowing = false
    }

    /*
    * 显示亮度
    * */
    private fun showBrightness() {
        llBrightness.visibility = View.VISIBLE
        mBrightnessIsShowing = true
    }

    /*
    * 隐藏亮度
    * */
    private fun hideBrightness() {
        llBrightness.visibility = View.GONE
        mBrightnessIsShowing = false
    }

    /*
    * 将毫秒转换成字符串时间
    * */
    private fun formatLongTime2Str(time: Int): String {
        val temp = time / 1000
        //秒
        val second = if (temp % 60 < 10) "0${temp % 60}" else "${temp % 60}"
        //分
        val minute = if (temp / 60 % 60 < 10) "0${temp / 60 % 60}" else "${temp / 60 % 60}"
        //小时
        val hour = if (temp / 3600 < 10) "0${temp / 3600}" else "${temp / 3600}"
        return "$hour:$minute:$second"
    }

    /*
    * 快进或快退
    * */
    private fun forwardOrBackward(value: Int) {
//        Log.i(TAG, "currentPosition is ${mMediaPlayer.currentPosition},value is $value")
        mCurrentPosition = if (value > 0) {
            if (mMediaPlayer.currentPosition + 5000 >= mMovieDuration) mMovieDuration else mMediaPlayer.currentPosition + 5000
        } else {
            if (mMediaPlayer.currentPosition - 5000 <= 0) 0 else mMediaPlayer.currentPosition - 5000
        }
        mMediaPlayer.seekTo(mCurrentPosition)
        updateTime(mCurrentPosition)
    }

    //音量调节区间在0-100，正好对应progressBar进度值
    private var mCurrentVolume = 0

    /*
    * 调高或调低播放音量
    * */
    private fun turnUpOrDownVolume(value: Int) {
        //value小于0时为手指往上滑，增加音量
        mCurrentVolume = if (value < 0)
            mAudioHelper.addVoice100()
        else
            mAudioHelper.subVoice100()
        volume.progress = mCurrentVolume
    }

    //屏幕亮度调节区间在0f-1f，所以要将调节后返回的值*100以供progressBar使用
    private var mCurrentBrightness = 0

    /*
    * 调高或调低屏幕亮度(只对当前Activity起作用)
    * */
    private fun turnUpOrDownBrightness(value: Int) {
        mCurrentBrightness = (100 * if (value < 0)
            mBrightnessHelper.turnUp()
        else
            mBrightnessHelper.turnDown()).toInt()
        brightness.progress = mCurrentBrightness
    }

    /*
    * 将屏幕区域划分为左右两半，判断手指触摸区域
    * 返回true为左侧，false为右侧
    * */
    private fun determineTheTouchArea(XPoint: Float): Boolean {
        return (XPoint <= mPoint.widthPixels / 2)
    }

    //双击暂停的上一次点击时间
    private var preDown = 0L

    //手势操作中上一次的落指点
    private var preX = 0f
    private var preY = 0f

    //锁定竖直方向手势(音量或屏幕亮度)操作的flag
    private var lockVerticalOperate = false

    //锁定水平方向手势(播放进度)操作的flag
    private var lockHorizontalOperate = false

    //手指触摸所在区域的flag，用来区分音量手势和亮度手势
    private var touchLeftArea by Delegates.notNull<Boolean>()

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val currentDown: Long
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
//                Log.i(TAG, "down")
                touchLeftArea = determineTheTouchArea(event.x)
                currentDown = System.currentTimeMillis()
                preX = event.x
                preY = event.y
                //双击暂停或播放
                preDown = if (currentDown - preDown < 500) {
                    playOrPause()
                    //重置，防止双击后第三次点击在判断时间内
                    0L
                } else
                    currentDown
            }
            MotionEvent.ACTION_MOVE -> {
                val currentX = event.x
                val currentY = event.y
//                Log.i(TAG, "preX = $preX,currentX = $currentX")
                //横竖屏情况下X、Y轴对调，既横屏的X轴为竖屏的Y轴，横屏的Y轴为竖屏的X轴
                //判断手指在屏幕上滑动方向来区分手势操作并锁定操作
                //第一次触发的手势操作是哪个就锁定那个，后续只执行这个手势操作，直到当前手势操作结束，也就是手指抬起
                //快进或快退
                if (abs(currentX - preX) > abs(currentY - preY)) {
                    if (!lockVerticalOperate) {
                        //当水平滑动距离大于30个像素时触发快进快退操作
                        if (abs(currentX - preX) >= 30f) {
                            lockHorizontalOperate = true
                            if (!mControllerIsShowing)
                                showController()
                            forwardOrBackward((currentX - preX).toInt())
                            preX = currentX
                        }
                    }
                }
                //增加或减小音量
                else {
                    if (!lockHorizontalOperate) {
                        //当竖直滑动距离大于30个像素时触发音量或亮度操作
                        if (abs(currentY - preY) >= 30f) {
                            lockVerticalOperate = true
                            if (touchLeftArea) {
                                if (!mBrightnessIsShowing)
                                    showBrightness()
                                turnUpOrDownBrightness((currentY - preY).toInt())
                            } else {
                                if (!mVolumeIsShowing)
                                    showVolume()
                                turnUpOrDownVolume((currentY - preY).toInt())
                            }
                            preY = currentY
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {

                //点击屏幕抬起时判断隐藏或显示播放控制器
                if (mControllerIsShowing) {
                    hideController()
                    //清除handler所有隐藏控制器的消息
                    mHandler.removeMessages(HIDE_CONTROLLER)
                } else {
                    //不是竖直方向手势抬起时才显示播放控制器
                    if (!lockVerticalOperate) {
                        showController()
                        //手指抬起时发送隐藏控制器的延时消息
                        mHandler.sendEmptyMessageDelayed(HIDE_CONTROLLER, 3000)
                    }
                }
                //隐藏音量显示
                if (mVolumeIsShowing)
                    hideVolume()
                if (mBrightnessIsShowing)
                    hideBrightness()
                //重置手势操作锁
                lockVerticalOperate = false
                lockHorizontalOperate = false
            }
        }
        return true
    }

    private val landLayoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private var portLayoutParams: LayoutParams? = null
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            //横屏
            Configuration.ORIENTATION_LANDSCAPE -> {
                Log.i(TAG, "屏幕横屏")
                surfaceView.layoutParams = landLayoutParams
            }
            //竖屏
            Configuration.ORIENTATION_PORTRAIT -> {
                Log.i(TAG, "屏幕竖屏")
                if (portLayoutParams == null) {
                    mPoint = resources.displayMetrics
                    val params = LayoutParams(mPoint.widthPixels, mPoint.widthPixels * mPoint.widthPixels / mPoint.heightPixels)
                    params.addRule(CENTER_IN_PARENT)
                    portLayoutParams = params
                }
                surfaceView.layoutParams = portLayoutParams
            }
        }
    }

    /**
     * dp转px
     *
     * @param dp
     * @return
     */
    private fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    public fun setBackOnClickListener(listener: OnClickListener) {
        btnBack.setOnClickListener(listener)
    }

    public fun onStop() {
        //正在播放的时候锁屏或者退到后台，暂停播放并记录当前播放时间
        if (mMediaPlayer.isPlaying)
            playOrPause()
    }

    public fun onDestroy() {
        mHandler.removeCallbacksAndMessages(null)
        if (mMediaPlayer.isPlaying) {
            mMediaPlayer.stop()
        }
        mMediaPlayer.release()
    }

}