package pkleczek.gestures;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.PointF;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.view.MotionEventCompat;
import android.view.Menu;
import android.view.MotionEvent;
import android.widget.Toast;

public class MainActivity extends Activity {

	private GLSurfaceView mGLSurfaceView;
	private Renderer mRenderer = new Renderer();

	// JUMP
	/**
	 * Kiedy podniesiono ostatni palec
	 */
	long mjLastReleased = 0;
	long mjFirstPressed = 0;
	long mjSecondPressed = Long.MAX_VALUE;

	/**
	 * Przez ile czasu nie mozna dotknac ekranu, aby zostalo zarejestrowane
	 * rozpoczecie/zakonczenie skoku.
	 */
	private static final int JUMP_RELEASE_TIME = 50;

	/**
	 * Ile czasu moze uplynac pomiedzy nacisnieciem pierwszym i drugim palcem.
	 */
	private static final int JUMP_TAP_TIME = 50;

	// ROTATE
	private PointF mrPivotPosition = new PointF();
	private PointF mrHandlerPosition = new PointF();

	/**
	 * Wyjscie poza otoczenie poczatkowej pozycji powoduje koniec rotacji.
	 */
	private static final float PIVOT_PROXIMITY_RADIUS = 200f;

	// MOVE
	private PointF mmInitialHandlerPosition = new PointF();
	private PointF mmLastHandlerPosition = new PointF();
	private long mmInitialTimestamp = 0;
	// private float mmHandlerSpeed = 0;
	private int mmLeadingPointerId = 0;

	/**
	 * Zbyt mala predkosc ruchu powoduje, ze nie da sie rozpoznac gestu.
	 */
	private static final float MINIMAL_MOVEMENT_SPEED = 0.05f;

	/**
	 * Zbyt mala odleglosc miedzy "nogami" powoduje, ze nie da sie rozpoznac
	 * gestu.
	 */
	private static final float MINIMAL_LEG_GAP = 20.0f;

	/**
	 * Nie da sie stawiac zbyt dlugich krokow.
	 */
	private static final float MAXIMAL_STEP_LENGTH = 100.0f;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// setContentView(R.layout.activity_main);

		mGLSurfaceView = new GLSurfaceView(this);

		// Check if the system supports OpenGL ES 2.0.
		final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		final ConfigurationInfo configurationInfo = activityManager
				.getDeviceConfigurationInfo();
		final boolean supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000;

		if (supportsEs2) {
			// Request an OpenGL ES 2.0 compatible context.
			mGLSurfaceView.setEGLContextClientVersion(2);

			// Set the renderer to our demo renderer, defined below.
			mGLSurfaceView.setRenderer(mRenderer);
		} else {
			// This is where you could create an OpenGL ES 1.x compatible
			// renderer if you wanted to support both ES 1 and ES 2.
			return;
		}

