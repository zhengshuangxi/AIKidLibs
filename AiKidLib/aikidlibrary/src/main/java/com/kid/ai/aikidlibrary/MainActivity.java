package com.kid.ai.aikidlibrary;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;
import android.os.Environment;
import android.util.Log;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.cloud.util.ResourceUtil.RESOURCE_TYPE;
import com.kid.ai.aikidlibrary.speech.util.FucUtil;
import com.kid.ai.aikidlibrary.speech.util.JsonParser;
import com.kid.ai.aikidlibrary.speech.util.XmlParser;
import com.iflytek.cloud.RequestListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.VoiceWakeuper;
import com.iflytek.cloud.WakeuperListener;
import com.iflytek.cloud.WakeuperResult;
import com.iflytek.cloud.util.FileDownloadListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.cloud.util.ResourceUtil.RESOURCE_TYPE;

public class MainActivity extends UnityPlayerActivity {

    private  static  final String TAG="NativeCall";
    private StringBuilder sb = new StringBuilder();
    //语音识别对象
    private  SpeechRecognizer mAsr;
    // 缓存
    private SharedPreferences mSharedPreferences;
    // 语音唤醒对象
    private VoiceWakeuper mIvw;
    // 唤醒结果内容
    private String resultString;
    // 本地语法文件
    private String mLocalGrammar = null;
    // 本地词典
    private String mLocalLexicon = null;
    // 云端语法文件
    private String mCloudGrammar = null;
    // 本地语法构建路径
    private String grmPath = Environment.getExternalStorageDirectory()
            .getAbsolutePath() + "/msc/test";
    // 返回结果格式，支持：xml,json
    private String mResultType = "json";

    private  final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    private  final String GRAMMAR_TYPE_ABNF = "abnf";
    private  final String GRAMMAR_TYPE_BNF = "bnf";

    private String mEngineType = "cloud";

    String mContent;// 语法、词典临时变量
    int ret = 0;// 函数调用返回值

