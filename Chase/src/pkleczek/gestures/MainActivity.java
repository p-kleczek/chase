package pkleczek.gestures;

import java.util.ArrayList;
import java.util.List;

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

	private static final int NIL = -1;
	private int activeCounter = 0;

	private GLSurfaceView mGLSurfaceView;
	private Renderer mRenderer = new Renderer();

	private int mActivePointerId;
	private int mSecondaryPointerId;

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
	private boolean mmPressedCorrectly = false;
	private List<PointF> mmInitialPositions = new ArrayList<PointF>(2);
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
//		return detectMovement(event);
		// return testMovement(event);
		 return detectJump(event) || detectRotation(event)
		 || detectMovement(event);
	}

	private boolean testMovement(MotionEvent event) {
		int action = MotionEventCompat.getActionMasked(event);
		int pointersCount = MotionEventCompat.getPointerCount(event);
		int pointerIndex = MotionEventCompat.findPointerIndex(event,
				mmLeadingPointerId);

		// System.out.println("id=" + mmLeadingPointerId + "  inx=" +
		// pointerIndex);

		float x = MotionEventCompat.getX(event, pointerIndex);
		float y = MotionEventCompat.getY(event, pointerIndex);

		System.out.println("------------------   " + activeCounter);

		if (action == MotionEvent.ACTION_DOWN) {
			// String msg = String.format("[move down] x=%f]", x);
			// System.out.println(msg);

			if (Math.abs(mmInitialHandlerPosition.x - x) < MINIMAL_LEG_GAP) {
				String msg = String.format(
						"[minimal gap] init=%f   cur=%f  n=%d]",
						mmInitialHandlerPosition.x, x, pointersCount);
				System.err.println(msg);
				return false;
			}
			mmInitialHandlerPosition.set(x, y);

			printPointers("DOWN", event);
			System.out.println("id="
					+ event.getPointerId(event.getActionIndex()));

			mmLeadingPointerId = event.getPointerId(event.getActionIndex());

			activeCounter++;
		}

		if (action == MotionEvent.ACTION_UP) {
			printPointers("UP", event);
			System.out.println("id="
					+ event.getPointerId(event.getActionIndex()));
			mmLeadingPointerId = 0;

			mmInitialHandlerPosition.set(x, y);

			activeCounter--;
		}

		if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN) {
			printPointers("p DOWN", event);
			System.out.println("id="
					+ event.getPointerId(event.getActionIndex()));

			activeCounter++;
		}

		if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_UP) {
			printPointers("p UP", event);

			int id = event.getPointerId(event.getActionIndex());

			System.out.println("id="
					+ event.getPointerId(event.getActionIndex()));

			if (id == mmLeadingPointerId) {
				// FIXME: brzydkie ;p
				mmLeadingPointerId = (id == 0) ? 1 : 0;

				// FIXME: do dopracowania, trzeba zapamietac poczatkowa pozycje
				// (w chwili nacisniecia)
				pointerIndex = MotionEventCompat.findPointerIndex(event,
						mmLeadingPointerId);
				x = MotionEventCompat.getX(event, pointerIndex);
				y = MotionEventCompat.getY(event, pointerIndex);

				mmInitialHandlerPosition.set(x, y);
			}

			activeCounter--;
		}

		return false;
	}

	private void printPointers(String msg, MotionEvent event) {
		System.out.print("[" + msg + "]:  ");
		for (int i = 0; i < MotionEventCompat.getPointerCount(event); i++) {
			float xp = MotionEventCompat.getX(event, i);
			float yp = MotionEventCompat.getY(event, i);

			String s = String.format("p%d=[%f, %f]   ", i, xp, yp);
			System.out.print(s);
		}
		System.out.println();
	}

	/*
	 * Jesli drugi palec dotknie ekranu w czasie ruchu pierwszego (przy
	 * odpowiedniej predkosci), to nie jest to blad.!
	 */

	private boolean detectMovement(MotionEvent event) {
		int action = MotionEventCompat.getActionMasked(event);
		int pointersCount = MotionEventCompat.getPointerCount(event);
		int pointerIndex = MotionEventCompat.findPointerIndex(event,
				mmLeadingPointerId);

		float x = MotionEventCompat.getX(event, pointerIndex);
		float y = MotionEventCompat.getY(event, pointerIndex);

		System.out
				.println(String
						.format("--- lID=%d (n=%d) m=%d : init=[%.0f, %.0f]\t cur=[%.0f, %.0f]\t last=[%.0f, %.0f]",
								mmLeadingPointerId, pointersCount,
								event.getActionMasked(),
								mmInitialHandlerPosition.x,
								mmInitialHandlerPosition.y, x, y,
								mmLastHandlerPosition.x,
								mmLastHandlerPosition.y));

		if (action == MotionEvent.ACTION_DOWN) {

			if (Math.abs(mmInitialHandlerPosition.x - x) < MINIMAL_LEG_GAP) {
				System.err.println(String.format(
						"[minimal gap] init=%.0f   cur=%.0f",
						mmInitialHandlerPosition.x, x, pointersCount));

				mmPressedCorrectly = false;
				return false;
			}

			mmInitialHandlerPosition.set(x, y);
			mmLastHandlerPosition.set(mmInitialHandlerPosition);
			mmInitialTimestamp = SystemClock.uptimeMillis();
			mmLeadingPointerId = MotionEventCompat.getPointerId(event, 0);

			System.out.println(String.format("[down] x=%.0f", x));
		} else {
			// Czasem gubione sa zdarzenia UP - wykryj taka sytuacje i popraw.
			if (Math.abs(x - mmLastHandlerPosition.x) > MINIMAL_LEG_GAP) {
				mmInitialHandlerPosition.set(x, y);
				mmLastHandlerPosition.set(x, y);
				mmInitialTimestamp = SystemClock.uptimeMillis();
			}
		}

		if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_UP) {
			System.out.println("[action pointer UP]");

			int id = event.getPointerId(event.getActionIndex());

			if (id == mmLeadingPointerId) {
				// System.out.println(String.format("up id_prev=%d",
				// mmLeadingPointerId));
				// FIXME: brzydkie ;p
				mmLeadingPointerId = (id == 0) ? 1 : 0;

				// FIXME: do dopracowania, trzeba zapamietac poczatkowa pozycje
				// (w chwili nacisniecia)
				pointerIndex = MotionEventCompat.findPointerIndex(event,
						mmLeadingPointerId);
				x = MotionEventCompat.getX(event, pointerIndex);
				y = MotionEventCompat.getY(event, pointerIndex);

				mmInitialHandlerPosition.set(x, y);
				mmLastHandlerPosition.set(x, y);
				mmInitialTimestamp = SystemClock.uptimeMillis();

				// System.out.println(String.format("up id_cur=%d",
				// mmLeadingPointerId));
			}
		}

		if (action == MotionEvent.ACTION_UP) {
			System.out.println(String.format("[up]", 0));

			// System.out.println(String.format("up id_prev=%d",
			// mmLeadingPointerId));

			mmLeadingPointerId = 0;
			mmInitialHandlerPosition.set(x, y);

			// System.out.println(String
			// .format("up id_cur=%d", mmLeadingPointerId));
		}

		float dist = y - mmInitialHandlerPosition.y;
		long time = SystemClock.uptimeMillis() - mmInitialTimestamp;
		float speed = dist / (float) time;

		// TODO: sprawdzenie szybkosci
		if (pointersCount > 1 && speed > 0 && speed < MINIMAL_MOVEMENT_SPEED) {
			String msg = String.format("s=%f", speed);
			System.err.println(msg);
			System.err.println("[E] 2 fingers");
			return false;
		}

		if (y - mmLastHandlerPosition.y < -5) {
			System.err.println(String.format("y_init=%.0f y=%.0f  y_l=%.0f", y,
					mmInitialHandlerPosition.y, mmLastHandlerPosition.y));
			// return false;
		}

		if (Math.abs(mmInitialHandlerPosition.y - y) > MAXIMAL_STEP_LENGTH) {
			System.err.println("[E] maximal step length");
			return false;
		}

		if (action == MotionEvent.ACTION_MOVE) {
			System.out.println(String.format("[move]", 0));
			if (y - mmLastHandlerPosition.y > 5) {
				// System.out.println("[E] step too small");
				// return false;
				// }

				// System.out.println(String.format("d=%f t=%d | v=%f", dist,
				// time,
				// speed));

				if (speed > MINIMAL_MOVEMENT_SPEED) {
					mRenderer.changePosition(dist / 200);
				}
			}

			mmLastHandlerPosition.set(x, y);

		}

		return false;
	}

	// TODO : rozroznienie prawa/lewa strona jako pivot
	private boolean detectRotation(MotionEvent event) {
		// this is the preferred solution
		int action = MotionEventCompat.getActionMasked(event);
		int pointersCount = MotionEventCompat.getPointerCount(event);

		float x0 = MotionEventCompat.getX(event, 0);
		float y0 = MotionEventCompat.getY(event, 0);

		float x1 = pointersCount > 1 ? MotionEventCompat.getX(event, 1) : 0;
		float y1 = pointersCount > 1 ? MotionEventCompat.getY(event, 1) : 0;

		if (action == MotionEvent.ACTION_DOWN) {
			mrPivotPosition.set(x0, y0);
			System.out.println("ROTATE: first down");
		}

		if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN
				&& event.getPointerCount() == 2) {
			mrHandlerPosition.set(x1, y1);
			System.out.println("ROTATE: second down");
		}
		if (action == MotionEvent.ACTION_MOVE && pointersCount == 2) {
			float dist = (mrPivotPosition.x - x0) * (mrPivotPosition.x - x0)
					+ (mrPivotPosition.y - y0) * (mrPivotPosition.y - y0);
			// System.out.println(String
			// .format("x0=%f y0=%f | d=%f", x0, y0, dist));

			if (dist > PIVOT_PROXIMITY_RADIUS)
				return false;

			float angleDelta = mrHandlerPosition.y - y1;
			System.out.println(angleDelta);
			mRenderer.changeAngle(angleDelta);
			mrHandlerPosition.set(x1, y1);

			System.out.println("# ROTATE!");

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

		mActivePointerId = event.getPointerId(0);
		mSecondaryPointerId = event.getPointerCount() > 1 ? event
				.getPointerId(1) : -1;

		int action = MotionEventCompat.getActionMasked(event);
		int pointersCount = MotionEventCompat.getPointerCount(event);

		// String msg = String.format("Id1=%d Id2=%d Act=%d | n=%d",
		// mActivePointerId, mSecondaryPointerId, action, pointersCount);
		// System.out.println(msg);

		// ostatni wskaznik podniesiony
		if (action == MotionEvent.ACTION_UP) {
			if (pointersCount == 1) {
				mjLastReleased = SystemClock.uptimeMillis();
				System.out.println("released");
			}

			if (Math.abs(mjFirstPressed - mjLastReleased) > JUMP_RELEASE_TIME
					&& Math.abs(mjSecondPressed - mjFirstPressed) < JUMP_TAP_TIME
					&& pointersCount < 2) {
				Toast.makeText(MainActivity.this, "Jump!", 0).show();
				System.out.println("# JUMP!");
				mjLastReleased = 0;
				mjFirstPressed = 0;
				mjSecondPressed = 0;
				return true;
			}
		}

		if (action == MotionEvent.ACTION_DOWN) {
			mjFirstPressed = SystemClock.uptimeMillis();
			System.out.println("first down");
		}

		if ((action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_DOWN
				&& pointersCount == 2) {
			mjSecondPressed = SystemClock.uptimeMillis();
			System.out.println("second down");
		}

		return false;
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		mGLSurfaceView.onPause();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		mGLSurfaceView.onResume();
	}

}
