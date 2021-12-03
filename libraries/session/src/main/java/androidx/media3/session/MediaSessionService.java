/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession.ControllerInfo;
import java.util.List;

/**
 * Superclass to be extended by services hosting {@link MediaSession media sessions}.
 *
 * <p>It's highly recommended for an app to use this class if they want to keep media playback in
 * the background. The service allows other apps to know that your app supports {@link MediaSession}
 * even when your app isn't running. For example, user's voice command may start your app to play
 * media.
 *
 * <p>To extend this class, declare the intent filter in your {@code AndroidManifest.xml}.
 *
 * <pre>{@code
 * <service android:name="NameOfYourService">
 *   <intent-filter>
 *     <action android:name="androidx.media3.session.MediaSessionService"/>
 *   </intent-filter>
 * </service>
 * }</pre>
 *
 * <p>You may also declare {@code android.media.browse.MediaBrowserService} for compatibility with
 * {@link android.support.v4.media.MediaBrowserCompat}. This service can handle the case
 * automatically.
 *
 * <p>It's recommended for an app to have a single service declared in the manifest. Otherwise, your
 * app might be shown twice in the list of the controller apps, or another app might fail to pick
 * the right service when it wants to start a playback on this app. If you want to provide multiple
 * sessions, take a look at <a href="#MultipleSessions">Supporting Multiple Sessions</a>.
 *
 * <p>Topics covered here:
 *
 * <ol>
 *   <li><a href="#ServiceLifecycle">Service Lifecycle</a>
 *   <li><a href="#MultipleSessions">Supporting Multiple Sessions</a>
 * </ol>
 *
 * <h2 id="ServiceLifecycle">Service Lifecycle</h2>
 *
 * <p>A media session service is a bound service. When a {@link MediaController} is created for the
 * service, the controller binds to the service. {@link #onGetSession(ControllerInfo)} will be
 * called inside of the {@link #onBind(Intent)}.
 *
 * <p>After the binding, the session's {@link MediaSession.SessionCallback#onConnect(MediaSession,
 * MediaSession.ControllerInfo)} will be called to accept or reject the connection request from the
 * controller. If it's accepted, the controller will be available and keep the binding. If it's
 * rejected, the controller will unbind.
 *
 * <p>When a playback is started on the service, {@link #onUpdateNotification(MediaSession)} is
 * called and the service will become a <a
 * href="https://developer.android.com/guide/components/foreground-services">foreground service</a>.
 * It's required to keep the playback after the controller is destroyed. The service will become a
 * background service when all playbacks are stopped. Apps targeting {@code SDK_INT >= 28} must
 * request the permission, {@link android.Manifest.permission#FOREGROUND_SERVICE}, in order to make
 * the service foreground.
 *
 * <p>The service will be destroyed when all sessions are closed, or no controller is binding to the
 * service while the service is in the background.
 *
 * <h2 id="MultipleSessions">Supporting Multiple Sessions</h2>
 *
 * <p>Generally, multiple sessions aren't necessary for most media apps. One exception is if your
 * app can play multiple media content at the same time, but only for the playback of video-only
 * media or remote playback, since the <a
 * href="https://developer.android.com/guide/topics/media-apps/audio-focus">audio focus policy</a>
 * recommends not playing multiple audio content at the same time. Also, keep in mind that multiple
 * media sessions would make Android Auto and Bluetooth device with display to show your apps
 * multiple times, because they list up media sessions, not media apps.
 *
 * <p>However, if you're capable of handling multiple playbacks and want to keep their sessions
 * while the app is in the background, create multiple sessions and add them to this service with
 * {@link #addSession(MediaSession)}.
 *
 * <p>Note that {@link MediaController} can be created with {@link SessionToken} to connect to a
 * session in this service. In that case, {@link #onGetSession(ControllerInfo)} will be called to
 * decide which session to handle the connection request. Pick the best session among the added
 * sessions, or create a new session and return it from {@link #onGetSession(ControllerInfo)}.
 */
public abstract class MediaSessionService extends Service {

  /** The action for {@link Intent} filter that must be declared by the service. */
  public static final String SERVICE_INTERFACE = "androidx.media3.session.MediaSessionService";

  private final MediaSessionServiceImpl impl;

  /** Creates a service. */
  @SuppressWarnings("nullness:method.invocation") // createImpl() under initialization
  public MediaSessionService() {
    super();
    // Note: This service doesn't have valid context at this moment.
    impl = createImpl();
  }

  /* package */ MediaSessionServiceImpl createImpl() {
    return new androidx.media3.session.MediaSessionServiceImpl();
  }

  /**
   * Called when the service is created.
   *
   * <p>Override this method if you need your own initialization.
   */
  @CallSuper
  @Override
  public void onCreate() {
    super.onCreate();
    impl.onCreate(this);
  }

