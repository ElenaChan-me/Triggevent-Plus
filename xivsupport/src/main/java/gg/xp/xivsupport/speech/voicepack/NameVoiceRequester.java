package gg.xp.xivsupport.speech.voicepack;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.reevent.scan.LiveOnly;
import gg.xp.xivsupport.events.state.PartyChangeEvent;
import gg.xp.xivsupport.events.state.RawXivPartyInfo;
import gg.xp.xivsupport.persistence.PersistenceProvider;
import gg.xp.xivsupport.persistence.settings.BooleanSetting;
import gg.xp.xivsupport.persistence.settings.StringSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * パーティメンバーの名前の音声を、Triggevent-VoiceGen に頼んで作ってもらう。
 * <p>
 * 名前は台本に無いので、パーティが変わったときに音声の無い名前だけを VoiceGen のサーバーへ送る。
 * 音声ができるまでは、名前の入る読み上げは今までどおり ACT の TTS で読む。
 * 名前を外へ送らないよう、送り先はこの PC（127.0.0.1 / localhost）に限る。
 */
public class NameVoiceRequester {

	private static final Logger log = LoggerFactory.getLogger(NameVoiceRequester.class);
	private static final String DEFAULT_URL = "http://127.0.0.1:8765";
	private static final Set<String> LOCAL_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]", "::1");
	// VoiceGen 側と同じ。FF14 のキャラクター名として正しいものだけを送る
	private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z'\\-]{0,19}(?: [A-Za-z][A-Za-z'\\-]{0,19})?$");
	// 声の変換に時間がかかるので長めに待つ
	private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
	// VoiceGen を起動していないときに、パーティが変わるたびに頼み直してログを埋めないよう、失敗したらしばらく待つ
	static final Duration RETRY_DELAY = Duration.ofMinutes(5);

	private final VoicePackPlayer player;
	private final BooleanSetting enabled;
	private final StringSetting url;
	private final ObjectMapper mapper = new ObjectMapper();
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	// 同じ名前を何度も頼まないよう、頼んだ名前を覚えておく（失敗したものは外して次の機会に頼み直す）
	private final Set<String> requested = ConcurrentHashMap.newKeySet();
	private volatile String lastError;
	private final Clock clock;
	private volatile Instant retryAfter = Instant.MIN;

	public NameVoiceRequester(PersistenceProvider pers, VoicePackPlayer player) {
		this(pers, player, Clock.systemUTC());
	}

	NameVoiceRequester(PersistenceProvider pers, VoicePackPlayer player, Clock clock) {
		this.player = player;
		this.clock = clock;
		enabled = new BooleanSetting(pers, "voice-pack.name-generation", false);
		url = new StringSetting(pers, "voice-pack.voicegen-url", DEFAULT_URL);
		// 設定を直したり有効にし直したりしたら、待たずにすぐ頼めるようにする
		url.addListener(() -> retryAfter = Instant.MIN);
		enabled.addListener(() -> retryAfter = Instant.MIN);
	}

	@LiveOnly
	@HandleEvents
	public void partyChange(EventContext context, PartyChangeEvent event) {
		request(event.getMembers().stream().map(RawXivPartyInfo::getName).toList());
	}

	/**
	 * 音声の無い名前を VoiceGen に頼む。結果は待たない。
	 */
	public CompletableFuture<Void> request(Collection<String> names) {
		VoicePack pack = player.getPack();
		if (!enabled.get() || !player.getEnabled().get() || pack == null || clock.instant().isBefore(retryAfter)) {
			return CompletableFuture.completedFuture(null);
		}
		List<String> missing = names.stream()
				.filter(n -> n != null && NAME_PATTERN.matcher(n.strip()).matches())
				.map(String::strip)
				.distinct()
				.filter(n -> !pack.hasNameClip(n))
				.filter(requested::add)
				.toList();
		if (missing.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}
		URI endpoint;
		try {
			endpoint = endpoint(url.get());
		}
		catch (IllegalArgumentException e) {
			lastError = e.getMessage();
			missing.forEach(requested::remove);
			return CompletableFuture.completedFuture(null);
		}
		String body;
		try {
			body = mapper.writeValueAsString(Map.of("pack", pack.getName(), "directory", pack.getDirectory().toString(), "names", missing));
		}
		catch (JacksonException e) {
			missing.forEach(requested::remove);
			return CompletableFuture.failedFuture(e);
		}
		HttpRequest req = HttpRequest.newBuilder(endpoint)
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		log.info("Requesting {} name clip(s) from VoiceGen", missing.size());
		return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
				.thenAccept(res -> {
					if (res.statusCode() == 200) {
						lastError = null;
						log.info("VoiceGen generated name clips: {}", res.body());
					}
					else {
						lastError = "VoiceGen: HTTP " + res.statusCode() + " " + res.body();
						log.warn("VoiceGen rejected name request: {} {}", res.statusCode(), res.body());
						failed(missing);
					}
				})
				.exceptionally(e -> {
					// VoiceGen を起動していないことが多いので、警告だけにして読み上げは TTS のまま続ける
					lastError = "VoiceGen: " + e.getMessage();
					log.warn("Could not reach VoiceGen at {}: {} (retrying in {} minutes)", endpoint, e.toString(), RETRY_DELAY.toMinutes());
					failed(missing);
					return null;
				});
	}

	private void failed(List<String> names) {
		names.forEach(requested::remove);
		retryAfter = clock.instant().plus(RETRY_DELAY);
	}

	/**
	 * 設定された URL から送り先を作る。この PC 以外の宛先は受け付けない。
	 */
	static URI endpoint(String base) {
		URI uri;
		try {
			uri = new URI(base == null ? "" : base.strip());
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid VoiceGen URL: " + base, e);
		}
		if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || !LOCAL_HOSTS.contains(uri.getHost().toLowerCase())) {
			throw new IllegalArgumentException("VoiceGen URL must point to this PC (http://127.0.0.1:port): " + base);
		}
		return uri.resolve("/api/names");
	}

	public BooleanSetting getEnabled() {
		return enabled;
	}

	public StringSetting getUrl() {
		return url;
	}

	public String getLastError() {
		return lastError;
	}
}
