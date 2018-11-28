package com.oblongmana.webviewfileuploadandroid;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.os.Environment;
import android.webkit.URLUtil;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import java.nio.charset.StandardCharsets;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.views.webview.ReactWebViewManager;
import com.oblongmana.webviewfileuploadandroid.AndroidWebViewModule;

import com.facebook.react.common.build.ReactBuildConfig;

import android.os.Build;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.views.webview.events.TopMessageEvent;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.annotation.Nullable;

public class AndroidWebViewManager extends ReactWebViewManager {

  private Activity mActivity = null;
  private AndroidWebViewPackage aPackage;

  public String getName() {
    return "AndroidWebView";
  }

  protected static class WebtempWebView extends WebView implements LifecycleEventListener {
    protected @Nullable String injectedJS;
    protected boolean messagingEnabled = false;
    protected @Nullable ReactWebViewClient mReactWebViewClient;

    protected class ReactWebViewBridge {
      WebtempWebView mContext;

      ReactWebViewBridge(WebtempWebView c) {
        mContext = c;
      }

      @JavascriptInterface
      public void wtappMessage(String message) {
        mContext.onMessage(message);
      }
    }

    /**
     * WebView must be created with an context of the current activity
     *
     * Activity Context is required for creation of dialogs internally by WebView
     * Reactive Native needed for access to ReactNative internal system
     * functionality
     *
     */
    public WebtempWebView(ThemedReactContext reactContext) {
      super(reactContext);
    }

    @Override
    public void onHostResume() {
      // do nothing
    }

    @Override
    public void onHostPause() {
      // do nothing
    }

    @Override
    public void onHostDestroy() {
      cleanupCallbacksAndDestroy();
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
      super.setWebViewClient(client);
      mReactWebViewClient = (ReactWebViewClient) client;
    }

    public @Nullable ReactWebViewClient getReactWebViewClient() {
      return mReactWebViewClient;
    }

    public void setInjectedJavaScript(@Nullable String js) {
      injectedJS = js;
    }

    protected ReactWebViewBridge createReactWebViewBridge(WebtempWebView webView) {
      return new ReactWebViewBridge(webView);
    }

    public void setMessagingEnabled(boolean enabled) {
      if (messagingEnabled == enabled) {
        return;
      }

      messagingEnabled = enabled;
      if (enabled) {
        addJavascriptInterface(createReactWebViewBridge(this), "WTAPP_WEBVIEW_BRIDGE");
        linkBridge();
      } else {
        removeJavascriptInterface("WTAPP_WEBVIEW_BRIDGE");
      }
    }

    protected void evaluateJavascriptWithFallback(String script) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        evaluateJavascript(script, null);
        return;
      }

      try {
        loadUrl("javascript:" + URLEncoder.encode(script, "UTF-8"));
      } catch (UnsupportedEncodingException e) {
        // UTF-8 should always be supported
        throw new RuntimeException(e);
      }
    }

    public void callInjectedJavaScript() {
      if (getSettings().getJavaScriptEnabled() && injectedJS != null && !TextUtils.isEmpty(injectedJS)) {
        evaluateJavascriptWithFallback("(function() {\n" + injectedJS + ";\n})();");
      }
    }

    public void linkBridge() {
      if (messagingEnabled) {
        if (ReactBuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          // See isNative in lodash
          String testPostMessageNative = "String(window.postMessage) === String(Object.hasOwnProperty).replace('hasOwnProperty', 'postMessage')";
          evaluateJavascript(testPostMessageNative, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
              if (value.equals("true")) {
                FLog.w(ReactConstants.TAG,
                    "Setting onMessage on a WebView overrides existing values of window.postMessage, but a previous value was defined");
              }
            }
          });
        }

        evaluateJavascriptWithFallback(
            "(" + "window.originalPostMessage = window.postMessage," + "window.wtappMessage = function(data) {"
                + "WTAPP_WEBVIEW_BRIDGE" + ".postMessage(String(data));" + "}" + ")");
      }
    }

    public void onMessage(String message) {
      dispatchEvent(this, new TopMessageEvent(this.getId(), message));
    }

    protected void cleanupCallbacksAndDestroy() {
      setWebViewClient(null);
      destroy();
    }
  }

  @Override
  protected WebView createViewInstance(ThemedReactContext reactContext) {
    // if (ReactBuildConfig.DEBUG) {
    WebView.setWebContentsDebuggingEnabled(true);
    // }

    WebView view = super.createViewInstance(reactContext);
    // Now do our own setWebChromeClient, patching in file chooser support
    final AndroidWebViewModule module = this.aPackage.getModule();
    view.setWebChromeClient(new WebChromeClient() {

      public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {
        module.setUploadMessage(uploadMsg);
        module.openFileChooserView();

      }

      public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        return true;
      }

      public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        return true;
      }

      // For Android < 3.0
      public void openFileChooser(ValueCallback<Uri> uploadMsg) {
        module.setUploadMessage(uploadMsg);
        module.openFileChooserView();
      }

      // For Android > 4.1.1
      public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
        module.setUploadMessage(uploadMsg);
        module.openFileChooserView();
      }

      // For Android > 5.0
      public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
          WebChromeClient.FileChooserParams fileChooserParams) {
        Log.d("customwebview", "onShowFileChooser");

        module.setmUploadCallbackAboveL(filePathCallback);
        if (module.grantFileChooserPermissions()) {
          module.openFileChooserView();
        } else {
          Toast.makeText(module.getActivity().getApplicationContext(),
              "Cannot upload files as permission was denied. Please provide permission to access storage, in order to upload files.",
              Toast.LENGTH_LONG).show();
        }
        return true;
      }
    });

    view.setDownloadListener(new DownloadListener() {
      public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype,
          long contentLength) {

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

        // Try to extract filename from contentDisposition, otherwise guess using
        // URLUtil
        String fileName = "";
        try {
          fileName = contentDisposition.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
          fileName = URLDecoder.decode(fileName, "UTF-8");
        } catch (Exception e) {
          System.out.println("Error extracting filename from contentDisposition: " + e);
          System.out.println("Falling back to URLUtil.guessFileName");
          fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
        }
        String downloadMessage = "Downloading " + fileName;

        // Attempt to add cookie, if it exists
        URL urlObj = null;
        try {
          urlObj = new URL(url);
          String baseUrl = urlObj.getProtocol() + "://" + urlObj.getHost();
          String cookie = CookieManager.getInstance().getCookie(baseUrl);
          request.addRequestHeader("Cookie", cookie);
          System.out.println("Got cookie for DownloadManager: " + cookie);
        } catch (MalformedURLException e) {
          System.out.println("Error getting cookie for DownloadManager: " + e.toString());
          e.printStackTrace();
        }

        // Finish setting up request
        request.addRequestHeader("User-Agent", userAgent);
        request.setTitle(fileName);
        request.setDescription(downloadMessage);
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        module.setDownloadRequest(request);

        if (module.grantFileDownloaderPermissions()) {
          module.downloadFile();
        } else {
          Toast.makeText(module.getActivity().getApplicationContext(),
              "Cannot download files as permission was denied. Please provide permission to write to storage, in order to download files.",
              Toast.LENGTH_LONG).show();
        }
      }
    });

    return view;
  }

  public void setPackage(AndroidWebViewPackage aPackage) {
    this.aPackage = aPackage;
  }

  public AndroidWebViewPackage getPackage() {
    return this.aPackage;
  }
}
