package ${package};

import java.lang.reflect.Field;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.res.Resources;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.threed.jpct.Camera;
import com.threed.jpct.Config;
import com.threed.jpct.FrameBuffer;
import com.threed.jpct.Light;
import com.threed.jpct.Loader;
import com.threed.jpct.Logger;
import com.threed.jpct.Object3D;
import com.threed.jpct.RGBColor;
import com.threed.jpct.SimpleVector;
import com.threed.jpct.Texture;
import com.threed.jpct.TextureManager;
import com.threed.jpct.World;
import com.threed.jpct.util.MemoryHelper;

import static ${package}.R.*;

/**
 * Hello world!
 */
public class App extends Activity {


    // Used to handle pause and resume...
    private static App master = null;

    private GLSurfaceView mGLView;
    private MyRenderer renderer = null;
    private FrameBuffer fb = null;
    private World world = null;
    private int move = 0;
    private float turn = 0;
    private RGBColor back = new RGBColor(50, 50, 100);

    private float touchTurn = 0;
    private float touchTurnUp = 0;

    private float xpos = -1;
    private float ypos = -1;

    private Texture planeReplace = null;

    private Object3D plane = null;
    private Object3D tree2 = null;
    private Object3D tree1 = null;
    private Object3D grass = null;
    private Texture font = null;

    private Object3D box = null;
    private Object3D rock = null;

    private Light sun = null;

    protected void onCreate(Bundle savedInstanceState) {
        Logger.log("onCreate");

        if (master != null) {
            copy(master);
        }

        super.onCreate(savedInstanceState);
        mGLView = new GLSurfaceView(getApplication());

        mGLView.setEGLConfigChooser(new GLSurfaceView.EGLConfigChooser() {
            public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
                // Ensure that we get a 16bit framebuffer. Otherwise, we'll fall
                // back to Pixelflinger on some device (read: Samsung I7500)
                int[] attributes = new int[]{EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE};
                EGLConfig[] configs = new EGLConfig[1];
                int[] result = new int[1];
                egl.eglChooseConfig(display, attributes, configs, 1, result);
                return configs[0];
            }
        });

