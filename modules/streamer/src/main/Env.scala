package lila.streamer

import akka.actor._
import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import play.api.Configuration
import scala.concurrent.duration._

import lila.common.config._
import lila.common.Strings

@Module
private class NotifyConfig(
    @ConfigName("collection.streamer") val streamerColl: CollName,
    @ConfigName("collection.image") val imageColl: CollName,
    @ConfigName("paginator.max_per_page") val paginatorMaxPerPage: MaxPerPage,
    @ConfigName("streaming.keyword") val keyword: Stream.Keyword,
    @ConfigName("streaming.google.api_key") val googleApiKey: Secret,
    @ConfigName("streaming.twitch.client_id") val twitchClientId: Secret
)

final class Env(
    appConfig: Configuration,
    ws: play.api.libs.ws.WSClient,
    settingStore: lila.memo.SettingStore.Builder,
    renderer: ActorSelection,
    isOnline: lila.user.User.ID => Boolean,
    asyncCache: lila.memo.AsyncCache.Builder,
    notifyApi: lila.notify.NotifyApi,
    lightUserApi: lila.user.LightUserApi,
    userRepo: lila.user.UserRepo,
    hub: lila.hub.Env,
    db: lila.db.Env
)(implicit system: ActorSystem) {

  private implicit val keywordLoader = strLoader(Stream.Keyword.apply)
  private val config = appConfig.get[NotifyConfig]("notify")(AutoConfig.loader)

  private lazy val streamerColl = db(config.streamerColl)

  private lazy val photographer = new lila.db.Photographer(db(config.imageColl), "streamer")

  lazy val alwaysFeaturedSetting = {
    import lila.memo.SettingStore.Strings._
    settingStore[Strings](
      "streamerAlwaysFeatured",
      default = Strings(Nil),
      text = "Twitch streamers who get featured without the keyword - lichess usernames separated by a comma".some
    )
  }

  lazy val api: StreamerApi = wire[StreamerApi]

  lazy val pager = wire[StreamerPager]

  private val streamingActor = system.actorOf(Props(new Streaming(
    ws = ws,
    renderer = renderer,
    api = api,
    isOnline = isOnline,
    timeline = hub.timeline,
    keyword = config.keyword,
    alwaysFeatured = alwaysFeaturedSetting.get,
    googleApiKey = config.googleApiKey,
    twitchClientId = config.twitchClientId,
    lightUserApi = lightUserApi
  )))

  lazy val liveStreamApi = wire[LiveStreamApi]

  lila.common.Bus.subscribeFun("adjustCheater") {
    case lila.hub.actorApi.mod.MarkCheater(userId, true) => api demote userId
  }

  system.scheduler.scheduleWithFixedDelay(1 hour, 1 day) {
    () => api.autoDemoteFakes
  }
}