		setContentView(mGLSurfaceView);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return detectJump(event) || detectRotation(event)
				|| detectMovement(event);
	}

	private boolean detectMovement(MotionEvent event) {
		int action = MotionEventCompat.getActionMasked(event);
		int pointersCount = MotionEventCompat.getPointerCount(event);
		int pointerIndex = MotionEventCompat.findPointerIndex(event,
				mmLeadingPointerId);

		// "Repair" leading pointer ID (sometimes events are lost).
		if (pointerIndex == -1) {
			mmLeadingPointerId = 0;
			pointerIndex = MotionEventCompat.findPointerIndex(event,
					mmLeadingPointerId);
		}

		float x = MotionEventCompat.getX(event, pointerIndex);
		float y = MotionEventCompat.getY(event, pointerIndex);

		if (action == MotionEvent.ACTION_DOWN) {
			// Prevent player from walking quickly using one finger only.
			if (Math.abs(mmInitialHandlerPosition.x - x) < MINIMAL_LEG_GAP) {
				return false;
			}

			mmInitialHandlerPosition.set(x, y);
			mmLastHandlerPosition.set(mmInitialHandlerPosition);
			mmInitialTimestamp = SystemClock.uptimeMillis();
			mmLeadingPointerId = MotionEventCompat.getPointerId(event, 0);
		} else {
			// Czasem gubione sa zdarzenia UP - wykryj taka sytuacje i popraw.
			if (Math.abs(x - mmLastHandlerPosition.x) > MINIMAL_LEG_GAP) {
				mmInitialHandlerPosition.set(x, y);
				mmLastHandlerPosition.set(x, y);
				mmInitialTimestamp = SystemClock.uptimeMillis();
			}
		}

		if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_UP) {
			int id = event.getPointerId(event.getActionIndex());

			// Leading pointer was released, the second one is now the leader.
			if (id == mmLeadingPointerId) {
				// FIXME: For the time being assume only two pointers might be
				// active simultanously.
				mmLeadingPointerId = (id == 0) ? 1 : 0;

				pointerIndex = MotionEventCompat.findPointerIndex(event,
						mmLeadingPointerId);
				x = MotionEventCompat.getX(event, pointerIndex);
				y = MotionEventCompat.getY(event, pointerIndex);

				mmInitialHandlerPosition.set(x, y);
				mmLastHandlerPosition.set(x, y);
				mmInitialTimestamp = SystemClock.uptimeMillis();
			}
		}

		if (action == MotionEvent.ACTION_UP) {
			// It was the last pointer, so during the next event ID=0.
			mmLeadingPointerId = 0;
			mmInitialHandlerPosition.set(x, y);
		}

		float dist = y - mmInitialHandlerPosition.y;
		long time = SystemClock.uptimeMillis() - mmInitialTimestamp;
		float speed = dist / (float) time;

		// Recognize the second finger as a "member" of the movement gesture
		// only if the leading finger moves fast enough.
		if (pointersCount > 1 && speed > 0 && speed < MINIMAL_MOVEMENT_SPEED) {
			return false;
		}

		if (Math.abs(mmInitialHandlerPosition.y - y) > MAXIMAL_STEP_LENGTH) {
			return false;
		}

		if (action == MotionEvent.ACTION_MOVE) {
			if (speed > MINIMAL_MOVEMENT_SPEED) {
				mRenderer.changePosition(dist / 200);
			}

			mmLastHandlerPosition.set(x, y);
		}

		return false;
	}

	/**
	 * The rotation gesture means that one finger constantly presses the screen
	 * in a certain point (it is a pivot) and a second one scrolls (up- or
	 * downwards).
	 * 
	 * It is important to distinguish two cases:<br>
	 * <ul>
	 * a) the right finger is a pivot (then scroll upwards = rotate CCW)<br>
	 * b) the left finger is a pivot (then scroll upwards = rotate CW)
	 * </ul>
	 * 
	 * @param event
	 *            motion to be processed
	 * @return <code>true</code> if the gesture was recognized, otherwise
	 *         <code>false</code>
	 */
	private boolean detectRotation(MotionEvent event) {
		int action = MotionEventCompat.getActionMasked(event);
		int pointersCount = MotionEventCompat.getPointerCount(event);

		float x0 = MotionEventCompat.getX(event, 0);
		float y0 = MotionEventCompat.getY(event, 0);

		float x1 = pointersCount > 1 ? MotionEventCompat.getX(event, 1) : 0;
		float y1 = pointersCount > 1 ? MotionEventCompat.getY(event, 1) : 0;

		if (action == MotionEvent.ACTION_DOWN) {
			mrPivotPosition.set(x0, y0);
		}

		if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN
				&& event.getPointerCount() == 2) {
			mrHandlerPosition.set(x1, y1);
		}
		if (action == MotionEvent.ACTION_MOVE && pointersCount == 2) {
			float dist = (mrPivotPosition.x - x0) * (mrPivotPosition.x - x0)
					+ (mrPivotPosition.y - y0) * (mrPivotPosition.y - y0);

			if (dist > PIVOT_PROXIMITY_RADIUS)
				return false;

			float angleDelta = mrHandlerPosition.y - y1;

			// If the pivot point is to the right we rotate CW by scrolling up.
			if (mrPivotPosition.x > mrHandlerPosition.x)
				angleDelta *= -1;

			mRenderer.changeAngle(angleDelta);
			mrHandlerPosition.set(x1, y1);

			return true;
		}

		return false;
	}

	/*
	 * Moze warto logowac zdarzenie nacisniecia i puszczenia wskaznika wraz z
	 * czasem wystapienia (dla np. ostatnich trzech wystapien)?
	 */

	/**
	 * Tap with two fingers.
	 * 
	 * 1) No finger on the screen for [..]ms<br>
	 * 2) Press with two fingers (max. delay
	 * 
	 * @param event
	 * @return
	 */
	private boolean detectJump(MotionEvent event) {
		int action = MotionEventCompat.getActionMasked(event);
		int pointersCount = MotionEventCompat.getPointerCount(event);

		if (action == MotionEvent.ACTION_UP) {
			if (Math.abs(mjFirstPressed - mjLastReleased) > JUMP_RELEASE_TIME
					&& Math.abs(mjSecondPressed - mjFirstPressed) < JUMP_TAP_TIME
					&& pointersCount < 2) {
				Toast.makeText(MainActivity.this, "Jump!", 0).show();
				mjLastReleased = 0;
				mjFirstPressed = 0;
				mjSecondPressed = 0;
				return true;
			}
		}

		if (action == MotionEvent.ACTION_DOWN) {
			mjFirstPressed = SystemClock.uptimeMillis();
		}

		if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN
				&& pointersCount == 2) {
			mjSecondPressed = SystemClock.uptimeMillis();
		}

		return false;
	}

	@Override
	protected void onPause() {
		super.onPause();
		mGLSurfaceView.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mGLSurfaceView.onResume();
	}

}
