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
	private String apkFileName;  //����apk�ļ�·��
	private String odexPath;     //dex·��
	private String libPath;		 //so·��
	private String apkname = "temp.apk";
	private File dexFile;
	private int apkSize;
	private static final int READ_DATA = 2048;
	private static final int INIT_SIZE = 23000000;
	private SharedPreferences sp;

	//����context ��ֵ
	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		try {
			//���������ļ���payload_odex, ˽�еģ���д���ļ�Ŀ¼
			File odex = this.getDir("payload_odex", MODE_PRIVATE);
			odexPath = odex.getAbsolutePath();
			String appPath = getPackageResourcePath();  //app·��
			libPath = PATH + appPath.substring(10,appPath.lastIndexOf("."));
			
			apkFileName = odex.getAbsolutePath() + apkname;
			dexFile = new File(apkFileName);
			
			if (!dexFile.exists())
				dexFile.createNewFile();
			Log.i(TAG, "read dex");
			byte[] dexdata = this.readDexFileFromApk();
			Log.i(TAG, "write apk");
			this.splitPayLoadFromDex(dexdata);
			
			// ���ö�̬���ػ���
			Object currentActivityThread = RefInvoke.invokeStaticMethod(
					"android.app.ActivityThread", "currentActivityThread",
					new Class[] {}, new Object[] {});//��ȡ���̶߳���
			
			String packageName = this.getPackageName();//��ǰapk�İ���
			ArrayMap mPackages = (ArrayMap) RefInvoke.getFieldOjbect(
					"android.app.ActivityThread", currentActivityThread,
					"mPackages");
			WeakReference wr = (WeakReference) mPackages.get(packageName);
			//�������ӿ�apk��DexClassLoader����  ����apk�ڵ���ͱ��ش��루c/c++���룩
			DexClassLoader dLoader = new DexClassLoader(apkFileName, odexPath,
					libPath, (ClassLoader) RefInvoke.getFieldOjbect(
							"android.app.LoadedApk", wr.get(), "mClassLoader"));
            //�ѵ�ǰ���̵�DexClassLoader ���ó��˱��ӿ�apk��DexClassLoader  ----�е�c++�н��̻�������˼~~  
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
		// ���ԴӦ��������Appliction�������滻ΪԴӦ��Applicaiton���Ա㲻Ӱ��Դ�����߼���  
		String appClassName = null;
		try {
			ApplicationInfo ai = this.getPackageManager()
					.getApplicationInfo(this.getPackageName(),
						PackageManager.GET_META_DATA);
			Bundle bundle = ai.metaData;
			if (bundle != null && bundle.containsKey("APPLICATION_CLASS_NAME")) {
				appClassName = bundle.getString("APPLICATION_CLASS_NAME");//className ��������xml�ļ��еġ�  
			} else {
				Log.i(TAG, "have no application class name");
				return;
			}
		} catch (NameNotFoundException e) {
			Log.i(TAG, "error:"+Log.getStackTraceString(e));
			e.printStackTrace();
		}
		//��ֵ�Ļ����ø�Applicaiton
		Object currentActivityThread = RefInvoke.invokeStaticMethod(
				"android.app.ActivityThread", "currentActivityThread",
				new Class[] {}, new Object[] {});	
		Object mBoundApplication = RefInvoke.getFieldOjbect(
				"android.app.ActivityThread", currentActivityThread,
				"mBoundApplication");
		Object loadedApkInfo = RefInvoke.getFieldOjbect(
				"android.app.ActivityThread$AppBindData",
				mBoundApplication, "info");
			
		//�ѵ�ǰ���̵�mApplication ���ó���null 
		RefInvoke.setFieldOjbect("android.app.LoadedApk", "mApplication",
				loadedApkInfo, null);
		Object oldApplication = RefInvoke.getFieldOjbect(
				"android.app.ActivityThread", currentActivityThread,
				"mInitialApplication");
		ArrayList<Application> mAllApplications = (ArrayList<Application>) RefInvoke
				.getFieldOjbect("android.app.ActivityThread",
						currentActivityThread, "mAllApplications");
		mAllApplications.remove(oldApplication);//ɾ��oldApplication 
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
				new Object[] { false, null });//ִ�� makeApplication��false,null��
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
     * ��apk�������ȡdex�ļ����ݣ�byte�� 
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
     * �ͷű��ӿǵ�apk�ļ� 
     * @param data 
     * @throws IOException 
     */ 
	private void splitPayLoadFromDex(byte[] apkdata) throws IOException {
		int ablen = apkSize;
		//ȡ���ӿ�apk�ĳ���   ����ĳ���ȡֵ����Ӧ�ӿ�ʱ���ȵĸ�ֵ��������Щ�� 
		byte[] dexlen = new byte[4];
		System.arraycopy(apkdata, ablen - 4, dexlen, 0, 4);
		printHexString(dexlen);
		ByteArrayInputStream bais = new ByteArrayInputStream(dexlen);
		DataInputStream in = new DataInputStream(bais);
		int readInt = in.readInt();
		System.out.println("readInt:"+Integer.toHexString(readInt));
		byte[] newdex = new byte[readInt];
		//�ѱ��ӿ�apk���ݿ�����newdex�� 
		System.arraycopy(apkdata, ablen - 4 - readInt, newdex, 0, readInt);

		//��Դ����Apk���н���
		newdex = decrypt(newdex);
		
		 //д��apk�ļ� 
		File file = new File(apkFileName);
		try {
			FileOutputStream localFileOutputStream = new FileOutputStream(file);
			localFileOutputStream.write(newdex);
			localFileOutputStream.close();
		} catch (IOException localIOException) {
			throw new RuntimeException(localIOException);
		}
	}

	//ֱ�ӷ������ݣ����߿�������Լ����ܷ���
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