  /**
   * Called when a {@link MediaController} is created with this service's {@link SessionToken}.
   * Return a {@link MediaSession} that the controller will connect to, or {@code null} to reject
   * the connection request.
   *
   * <p>The service automatically maintains the returned sessions. In other words, a session
   * returned by this method will be added to the service, and removed from the service when the
   * session is closed. You don't need to manually call {@link #addSession(MediaSession)} nor {@link
   * #removeSession(MediaSession)}.
   *
   * <p>There are two special cases where the {@link ControllerInfo#getPackageName()} returns
   * non-existent package name:
   *
   * <ul>
   *   <li>When the service is started by a media button event, the package name will be {@link
   *       Intent#ACTION_MEDIA_BUTTON}. If you want to allow the service to be started by media
   *       button events, do not return {@code null}.
   *   <li>When a legacy {@link android.media.browse.MediaBrowser} or a {@link
   *       android.support.v4.media.MediaBrowserCompat} tries to connect, the package name will be
   *       {@link MediaBrowserServiceCompat#SERVICE_INTERFACE}. If you want to allow the service to
   *       be bound by the legacy media browsers, do not return {@code null}.
   * </ul>
   *
   * <p>For those special cases, the values returned by {@link ControllerInfo#getUid()} and {@link
   * ControllerInfo#getConnectionHints()} have no meaning.
   *
   * <p>This method is always called on the main thread.
   *
   * @param controllerInfo The information of the controller that is trying to connect.
   * @return A {@link MediaSession} for the controller, or {@code null} to reject the connection.
   * @see MediaSession.Builder
   * @see #getSessions()
   */
  @Nullable
  public abstract MediaSession onGetSession(ControllerInfo controllerInfo);

  /**
   * Adds a {@link MediaSession} to this service. This is not necessary for most media apps. See <a
   * href="#MultipleSessions">Supporting Multiple Sessions</a> for details.
   *
   * <p>Added session will be removed automatically when it's closed.
   *
   * @param session A session to be added.
   * @see #removeSession(MediaSession)
   * @see #getSessions()
   */
  public final void addSession(MediaSession session) {
    checkNotNull(session, "session must not be null");
    checkArgument(!session.isReleased(), "session is already released");
    impl.addSession(session);
  }

  /**
   * Removes a {@link MediaSession} from this service. This is not necessary for most media apps.
   * See <a href="#MultipleSessions">Supporting Multiple Sessions</a> for details.
   *
   * @param session A session to be removed.
   * @see #addSession(MediaSession)
   * @see #getSessions()
   */
  public final void removeSession(MediaSession session) {
    checkNotNull(session, "session must not be null");
    impl.removeSession(session);
  }

  /**
   * Called when {@link MediaNotification} needs to be updated. Override this method to show or
   * cancel your own notification.
   *
   * <p>This will be called on the application thread of the underlying {@link Player} of {@link
   * MediaSession}.
   *
   * <p>With the notification returned by this method, the service becomes a <a
   * href="https://developer.android.com/guide/components/foreground-services">foreground
   * service</a> when the playback is started. Apps targeting {@code SDK_INT >= 28} must request the
   * permission, {@link android.Manifest.permission#FOREGROUND_SERVICE}. It becomes a background
   * service after the playback is stopped.
   *
   * @param session A session that needs notification update.
   * @return A {@link MediaNotification}, or {@code null} if you don't want the automatic
   *     foreground/background transitions.
   */
  @Nullable
  public MediaNotification onUpdateNotification(MediaSession session) {
    checkNotNull(session, "session must not be null");
    return impl.onUpdateNotification(session);
  }

  /**
   * Returns the list of {@link MediaSession sessions} that you've added to this service via {@link
   * #addSession} or {@link #onGetSession(ControllerInfo)}.
   */
  public final List<MediaSession> getSessions() {
    return impl.getSessions();
  }

  /**
   * Called when a component is about to bind to the service.
   *
   * <p>The default implementation handles the incoming requests from {@link MediaController
   * controllers}. In this case, the intent will have the action {@link #SERVICE_INTERFACE}.
   * Override this method if this service also needs to handle actions other than {@link
   * #SERVICE_INTERFACE}.
   */
  @CallSuper
  @Override
  @Nullable
  public IBinder onBind(@Nullable Intent intent) {
    return impl.onBind(intent);
  }

  /**
   * Called when a component calls {@link android.content.Context#startService(Intent)}.
   *
   * <p>The default implementation handles the incoming media button events. In this case, the
   * intent will have the action {@link Intent#ACTION_MEDIA_BUTTON}. Override this method if this
   * service also needs to handle actions other than {@link Intent#ACTION_MEDIA_BUTTON}.
   */
  @CallSuper
  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    return impl.onStartCommand(intent, flags, startId);
  }

  /**
   * Called when the service is no longer used and is being removed.
   *
   * <p>Override this method if you need your own clean up.
   */
  @CallSuper
  @Override
  public void onDestroy() {
    super.onDestroy();
    impl.onDestroy();
  }

  /** A notification for media playback returned by {@link #onUpdateNotification(MediaSession)}. */
  public static final class MediaNotification {

    /** The notification id. */
    @IntRange(from = 1)
    public final int notificationId;

    /** The {@link Notification}. */
    public final Notification notification;

    /**
     * Creates an instance.
     *
     * @param notificationId The notification id to be used for {@link
     *     NotificationManager#notify(int, Notification)}.
     * @param notification A {@link Notification} to make the {@link MediaSessionService} <a
     *     href="https://developer.android.com/guide/components/foreground-services">foreground</a>.
     *     It's highly recommended to use a {@link androidx.media.app.NotificationCompat.MediaStyle
     *     media style} {@link Notification notification}.
     */
    public MediaNotification(@IntRange(from = 1) int notificationId, Notification notification) {
      this.notificationId = notificationId;
      this.notification = checkNotNull(notification);
    }
  }
}