    // 设置门限值 ： 门限值越低越容易被唤醒
    private final static int MAX = 3000;
    private final static int MIN = 0;
    private int curThresh = 1450;
    private String threshStr = "门限值：";
    private String keep_alive = "1";
    private String ivwNetMode = "0";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 初始化识别对象
        mAsr = SpeechRecognizer.createRecognizer(this, mInitListener);
        // 初始化语法、命令词
        mLocalLexicon = "张海羊\n刘婧\n王锋\n";
        mLocalGrammar = FucUtil.readFile(this,"call.bnf", "utf-8");
        mCloudGrammar = FucUtil.readFile(this,"grammar_sample.abnf","utf-8");
        mSharedPreferences = getSharedPreferences(getPackageName(),	MODE_PRIVATE);
        //云端
        mEngineType = SpeechConstant.TYPE_CLOUD;
        try{
        mContent = new String(mCloudGrammar);
        // 指定引擎类型
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 设置文本编码格式
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING,"utf-8");
        ret = mAsr.buildGrammar(GRAMMAR_TYPE_ABNF, mContent, grammarListener);
        if(ret != ErrorCode.SUCCESS)
            Log.d(TAG,"语法构建失败,错误码：" + ret);
        } catch (Exception e) {

        }
        //唤醒初始化
        mIvw = VoiceWakeuper.createWakeuper(this, null);
        ivwNetMode = "1";
    }
    /**
     * 构建语法监听器。
     */
    private GrammarListener grammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if(error == null){
                if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
                    mContent = grammarId;
                }
                Log.d(TAG,"语法构建成功：" + grammarId);
            }else{
                Log.d(TAG,"语法构建失败,错误码：" + error.getErrorCode());
            }
        }
    };
    /**
     * @param method  方法名  unity那边实现的 方法名
     * @param params  方法参数  可以传给unity的参数 一般是 json字符串
     */
    public static void sendMessageToUnity(String method, String params) {
        UnityPlayer.UnitySendMessage(TAG, method, params);
    }
    /**
     * 识别监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            Log.d(TAG,"当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据："+data.length);
        }

        @Override
        public void onResult(final RecognizerResult result, boolean isLast) {
            if (null != result ) {
                Log.d(TAG, "recognizer result：" + result.getResultString());
                String text = "";
                if (mResultType.equals("json")) {
                    text = JsonParser.parseGrammarResult(result.getResultString(), mEngineType);
                } else if (mResultType.equals("xml")) {
                    text = XmlParser.parseNluResult(result.getResultString());
                }
                sb.append(text);
                if (isLast) {
                    Log.d(TAG, "recognizer result : " + sb.toString());
                    sendMessageToUnity("DistinguishResult", sb.toString());
                    sb.delete(0, sb.length());
                }
                // 显示
                //((EditText) findViewById(R.id.isr_text)).setText(text);
            } else {
                Log.d(TAG, "recognizer result : null");
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            Log.d(TAG,"结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            Log.d(TAG,"开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            Log.d(TAG,"onError Code："	+ error.getErrorCode());
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }

    };

    //开始识别
    public void StartDistinguish()
    {
        ret = mAsr.startListening(mRecognizerListener);
        if (ret != ErrorCode.SUCCESS) {
            Log.d(TAG,"识别失败,错误码: " + ret);
        }
    }
    //结束识别
    public void StopDistinguish()
    {
        mAsr.stopListening();
    }
    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                Log.d(TAG,"初始化失败,错误码："+code);
            }
        }
    };

    //开始唤醒
    public void OnWakeUpStart()
    {
        //非空判断，防止因空指针使程序崩溃
        mIvw = VoiceWakeuper.getWakeuper();
        if(mIvw != null) {
            //setRadioEnable(false);
            resultString = "";
            //textView.setText(resultString);

            // 清空参数
            mIvw.setParameter(SpeechConstant.PARAMS, null);
            // 唤醒门限值，根据资源携带的唤醒词个数按照“id:门限;id:门限”的格式传入
            mIvw.setParameter(SpeechConstant.IVW_THRESHOLD, "0:"+ curThresh);
            // 设置唤醒模式
            mIvw.setParameter(SpeechConstant.IVW_SST, "wakeup");
            // 设置持续进行唤醒
            mIvw.setParameter(SpeechConstant.KEEP_ALIVE, keep_alive);
            // 设置闭环优化网络模式
            mIvw.setParameter(SpeechConstant.IVW_NET_MODE, ivwNetMode);
            // 设置唤醒资源路径
            mIvw.setParameter(SpeechConstant.IVW_RES_PATH, getResource());
            // 设置唤醒录音保存路径，保存最近一分钟的音频
            mIvw.setParameter( SpeechConstant.IVW_AUDIO_PATH, Environment.getExternalStorageDirectory().getPath()+"/msc/ivw.wav" );
            mIvw.setParameter( SpeechConstant.AUDIO_FORMAT, "wav" );
            // 如有需要，设置 NOTIFY_RECORD_DATA 以实时通过 onEvent 返回录音音频流字节
            //mIvw.setParameter( SpeechConstant.NOTIFY_RECORD_DATA, "1" );

            // 启动唤醒
            mIvw.startListening(mWakeuperListener);
        } else {
            Log.d(TAG,"唤醒未初始化");
           // showTip("唤醒未初始化");
        }
    }
    //停止唤醒
    public void OnWakeUpStop()
    {
        mIvw.stopListening();
    }

    /**
     * 查询闭环优化唤醒资源
     * 请在闭环优化网络模式1或者模式2使用
     */
    public void queryResource() {
        int ret = mIvw.queryResource(getResource(), requestListener);
        //showTip("updateResource ret:"+ret);
    }
    // 查询资源请求回调监听
    private RequestListener requestListener = new RequestListener() {
        @Override
        public void onEvent(int eventType, Bundle params) {
            // 以下代码用于获取查询会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            //if(SpeechEvent.EVENT_SESSION_ID == eventType) {
            // 	Log.d(TAG, "sid:"+params.getString(SpeechEvent.KEY_EVENT_SESSION_ID));
            //}
        }

        @Override
        public void onCompleted(SpeechError error) {
            if(error != null) {
                Log.d(TAG, "error:"+error.getErrorCode());
                //showTip(error.getPlainDescription(true));
            }
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            try {
                String resultInfo = new String(buffer, "utf-8");
                Log.d(TAG, "resultInfo:"+resultInfo);

                JSONTokener tokener = new JSONTokener(resultInfo);
                JSONObject object = new JSONObject(tokener);

                int ret = object.getInt("ret");
                if(ret == 0) {
                    String uri = object.getString("dlurl");
                    String md5 = object.getString("md5");
                    Log.d(TAG,"uri:"+uri);
                    Log.d(TAG,"md5:"+md5);
                    //showTip("请求成功");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    // 下载资源回调监听
    private FileDownloadListener downloadListener = new FileDownloadListener() {
        @Override
        public void onStart() {
            // 下载启动回调
            Log.d(TAG, "download onStart");
            //showTip("download onStart");
        }

        @Override
        public void onProgress(int percent) {
            // 下载进度信息
            Log.d(TAG, "download onProgress,percent:"+ percent);
            //showTip("download onProgress,percent:"+ percent);
        }

        @Override
        public void onCompleted(String filePath, SpeechError error) {
            // 下载完成回调
            if(error != null) {
                Log.d(TAG, "error:"+error.getErrorCode());
                //showTip(error.getPlainDescription(true));
            } else {
                Log.d(TAG, "download onFinish,filePath:"+ filePath);
               // showTip(filePath);
            }
        }
    };
    private WakeuperListener mWakeuperListener = new WakeuperListener() {

        @Override
        public void onResult(WakeuperResult result) {
            Log.d(TAG, "onResult");
            if(!"1".equalsIgnoreCase(keep_alive)) {
                //setRadioEnable(true);
            }
            try {
                String text = result.getResultString();
                JSONObject object;
                object = new JSONObject(text);
                StringBuffer buffer = new StringBuffer();
                buffer.append("【RAW】 "+text);
                buffer.append("\n");
                buffer.append("【操作类型】"+ object.optString("sst"));
                buffer.append("\n");
                buffer.append("【唤醒词id】"+ object.optString("id"));
                buffer.append("\n");
                buffer.append("【得分】" + object.optString("score"));
                buffer.append("\n");
                buffer.append("【前端点】" + object.optString("bos"));
                buffer.append("\n");
                buffer.append("【尾端点】" + object.optString("eos"));
                resultString =buffer.toString();
            } catch (JSONException e) {
                resultString = "结果解析出错";
                e.printStackTrace();
            }
            sb.append(resultString);
            sendMessageToUnity("WakeUpResult", sb.toString());
            sb.delete(0, sb.length());
            //textView.setText(resultString);
        }

        @Override
        public void onError(SpeechError error) {
            //showTip(error.getPlainDescription(true));
            //setRadioEnable(true);
        }

        @Override
        public void onBeginOfSpeech() {
        }

        @Override
        public void onEvent(int eventType, int isLast, int arg2, Bundle obj) {
            switch( eventType ){
                // EVENT_RECORD_DATA 事件仅在 NOTIFY_RECORD_DATA 参数值为 真 时返回
                case SpeechEvent.EVENT_RECORD_DATA:
                    final byte[] audio = obj.getByteArray( SpeechEvent.KEY_EVENT_RECORD_DATA );
                    Log.i( TAG, "ivw audio length: "+audio.length );
                    break;
            }
        }

        @Override
        public void onVolumeChanged(int volume) {

        }
    };
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy WakeDemo");
        // 销毁合成对象
        mIvw = VoiceWakeuper.getWakeuper();
        if (mIvw != null) {
            mIvw.destroy();
        }
    }
    private String getResource() {
        final String resPath = ResourceUtil.generateResourcePath(this, RESOURCE_TYPE.assets, "ivw/"+/*getString(R.string.app_id)*/".jet");
        Log.d( TAG, "resPath: "+resPath );
        return resPath;
    }
}