        renderer = new MyRenderer();
        mGLView.setRenderer(renderer);
        setContentView(mGLView);
    }

    @Override
    protected void onPause() {
        Logger.log("onPause");
        super.onPause();
        mGLView.onPause();
    }

    @Override
    protected void onResume() {
        Logger.log("onResume");
        super.onResume();
        mGLView.onResume();
    }

    protected void onStop() {
        Logger.log("onStop");
        super.onStop();
    }

    private void copy(Object src) {
        try {
            Logger.log("Copying data from master Activity!");
            Field[] fs = src.getClass().getDeclaredFields();
            for (Field f : fs) {
                f.setAccessible(true);
                f.set(this, f.get(src));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean onTouchEvent(MotionEvent me) {
        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            xpos = me.getX();
            ypos = me.getY();
            return true;
        }

        if (me.getAction() == MotionEvent.ACTION_UP) {
            xpos = -1;
            ypos = -1;
            touchTurn = 0;
            touchTurnUp = 0;
            return true;
        }

        if (me.getAction() == MotionEvent.ACTION_MOVE) {
            float xd = me.getX() - xpos;
            float yd = me.getY() - ypos;

            xpos = me.getX();
            ypos = me.getY();

            touchTurn = xd / 100f;
            touchTurnUp = yd / 100f;
            return true;
        }

        try {
            Thread.sleep(15);
        } catch (Exception e) {
            // Doesn't matter here...
        }

        return super.onTouchEvent(me);
    }

    public boolean onKeyDown(int keyCode, KeyEvent msg) {

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            move = 2;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            move = -2;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            turn = 0.05f;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            turn = -0.05f;
            return true;
        }

        return super.onKeyDown(keyCode, msg);
    }

    public boolean onKeyUp(int keyCode, KeyEvent msg) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            move = 0;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            move = 0;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            turn = 0;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            turn = 0;
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            TextureManager.getInstance().replaceTexture("grassy", planeReplace);
            return true;
        }

        return super.onKeyUp(keyCode, msg);
    }

    protected boolean isFullscreenOpaque() {
        return true;
    }

    class MyRenderer implements GLSurfaceView.Renderer {

        private int fps = 0;
        private int lfps = 0;

        private long time = System.currentTimeMillis();

        private boolean stop = false;

        private SimpleVector sunRot = new SimpleVector(0, 0.05f, 0);

        public MyRenderer() {
            Config.maxPolysVisible = 500;
            Config.farPlane = 1500;
            Config.glTransparencyMul = 0.1f;
            Config.glTransparencyOffset = 0.1f;
            Config.useVBO = true;

            Texture.defaultToMipmapping(true);
            Texture.defaultTo4bpp(true);
        }

        public void stop() {
            stop = true;
        }

        public void onSurfaceChanged(GL10 gl, int w, int h) {
            if (fb != null) {
                fb.dispose();
            }

            fb = new FrameBuffer(gl, w, h);

            if (master == null) {
                world = new World();
                Resources res = getResources();

                TextureManager tm = TextureManager.getInstance();

                Texture grass2 = new Texture(res.openRawResource(R.raw.grassy));
                Texture leaves = new Texture(res.openRawResource(R.raw.tree2y));
                Texture leaves2 = new Texture(res.openRawResource(R.raw.tree3y));
                Texture rocky = new Texture(res.openRawResource(R.raw.rocky));
                Texture planetex = new Texture(res.openRawResource(R.raw.planetex));

                planeReplace = new Texture(res.openRawResource(R.raw.rocky));
                font = new Texture(res.openRawResource(R.raw.numbers));
                font.setMipmap(false);

                tm.addTexture("grass2", grass2);
                tm.addTexture("leaves", leaves);
                tm.addTexture("leaves2", leaves2);
                tm.addTexture("rock", rocky);
                tm.addTexture("grassy", planetex);

                plane = Loader.loadSerializedObject(res.openRawResource(R.raw.serplane));
                rock = Loader.loadSerializedObject(res.openRawResource(R.raw.serrock));
                tree1 = Loader.loadSerializedObject(res.openRawResource(R.raw.sertree1));
                tree2 = Loader.loadSerializedObject(res.openRawResource(R.raw.sertree2));
                grass = Loader.loadSerializedObject(res.openRawResource(R.raw.sergrass));

                grass.translate(-45, -17, -50);
                grass.rotateZ((float) Math.PI);
                rock.translate(0, 0, -90);
                rock.rotateX(-(float) Math.PI / 2);
                tree1.translate(-50, -92, -50);
                tree1.rotateZ((float) Math.PI);
                tree2.translate(60, -95, 10);
                tree2.rotateZ((float) Math.PI);
                plane.rotateX((float) Math.PI / 2f);
                plane.setName("plane");
                tree1.setName("tree1");
                tree2.setName("tree2");
                grass.setName("grass");
                rock.setName("rock");

                world.addObject(plane);
                world.addObject(tree1);
                world.addObject(tree2);
                world.addObject(grass);
                world.addObject(rock);

                plane.strip();
                tree1.strip();
                tree2.strip();
                grass.strip();
                rock.strip();

                grass.setTransparency(10);
                tree1.setTransparency(40);
                tree2.setTransparency(40);

                RGBColor dark = new RGBColor(100, 100, 100);
                tree1.setAdditionalColor(dark);
                tree2.setAdditionalColor(dark);
                grass.setAdditionalColor(dark);

                world.setAmbientLight(20, 20, 20);
                world.buildAllObjects();

                sun = new Light(world);
                sun.setIntensity(250, 250, 250);

                Camera cam = world.getCamera();
                cam.moveCamera(Camera.CAMERA_MOVEOUT, 250);
                cam.moveCamera(Camera.CAMERA_MOVEUP, 100);
                cam.lookAt(plane.getTransformedCenter());

                SimpleVector sv = new SimpleVector();
                sv.set(plane.getTransformedCenter());
                sv.y -= 300;
                sv.x -= 100;
                sv.z += 200;
                sun.setPosition(sv);

                MemoryHelper.compact();

                if (master == null) {
                    Logger.log("Saving master Activity!");
                    master = App.this;
                }
            }
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Logger.log("onSurfaceCreated");
        }

        public void onDrawFrame(GL10 gl) {

            try {
                if (!stop) {
                    Camera cam = world.getCamera();
                    if (turn != 0) {
                        world.getCamera().rotateY(-turn);
                    }

                    if (touchTurn != 0) {
                        world.getCamera().rotateY(touchTurn);
                        touchTurn = 0;
                    }

                    if (touchTurnUp != 0) {
                        world.getCamera().rotateX(touchTurnUp);
                        touchTurnUp = 0;
                    }

                    if (move != 0) {
                        world.getCamera().moveCamera(cam.getDirection(), move);
                    }

                    fb.clear(back);
                    world.renderScene(fb);
                    world.draw(fb);
                    blitNumber(lfps, 5, 5);

                    fb.display();

                    if (box != null) {
                        box.rotateX(0.01f);
                    }

                    if (sun != null) {
                        sun.rotate(sunRot, plane.getTransformedCenter());
                    }

                    if (System.currentTimeMillis() - time >= 1000) {
                        lfps = fps;
                        fps = 0;
                        time = System.currentTimeMillis();
                    }
                    fps++;
                } else {
                    if (fb != null) {
                        fb.dispose();
                        fb = null;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Logger.log("Drawing thread terminated!", Logger.MESSAGE);
            }
        }

        private void blitNumber(int number, int x, int y) {
            if (font != null) {
                String sNum = Integer.toString(number);

                for (int i = 0; i < sNum.length(); i++) {
                    char cNum = sNum.charAt(i);
                    int iNum = cNum - 48;
                    fb.blit(font, iNum * 5, 0, x, y, 5, 9, FrameBuffer.TRANSPARENT_BLITTING);
                    x += 5;
                }
            }
        }
    }
}
