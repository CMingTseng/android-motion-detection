package com.jjoe64.motiondetection;

public interface CaptureActionCallback {

    public void onPhotoCaptured(String filePath);

    public void onCaptureError(int errorCode);

    public static enum ErrorCode {

    }
}