package com.kedacom.platform2mc;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import dalvik.system.DexClassLoader;

public class MainApplication extends Application{
	private static final String appkey = "APPLICATION_CLASS_NAME";
	private static final String TAG = "[IPhoenix][MainApplication]";
	private static final String PATH = "/data/app-lib/";
	private String apkFileName;  //生成apk文件路径
	private String odexPath;     //dex路径
	private String libPath;		 //so路径
	private String apkname = "temp.apk";
	private File dexFile;
	private int apkSize;
	private static final int READ_DATA = 2048;
	private static final int INIT_SIZE = 23000000;
	private SharedPreferences sp;

	//这是context 赋值
	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		try {
			//创建两个文件夹payload_odex, 私有的，可写的文件目录
			File odex = this.getDir("payload_odex", MODE_PRIVATE);
			odexPath = odex.getAbsolutePath();
			String appPath = getPackageResourcePath();  //app路径
			libPath = PATH + appPath.substring(10,appPath.lastIndexOf("."));
			
			apkFileName = odex.getAbsolutePath() + apkname;
			dexFile = new File(apkFileName);
			
			if (!dexFile.exists())
				dexFile.createNewFile();
			Log.i(TAG, "read dex");
			byte[] dexdata = this.readDexFileFromApk();
			Log.i(TAG, "write apk");
			this.splitPayLoadFromDex(dexdata);
			
			// 配置动态加载环境
			Object currentActivityThread = RefInvoke.invokeStaticMethod(
					"android.app.ActivityThread", "currentActivityThread",
					new Class[] {}, new Object[] {});//获取主线程对象
			
			String packageName = this.getPackageName();//当前apk的包名
			ArrayMap mPackages = (ArrayMap) RefInvoke.getFieldOjbect(
					"android.app.ActivityThread", currentActivityThread,
					"mPackages");
			WeakReference wr = (WeakReference) mPackages.get(packageName);
			//创建被加壳apk的DexClassLoader对象  加载apk内的类和本地代码（c/c++代码）
			DexClassLoader dLoader = new DexClassLoader(apkFileName, odexPath,
					libPath, (ClassLoader) RefInvoke.getFieldOjbect(
							"android.app.LoadedApk", wr.get(), "mClassLoader"));
            //把当前进程的DexClassLoader 设置成了被加壳apk的DexClassLoader  ----有点c++中进程环境的意思~~  
			RefInvoke.setFieldOjbect("android.app.LoadedApk", "mClassLoader",
					wr.get(), dLoader);
			dexFile.delete();
			try{
				Object actObj = dLoader.loadClass("com.kedacom.platform2mc.MainActivity");
				Log.i(TAG, "activity:"+actObj+"has found!");
			}catch(Exception e){
				Log.i(TAG, "activity:"+Log.getStackTraceString(e));
			}
		} catch (Exception e) {
			Log.i(TAG, "error:"+Log.getStackTraceString(e));
			e.printStackTrace();
		}
	}

	@Override
	public void onCreate() {
		// 如果源应用配置有Appliction对象，则替换为源应用Applicaiton，以便不影响源程序逻辑。  
		String appClassName = null;
		try {
			ApplicationInfo ai = this.getPackageManager()
					.getApplicationInfo(this.getPackageName(),
						PackageManager.GET_META_DATA);
			Bundle bundle = ai.metaData;
			if (bundle != null && bundle.containsKey("APPLICATION_CLASS_NAME")) {
				appClassName = bundle.getString("APPLICATION_CLASS_NAME");//className 是配置在xml文件中的。  
			} else {
				Log.i(TAG, "have no application class name");
				return;
			}
		} catch (NameNotFoundException e) {
			Log.i(TAG, "error:"+Log.getStackTraceString(e));
			e.printStackTrace();
		}
		//有值的话调用该Applicaiton
		Object currentActivityThread = RefInvoke.invokeStaticMethod(
				"android.app.ActivityThread", "currentActivityThread",
				new Class[] {}, new Object[] {});	
		Object mBoundApplication = RefInvoke.getFieldOjbect(
				"android.app.ActivityThread", currentActivityThread,
				"mBoundApplication");
		Object loadedApkInfo = RefInvoke.getFieldOjbect(
				"android.app.ActivityThread$AppBindData",
				mBoundApplication, "info");
			
		//把当前进程的mApplication 设置成了null 
		RefInvoke.setFieldOjbect("android.app.LoadedApk", "mApplication",
				loadedApkInfo, null);
		Object oldApplication = RefInvoke.getFieldOjbect(
				"android.app.ActivityThread", currentActivityThread,
				"mInitialApplication");
		ArrayList<Application> mAllApplications = (ArrayList<Application>) RefInvoke
				.getFieldOjbect("android.app.ActivityThread",
						currentActivityThread, "mAllApplications");
		mAllApplications.remove(oldApplication);//删除oldApplication 
		ApplicationInfo appinfo_In_LoadedApk = (ApplicationInfo) RefInvoke
				.getFieldOjbect("android.app.LoadedApk", loadedApkInfo,
						"mApplicationInfo");
		ApplicationInfo appinfo_In_AppBindData = (ApplicationInfo) RefInvoke
				.getFieldOjbect("android.app.ActivityThread$AppBindData",
						mBoundApplication, "appInfo");
		appinfo_In_LoadedApk.className = appClassName;
		appinfo_In_AppBindData.className = appClassName;
		Application app = (Application) RefInvoke.invokeMethod(
				"android.app.LoadedApk", "makeApplication", loadedApkInfo,
				new Class[] { boolean.class, Instrumentation.class },
				new Object[] { false, null });//执行 makeApplication（false,null）
		RefInvoke.setFieldOjbect("android.app.ActivityThread",
				"mInitialApplication", currentActivityThread, app);

		ArrayMap mProviderMap = (ArrayMap) RefInvoke.getFieldOjbect(
				"android.app.ActivityThread", currentActivityThread,
				"mProviderMap");
		
		Iterator it = mProviderMap.values().iterator();
		while (it.hasNext()) {
			Object providerClientRecord = it.next();
			Object localProvider = RefInvoke.getFieldOjbect(
					"android.app.ActivityThread$ProviderClientRecord",
					providerClientRecord, "mLocalProvider");
			RefInvoke.setFieldOjbect("android.content.ContentProvider",
					"mContext", localProvider, app);
		}		
		Log.i(TAG, "app:"+app);		
		app.onCreate();
	}
	
	/** 
     * 从apk包里面获取dex文件内容（byte） 
     * @return 
     * @throws IOException 
     */ 
	private byte[] readDexFileFromApk() throws IOException {
		apkSize = 0;
		byte []dexFileContent = new byte[INIT_SIZE];
		boolean flag = false;
		
		ZipInputStream localZipInputStream = new ZipInputStream(
				new BufferedInputStream(new FileInputStream(
						this.getApplicationInfo().sourceDir)));
		while (true) {
			ZipEntry localZipEntry = localZipInputStream.getNextEntry();
			if (localZipEntry == null) {
				localZipInputStream.close();
				break;
			}
			if (localZipEntry.getName().equals("classes.dex")) {
				Log.i(TAG, "reading dex...");
				byte[] arrayOfByte = new byte[READ_DATA];
				int x = 0;
				while (true) {
					int i = localZipInputStream.read(arrayOfByte, 0, READ_DATA);
					if (i == -1)
						break;
					System.arraycopy(arrayOfByte, 0, dexFileContent, x, i);
					x += i;
					apkSize = x;								
				}
				flag = true;
			}
			localZipInputStream.closeEntry();
			if(flag) break;
		}
		localZipInputStream.close();
		return dexFileContent;
	}

	/** 
     * 释放被加壳的apk文件 
     * @param data 
     * @throws IOException 
     */ 
	private void splitPayLoadFromDex(byte[] apkdata) throws IOException {
		int ablen = apkSize;
		//取被加壳apk的长度   这里的长度取值，对应加壳时长度的赋值都可以做些简化 
		byte[] dexlen = new byte[4];
		System.arraycopy(apkdata, ablen - 4, dexlen, 0, 4);
		printHexString(dexlen);
		ByteArrayInputStream bais = new ByteArrayInputStream(dexlen);
		DataInputStream in = new DataInputStream(bais);
		int readInt = in.readInt();
		System.out.println("readInt:"+Integer.toHexString(readInt));
		byte[] newdex = new byte[readInt];
		//把被加壳apk内容拷贝到newdex中 
		System.arraycopy(apkdata, ablen - 4 - readInt, newdex, 0, readInt);

		//对源程序Apk进行解密
		newdex = decrypt(newdex);
		
		 //写入apk文件 
		File file = new File(apkFileName);
		try {
			FileOutputStream localFileOutputStream = new FileOutputStream(file);
			localFileOutputStream.write(newdex);
			localFileOutputStream.close();
		} catch (IOException localIOException) {
			throw new RuntimeException(localIOException);
		}
	}

	//直接返回数据，读者可以添加自己解密方法
	private byte[] decrypt(byte[] srcdata) {
		for(int i=0;i<srcdata.length;i++){
			srcdata[i] = (byte)(0xFF ^ srcdata[i]);
		}
		return srcdata;
	}
	
	public static void printHexString( byte[] b) {    
		for (int i = 0; i < b.length; i++) {   
		String hex = Integer.toHexString(b[i] & 0xFF);   
	    if (hex.length() == 1) {   
	       hex = '0' + hex;   
	     }   
	    Log.i(TAG, "apkSize:" + hex.toUpperCase());
		}   
		  
	}
}

