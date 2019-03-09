package com.richie.multimedialearning.camera;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.AttributeSet;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.richie.easylog.ILogger;
import com.richie.easylog.LoggerFactory;

import java.util.Arrays;

/**
 * @author Richie on 2019.03.07
 * https://juejin.im/post/5a33a5106fb9a04525782db5
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2SurfacePreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private final ILogger logger = LoggerFactory.getLogger(Camera2SurfacePreview.class);
    private String mCameraId;
    private Context mContext;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private SurfaceHolder mSurfaceHolder;
    private ImageReader mImageReader;
    private Handler mCallbackHandler;

    public Camera2SurfacePreview(Context context) {
        super(context);
        init();
    }

    public Camera2SurfacePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Camera2SurfacePreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        logger.debug("surfaceCreated() called with: holder = [" + holder + "]");
        HandlerThread handlerThread = new HandlerThread("camera2");
        handlerThread.start();
        mCallbackHandler = new Handler(handlerThread.getLooper());
        checkCamera();
        openCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        logger.debug("surfaceChanged() called with: format = [" + format + "], width = [" + width + "], height = [" + height + "]");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        logger.debug("surfaceDestroyed() called with: holder = [" + holder + "]");
        closeCameraPreview();
        mCameraDevice.close();
        mImageReader.close();
        mCallbackHandler.getLooper().quitSafely();
    }

    private void init() {
        mContext = getContext();
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
    }

    private void checkCamera() {
        CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = cameraManager.getCameraIdList();
            for (String s : cameraIdList) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(s);
                Integer lensFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Integer supportedHardwareLevel = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                logger.debug("lensFacing:{}, sensorOrientation:{}, supportedHardwareLevel:{}",
                        lensFacing, sensorOrientation, supportedHardwareLevel);
                if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                    mCameraId = s;
                    //break;
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    int[] outputFormats = map.getOutputFormats();
                    Size[] outputSizes = map.getOutputSizes(SurfaceHolder.class);
                    logger.debug("format:{}, size:{}", Arrays.toString(outputFormats), Arrays.toString(outputSizes));
                    // size:[3264x2448, 1920x1920, 640x480, 320x240, 1280x720, 2048x1536, 1920x1080, 2448x2448, 3264x1840, 1536x864]
                }
            }
            logger.debug("front camera available:{}", mCameraId);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    private void openCamera() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (mCameraId == null) {
            return;
        }

        CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    logger.debug("onOpened");
                    mCameraDevice = camera;

                    mImageReader = ImageReader.newInstance(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT, ImageFormat.YUV_420_888, 8);
                    mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            logger.verbose("onImageAvailable");
                            Image image = reader.acquireLatestImage();
                            //我们可以将这帧数据转成字节数组，类似于Camera1的PreviewCallback回调的预览帧数据
                            //ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            //byte[] data = new byte[buffer.remaining()];
                            //buffer.get(data);
                            image.close();
                        }
                    }, mCallbackHandler);

                    createCameraPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    logger.debug("onDisconnected");
                    camera.close();
                    mCameraDevice = null;
                }

                @Override
                public void onClosed(@NonNull CameraDevice camera) {
                    super.onClosed(camera);
                    logger.debug("onClosed() called with: camera = [" + camera + "]");
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    logger.warn("onError:{}", error);
                    camera.close();
                    mCameraDevice = null;
                }
            }, mCallbackHandler);

            CameraCaptureSession.CaptureCallback captureCallback;
        } catch (Exception e) {
            logger.error(e);
        }
    }

    private void createCameraPreview() {
        try {
            final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface surface = mSurfaceHolder.getSurface();
            captureRequestBuilder.addTarget(surface);
            Surface imageReaderSurface = mImageReader.getSurface();
            captureRequestBuilder.addTarget(imageReaderSurface);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            mCameraDevice.createCaptureSession(Arrays.asList(surface, imageReaderSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    logger.debug("onConfigured");
                    mCameraCaptureSession = session;
                    CaptureRequest captureRequest = captureRequestBuilder.build();
                    try {
                        session.setRepeatingRequest(captureRequest, null, null);
                    } catch (CameraAccessException e) {
                        logger.error(e);
                    }

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    logger.warn("onConfigureFailed");
                }
            }, mCallbackHandler);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    private void closeCameraPreview() {
        if (mCameraCaptureSession != null) {
            try {
                mCameraCaptureSession.stopRepeating();
                mCameraCaptureSession.abortCaptures();
                mCameraCaptureSession.close();
            } catch (Exception e) {
                logger.error(e);
            }
            mCameraCaptureSession = null;
        }
    }

    public static boolean hasCamera2(Context mContext) {
        if (mContext == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        try {
            CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            String[] idList = manager.getCameraIdList();
            boolean notFull = true;
            if (idList.length == 0) {
                notFull = false;
            } else {
                for (final String str : idList) {
                    final CameraCharacteristics characteristics = manager.getCameraCharacteristics(str);
                    final int supportLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    if (supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                        notFull = false;
                        break;
                    }
                }
            }
            return notFull;
        } catch (Exception exp) {
            return false;
        }
    }
}
