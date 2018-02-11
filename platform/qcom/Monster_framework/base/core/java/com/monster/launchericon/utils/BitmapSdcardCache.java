package com.monster.launchericon.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.Log;

/**
 * Created by antino on 16-11-8.
 */
public class BitmapSdcardCache {
    private ExecutorService mPool = Executors.newFixedThreadPool(10);
    public static String THEME_SDCARD_PATH = Environment.getExternalStorageDirectory()+"/theme/icons/";
    public static String CONFIG_FILE_PATH = Environment.getExternalStorageDirectory()+"/theme/icons/themeversion";
    public static String CONFIG_FILE_NAME = "themeversion";
    private static final Object sLOCK = new Object();
    public static boolean CLEANING = false;
    Resources res;
    public BitmapSdcardCache(Resources res){
        this.res = res;
    }

    public void save(String fileName , Bitmap b){
       mPool.execute(new TaskSaver(fileName,b,null));
    }

    public void save(Drawable drawable,String fileName){
        mPool.execute(new TaskSaver(fileName,null,drawable));
    }

    public void remove(String fileName){
        mPool.execute(new TaskRemover(fileName));
    }

    /**
     * Get icon from storage : {@link #THEME_SDCARD_PATH}
     * @param fileName
     * @return
     */
    public Bitmap getIcon(String fileName){
    	android.util.Log.i("BitmapCache","getIcon from SDCardCache by fileName:"+fileName);
        if(CLEANING)return null;
        Bitmap bitmap;
        try {
            fileName = THEME_SDCARD_PATH + fileName + ".png";
            File f = new File(fileName);
            if (!f.exists()) {
                return null;
            }
            BitmapFactory.Options opt2 = new BitmapFactory.Options();
            opt2.inSampleSize = 1;
            bitmap = BitmapFactory.decodeStream(new FileInputStream(f), null,
                    opt2);
        }catch (FileNotFoundException e){
            android.util.Log.i(Contents.TAG,"-------------------- getIcon -------------------",e);
            bitmap = null;
        }catch (Exception e){
            android.util.Log.i(Contents.TAG,"-------------------- getIcon -------------------",e);
            bitmap = null;
        }
        return bitmap;
    }

    /**
     *  Get icon from storage : {@link #THEME_SDCARD_PATH}
     * @param fileName
     * @return
     */
    public Drawable getIconDrawable(String fileName){
        Bitmap bitmap = getIcon(fileName);
        return bitmap==null?null:new BitmapDrawable(res,bitmap);
    }

    class TaskSaver implements Runnable {
        private String fileName;
        private Bitmap bitmap;
        private Drawable drawable;
        TaskSaver(String fileName,Bitmap bitmap,Drawable drawable){
            this.fileName = fileName;
            this.bitmap = bitmap;
            this.drawable = drawable;
        }
        /**
         * Starts executing the active part of the class' code. This method is
         * called when a thread is started that has been created with a class which
         * implements {@code Runnable}.
         */
        @Override
        public void run() {
            try {
                if (fileName != null) {
                    if (bitmap != null) {
                        saveBitmap(fileName, bitmap);
                    }
                    if (drawable != null) {
                        saveBitmap(fileName, (drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : PhotoUtils.drawable2bitmap(drawable)));
                    }
                }
            }catch (IOException e){
                android.util.Log.i(Contents.TAG,"-------------------- 1 : saveBitmap -------------------",e);
            } catch(Exception e){
                android.util.Log.i(Contents.TAG,"-------------------- 2 : saveBitmap -------------------",e);
            }
        }

        private void saveBitmap(String fileName,Bitmap bitmap)throws Exception {
            FileOutputStream fot = null;
            try {
                //first find the root dir is exist or not
                File rootDir = new File(THEME_SDCARD_PATH);
                if (!rootDir.exists() || !rootDir.isDirectory()) {
                    boolean create = rootDir.mkdirs();
                    rootDir.setExecutable(true);//设置可执行权限
                    rootDir.setReadable(true);//设置可读权限
                    rootDir.setWritable(true);//设置可写权限
                }
                //second save the icon to root dir.
                File resultFile = new File(THEME_SDCARD_PATH + fileName + ".png");
                resultFile.createNewFile();
                resultFile.setExecutable(true);//设置可执行权限
                resultFile.setReadable(true);//设置可读权限
                resultFile.setWritable(true);//设置可写权限
                fot = new FileOutputStream(resultFile);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fot);
                fot.flush();
            }finally {
                if(fot!=null){
                    fot.close();
                }
            }
        }
    }

    class TaskRemover implements  Runnable{
        private String fileName;
        TaskRemover(String fileName){
            this.fileName = fileName;
        }

        /**
         * Starts executing the active part of the class' code. This method is
         * called when a thread is started that has been created with a class which
         * implements {@code Runnable}.
         */
        @Override
        public void run() {
            deleteFile(fileName);
        }
        public boolean  deleteFile(String fileName) {
            String filePath = THEME_SDCARD_PATH +fileName+".png";
            boolean flag = false;
            File file = new File(filePath);
            // 路径为文件且不为空则进行删除
            if (file.isFile() && file.exists()) {
                file.delete();
                flag = true;
            }
            return flag;
        }
    }

    class TaskClean implements Runnable {
        @Override
        public void run() {
            deleteAllFiles();
        }
    }

    public void clean(){
        synchronized (sLOCK) {
            CLEANING = true;
            mPool.execute(new TaskClean());
        }
    }

    private void deleteAllFiles() {
        Log.d("theme.icon", "deleteAllFiles : " + THEME_SDCARD_PATH);
        File file = new File(THEME_SDCARD_PATH);
        try {
            if (file != null && file.exists() && file.isDirectory()) {
                File[] childFiles = file.listFiles();
                if (childFiles != null && childFiles.length > 0) {
                    for (int i = 0; i < childFiles.length; i++) {
                        boolean isConfigFile = CONFIG_FILE_NAME.equals(childFiles[i].getName());
                        if(childFiles[i].isFile() && !isConfigFile)
                            childFiles[i].delete();
                    }
                }
            }
        }catch (Exception e){
            Log.e("theme.icon","deleteAllFiles error"+e.toString());
        }finally {
            CLEANING = false;
        }
    }
}
