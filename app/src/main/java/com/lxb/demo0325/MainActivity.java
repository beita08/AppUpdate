package com.lxb.demo0325;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private TextView tvUpdateInfo;
    private ProgressBar progressBar;
    private int mProcess = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvUpdateInfo = findViewById(R.id.tv_update_info);
        progressBar = findViewById(R.id.progressBar);
        //检测新版本
        checkVersion();
    }

    /**
     * 检测是否有新的版本，如有则进行下载更新：
     * 1. 请求服务器, 获取数据；2. 解析json数据；3. 判断是否有更新；4. 弹出升级弹窗或直接进入主页面
     */
    private void checkVersion() {
        showLaunchInfo("正在检测是否有新版本...", false);
        String checkUrl = "http://172.16.150.85:8080/AndroidAPK/AndroidUpdate.json";

        OkHttpClient okHttpClient = new OkHttpClient();//创建 OkHttpClient 对象
        Request request = new Request.Builder().url(checkUrl).build();//创建 Request
        okHttpClient.newCall(request).enqueue(new Callback() {//发送请求
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.w(TAG, "onFailure: e = " + e.getMessage());
                mProcess = 30;
                showLaunchInfo("新版本检测失败，请检查网络！", true);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try {
                    Log.w(TAG, "onResponse: response = " + response);
                    mProcess = 30;
                    final ResponseBody responseBody = response.body();
                    if (response.isSuccessful() && responseBody != null) {
                        final String responseString = responseBody.string();
                        Log.w(TAG, "onResponse: responseString = " + responseString);
                        //解析json
                        final JSONObject jo = new JSONObject(responseString);
                        final String versionName = jo.getString("VersionName");
                        final int versionCode = jo.getInt("VersionCode");
                        final String versionDes = jo.getString("VersionDes");
                        final String versionUrl = jo.getString("VersionUrl");
                        //本地版本号和服务器进行比对, 如果小于服务器, 说明有更新
                        if (BuildConfig.VERSION_CODE < versionCode) {
                            //本地版本小于服务器版本，存在新版本
                            showLaunchInfo("检测到新版本！", false);
                            progressBar.setProgress(mProcess);
                            //有更新, 弹出升级对话框
                            showUpdateDialog(versionDes, versionUrl);
                        } else {
                            showLaunchInfo("该版本已是最新版本，正在初始化项目...", true);
                        }
                    } else {
                        showLaunchInfo("新版本检测失败，请检查服务！", true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showLaunchInfo("新版本检测出现异常，请检查服务！", true);
                }
            }
        });
    }

    //弹出升级对话框
    private void showUpdateDialog(final String versionDes, final String versionUrl) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("版本更新：");
            builder.setMessage(versionDes);//版本描述
            builder.setPositiveButton("立即更新", (dialog, which) -> downloadNewApk(versionUrl));
            builder.setNegativeButton("以后再说", (dialog, which) -> showLaunchInfo("正在初始化项目...", true));
            builder.setOnCancelListener(dialog -> showLaunchInfo("正在初始化项目...", true));//监听弹窗被取消的事件
            builder.show();
        });
    }

    private void downloadNewApk(String apkName) {
        showLaunchInfo("检测到新版本，正在下载...", false);
        final File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory()) {
            //删除(/storage/emulated/0/Android/data/包名/files/Download)文件夹下的所有文件，避免一直下载文件堆积
            File[] files = downloadDir.listFiles();
            if (files != null) {
                for (final File file : files) {
                    if (file != null && file.exists() && file.isFile()) {
                        boolean delete = file.delete();
                    }
                }
            }
        }
        //显示进度条
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);//水平方向进度条, 可以显示进度
        dialog.setTitle("正在下载新版本...");
        dialog.setCancelable(false);
        dialog.show();
        //APK文件路径
        final String url = "http://172.16.100.110:7090/AndroidAPK/" + apkName;
        Request request = new Request.Builder().url(url).build();
        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                String strFailure = "新版本APK下载失败";
                showLaunchInfo(strFailure, false);
                showFailureDialog(strFailure, apkName);
                dialog.dismiss();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try {
                    final ResponseBody responseBody = response.body();
                    if (response.isSuccessful() && responseBody != null) {
                        final long total = responseBody.contentLength();
                        final InputStream is = responseBody.byteStream();
                        final File file = new File(downloadDir, apkName);
                        final FileOutputStream fos = new FileOutputStream(file);

                        int len;
                        final byte[] buf = new byte[2048];
                        long sum = 0L;
                        while ((len = is.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                            sum += len;
                            float downloadProgress = (sum * 100F / total);
                            dialog.setProgress((int) downloadProgress);//下载中，更新进度
                            progressBar.setProgress(mProcess + (int) (downloadProgress * 0.7));
                        }
                        fos.flush();
                        responseBody.close();
                        is.close();
                        fos.close();
                        installAPKByFile(file);//下载完成，开始安装
                    } else {
                        String strFailure = "新版本APK获取失败";
                        showLaunchInfo(strFailure, false);
                        showFailureDialog(strFailure, apkName);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String strException = "新版本APK下载安装出现异常";
                    showLaunchInfo(strException, false);
                    showFailureDialog(strException, apkName);
                } finally {
                    /*正常应该在finally中进行关流操作，以避免异常情况时没有关闭IO流，导致内存泄露
                     *因为本场景下异常情况可能性较小，为了代码可读性直接在正常下载结束后关流
                     */
                    dialog.dismiss();//dialog消失
                }
            }
        });
    }

    /**
     * 7.0以上系统APK安装
     */
    private void installAPKByFile(File file) {
        showLaunchInfo("新版本下载成功，正在安装中...", false);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        //intent.putExtra("pwd", "soft_2694349");//根据密码判断升级文件是否允许更新
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        Uri uri = FileProvider.getUriForFile(this, "com.lxb.demo0325.fileProvider", file);
        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        startActivityForResult(intent, REQUEST_INSTALL);
    }

    private static final int REQUEST_INSTALL = 3;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INSTALL && resultCode == RESULT_CANCELED) {
            //用户取消了安装，直接跳转登录页面
            enterHome();
        }
    }

    private void showFailureDialog(String message, String apkName) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);/*, R.style.DialogStyle*/
            builder.setTitle("提示：");
            builder.setMessage(String.format("%s，是否重新下载？", message));
            builder.setNegativeButton("直接登录", (dialog, which) -> enterHome());
            builder.setPositiveButton("重新下载", (dialog, which) -> downloadNewApk(apkName));
            builder.setOnCancelListener(dialog -> enterHome());//用户点击返回键或点击弹窗外侧
            builder.show();
        });
    }

    /**
     * 展示更新提示信息
     * @param updateInfo
     *         更新提示信息
     * @param startProcess
     *         是否开启自动进度
     */
    private void showLaunchInfo(final String updateInfo, boolean startProcess) {
        runOnUiThread(() -> tvUpdateInfo.setText(updateInfo));
        if (startProcess) {
            mHandler.removeMessages(0);
            mHandler.sendEmptyMessage(0);
        }
    }

    /**
     * 进入主页面
     */
    private void enterHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (mProcess > 100) {
                enterHome();
            } else {
                mProcess += 1;
                progressBar.setProgress(mProcess);
                mHandler.removeMessages(0);
                mHandler.sendEmptyMessageDelayed(0, 10);
            }
        }
    };
}