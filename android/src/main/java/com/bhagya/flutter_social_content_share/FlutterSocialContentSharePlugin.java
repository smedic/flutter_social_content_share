package com.bhagya.flutter_social_content_share;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import com.facebook.CallbackManager;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.widget.ShareDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** FlutterSocialContentSharePlugin */
public class FlutterSocialContentSharePlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;
  private Activity activity;
  private static CallbackManager callbackManager;
  private Bitmap socialImageBitmap;
  private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";

  String type;
  String quote;
  String url;
  String imageUrl;
  String imageName;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_social_content_share");
    channel.setMethodCallHandler(this);
  }

  /**
   * Plugin registration.
   */
  public static void registerWith(Registrar registrar) {
    final FlutterSocialContentSharePlugin instance = new FlutterSocialContentSharePlugin();
    instance.onAttachedToEngine(registrar.messenger());
    instance.activity = registrar.activity();
  }

  private void onAttachedToEngine(BinaryMessenger messenger) {
    channel = new MethodChannel(messenger, "flutter_social_content_share");
    channel.setMethodCallHandler(this);
    callbackManager = CallbackManager.Factory.create();
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    }else{
      if (call.method.equalsIgnoreCase("share")){
        type = call.argument("type");
        quote = call.argument("quote");
        url = call.argument("url");
        imageUrl = call.argument("imageUrl");
        imageName = call.argument("imageName");

        switch (type) {
          case "ShareType.facebookWithoutImage":
            shareToFacebook(url, quote, result);
            break;
          case "ShareType.instagramWithImageUrl":
            getImageBitmap(imageUrl,result);
            break;
          default:
            result.notImplemented();
            break;
        }
      }
    }
  }

  private void getPermissionToStoreData(final Result result) {
    Dexter.withContext(activity).withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .withListener(new PermissionListener() {
              @Override
              public void onPermissionGranted(PermissionGrantedResponse response) {
                if (instagramInstalled()) {
                  shareFileToInstagram(result);
                }else{
                  result.success("Instagram app is not installed on your device");
                }

              }

              @Override
              public void onPermissionDenied(PermissionDeniedResponse response) {
                result.success("Permission Denied!");
              }

              @Override
              public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                token.continuePermissionRequest();
              }
            }).check();
  }

  private void shareFileToInstagram(Result result) {
    Uri backgroundAssetUri = getImageUriFromBitmap(result,socialImageBitmap);
    if (backgroundAssetUri == null) {
      result.success("Failure");
      return;
    }

    Intent feedIntent = new Intent(Intent.ACTION_SEND);
    feedIntent.setType("image/*");
    feedIntent.putExtra(Intent.EXTRA_STREAM, backgroundAssetUri);
    feedIntent.putExtra(Intent.EXTRA_TEXT, quote);
    feedIntent.setPackage(INSTAGRAM_PACKAGE_NAME);

    //story
    Intent storiesIntent = new Intent("com.instagram.share.ADD_TO_STORY");
    storiesIntent.setDataAndType(backgroundAssetUri, "jpg");
    storiesIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    storiesIntent.setPackage(INSTAGRAM_PACKAGE_NAME);
    storiesIntent.putExtra(Intent.EXTRA_TEXT, quote);

    Intent chooserIntent = Intent.createChooser(feedIntent, "Share via Instagram");
    chooserIntent.putExtra(Intent.EXTRA_TEXT, quote);

    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{storiesIntent});

    try {
      result.success("Success");
      activity.startActivity(chooserIntent);
    } catch (ActivityNotFoundException e) {
      e.printStackTrace();
      result.success("Failure");
    }
  }

  private Uri getImageUriFromBitmap(Result result, Bitmap inImage) {
    if (inImage == null) {
      result.success("Could not load the image");
      return null;
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
    String path = MediaStore.Images.Media.insertImage(activity.getContentResolver(), inImage,"IMG_" + Calendar.getInstance().getTime(),null);
    return Uri.parse(path);
  }

  private void getImageBitmap(String path, final Result result) {

    Glide.with(activity)
            .asBitmap()
            .load(path)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(new CustomTarget<Bitmap>() {
              @Override
              public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                socialImageBitmap = resource;
                getPermissionToStoreData(result);
              }

              @Override
              public void onLoadCleared(@Nullable Drawable placeholder) {
              }
            });
  }

  /**
   * share to Facebook
   *
   * @param url    String
   * @param quote    String
   * @param result Result
   */
  private void shareToFacebook(String url, String quote, Result result) {

    ShareDialog shareDialog = new ShareDialog(activity);

    ShareLinkContent content = new ShareLinkContent.Builder()
            .setContentUrl(Uri.parse(url))
            .setQuote(quote)
            .build();
    if (ShareDialog.canShow(ShareLinkContent.class)) {
      shareDialog.show(content);
      result.success("Success");
    }

  }

  private boolean instagramInstalled() {
    try {
      if (activity != null) {
        activity.getPackageManager()
                .getApplicationInfo(INSTAGRAM_PACKAGE_NAME, 0);
        return true;
      } else {
        Log.d("App","Instagram app is not installed on your device");
      }
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
    return false;
  }



  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {

  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivity() {

  }
}
