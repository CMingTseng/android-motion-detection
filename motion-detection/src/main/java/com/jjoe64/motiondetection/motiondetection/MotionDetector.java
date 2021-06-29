package com.jjoe64.motiondetection.motiondetection;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static android.content.ContentValues.TAG;

public class MotionDetector {
    class MotionDetectorThread extends Thread {
        private AtomicBoolean isRunning = new AtomicBoolean(true);

        public void stopDetection() {
            isRunning.set(false);
        }

        @Override
        public void run() {
            while (isRunning.get()) {
                long now = System.currentTimeMillis();
                if (now-lastCheck > checkInterval) {
                    lastCheck = now;

                    if (nextData.get() != null) {
                        int[] img = ImageProcessing.decodeYUV420SPtoLuma(nextData.get(), nextWidth.get(), nextHeight.get());

                        // check if it is too dark
                        int lumaSum = 0;
                        for (int i : img) {
                            lumaSum += i;
                        }
                        if (lumaSum < minLuma) {
                            if (motionDetectorCallback != null) {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        motionDetectorCallback.onTooDark();
                                    }
                                });
                            }
                        } else if (detector.detect(img, nextWidth.get(), nextHeight.get())) {
                            // check
                            if (motionDetectorCallback != null) {
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        motionDetectorCallback.onMotionDetected();
                                    }
                                });
                            }
                        }
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final AggregateLumaMotionDetection detector;
    private long checkInterval = 500;
    private long lastCheck = 0;
    private MotionDetectorCallback motionDetectorCallback;
    private Handler mHandler = new Handler();

    private AtomicReference<byte[]> nextData = new AtomicReference<>();
    private AtomicInteger nextWidth = new AtomicInteger();
    private AtomicInteger nextHeight = new AtomicInteger();
    private int minLuma = 1000;
    private MotionDetectorThread worker;

    private Camera mCamera;
    private boolean inPreview;
    private SurfaceHolder previewHolder;
    private Context mContext;
    private SurfaceView mSurface;

    public MotionDetector(Context context, SurfaceView previewSurface) {
        detector = new AggregateLumaMotionDetection();
        mContext = context;
        mSurface = previewSurface;
    }

    public void setMotionDetectorCallback(MotionDetectorCallback motionDetectorCallback) {
        this.motionDetectorCallback = motionDetectorCallback;
    }

    public void consume(byte[] data, int width, int height) {
        nextData.set(data);
        nextWidth.set(width);
        nextHeight.set(height);
    }

    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    public void setMinLuma(int minLuma) {
        this.minLuma = minLuma;
    }

    public void setLeniency(int l) {
        detector.setLeniency(l);
    }

    public void onResume() {
        if (checkCameraHardware()) {
            mCamera = getCameraInstance();

            worker = new MotionDetectorThread();
            worker.start();

            // configure preview
            previewHolder = mSurface.getHolder();
            previewHolder.addCallback(surfaceCallback);
            previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    public boolean checkCameraHardware() {
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    private Camera getCameraInstance(){
        Camera c = null;

        try {
            if (Camera.getNumberOfCameras() >= 2) {
                //if you want to open front facing camera use this line
                c = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            } else {
                c = Camera.open();
            }
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            //txtStatus.setText("Kamera nicht zur Benutzung freigegeben");
        }
        return c; // returns null if camera is unavailable
    }

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) return;
            final Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) return;
//            Log.e("MotionDetector", "PreviewCallback get data"+data.length);
//            Log.e("MotionDetector", "PreviewCallback get size"+size.width+"__"+size.height);

            consume(data, size.width, size.height);
            //FIXME here get data to save file
            try {
                Thread.sleep(4000);
                pictureCallback.onPictureTaken(data,cam);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                mCamera.setPreviewDisplay(previewHolder);
                mCamera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {
                Log.e("MotionDetector", "Exception in setPreviewDisplay()", t);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            final Camera.Parameters parameters = mCamera.getParameters();
            final Camera.Size size = getBestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d("MotionDetector", "Using width=" + size.width + " height=" + size.height);
            }
            mCamera.setParameters(parameters);
            mCamera.startPreview();
            inPreview = true;
            //FIXME MotionDetec can not with takePicture
//            Log.e(TAG, "Use Preview Taking picture ");

//            try {
//                Thread.sleep(4000);
//                mCamera.takePicture(null, null, pictureCallback);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Ignore
        }
    };

    private Camera.PictureCallback pictureCallback =new Camera.PictureCallback(){

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.e("MotionDetector", "Camera onPictureTaken Image Captured Successfully ");
            if (data!=null){
                Log.e("MotionDetector", "Camera onPictureTaken Image Captured Successfully get data "+data.length);
//                previewCallback.onPreviewFrame(data,camera);
            }
//            if (data != null) {
//                // Intent mIntent = new Intent();
//                // mIntent.putExtra("image",imageData);
//                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1)
//                    audioMgr.setStreamMute(AudioManager.STREAM_SYSTEM, false);
//
//                WindowManager winMan = (WindowManager) context
//                        .getSystemService(WINDOW_SERVICE);
//                winMan.removeView(surfaceView);
//
//                try {
//                    BitmapFactory.Options opts = new BitmapFactory.Options();
//                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0,
//                            data.length, opts);
//                    bitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, false);
//                    int width = bitmap.getWidth();
//                    int height = bitmap.getHeight();
//                    int newWidth = 300;
//                    int newHeight = 300;
//
//                    // calculate the scale - in this case = 0.4f
//                    float scaleWidth = ((float) newWidth) / width;
//                    float scaleHeight = ((float) newHeight) / height;
//
//                    // createa matrix for the manipulation
//                    Matrix matrix = new Matrix();
//                    // resize the bit map
//                    matrix.postScale(scaleWidth, scaleHeight);
//                    // rotate the Bitmap
//                    matrix.postRotate(-90);
//                    Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
//                            width, height, matrix, true);
//
//                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
//                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 40,
//                            bytes);
//
//                    // you can create a new file name "test.jpg" in sdcard
//                    // folder.
//                    File f = new File(Environment.getExternalStorageDirectory()
//                            + File.separator + "test.jpg");
//
//                    System.out.println("File F : " + f);
//
//                    f.createNewFile();
//                    // write the bytes in file
//                    FileOutputStream fo = new FileOutputStream(f);
//                    fo.write(bytes.toByteArray());
//                    // remember close de FileOutput
//                    fo.close();
//                    context.stopService(new Intent(context,GetBackCoreService.class));
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                // StoreByteImage(mContext, imageData, 50,"ImageName");
//                // setResult(FOTO_MODE, mIntent);
//            }
//
//            GetBackCoreService getBackCoreService=new GetBackCoreService();
//            getBackCoreService.stopSelf();
        }
    };

    private Camera.ErrorCallback errorCallback =new Camera.ErrorCallback(){

        @Override
        public void onError(int error, Camera camera) {
            Log.e(TAG, "Camera get Error : " + error );
//            WindowManager winMan = (WindowManager) context
//                    .getSystemService(WINDOW_SERVICE);
//            winMan.removeView(surfaceView);
//            callback.onCaptureError(-1);
        }
    };

    private static Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) result = size;
                }
            }
        }

        return result;
    }

    public void onPause() {
        releaseCamera();
        if (previewHolder != null) previewHolder.removeCallback(surfaceCallback);
        if (worker != null) worker.stopDetection();
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.setPreviewCallback(null);
            if (inPreview) mCamera.stopPreview();
            inPreview = false;
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }
}
