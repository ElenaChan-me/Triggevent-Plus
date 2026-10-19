package gg.xp.xivsupport.speech.voicepack;

import com.sun.net.httpserver.HttpServer;
import gg.xp.xivsupport.persistence.InMemoryMapPersistenceProvider;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class NameVoiceRequesterTest {

	@Test
	public void onlyLocalEndpointsAreAccepted() {
		Assert.assertEquals(NameVoiceRequester.endpoint("http://127.0.0.1:8765").toString(), "http://127.0.0.1:8765/api/names");
		Assert.assertEquals(NameVoiceRequester.endpoint("http://localhost:9000/").toString(), "http://localhost:9000/api/names");
		// 名前を外へ送らないよう、この PC 以外は断る
		Assert.assertThrows(IllegalArgumentException.class, () -> NameVoiceRequester.endpoint("http://example.com:8765"));
		Assert.assertThrows(IllegalArgumentException.class, () -> NameVoiceRequester.endpoint("https://127.0.0.1:8765"));
		Assert.assertThrows(IllegalArgumentException.class, () -> NameVoiceRequester.endpoint("not a url"));
	}

	@Test
	public void requestsOnlyMissingValidNamesOnce() throws Exception {
		Path dir = VoicePackTest.createPack();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		List<String> bodies = new CopyOnWriteArrayList<>();
		server.createContext("/api/names", ex -> {
			bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] res = "{}".getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, res.length);
			try (OutputStream out = ex.getResponseBody()) {
				out.write(res);
			}
		});
		server.start();
		try {
			InMemoryMapPersistenceProvider pers = new InMemoryMapPersistenceProvider();
			VoicePackPlayer player = new VoicePackPlayer(pers);
			player.getFolder().set(dir.toString());
			player.getEnabled().set(true);
			NameVoiceRequester requester = new NameVoiceRequester(pers, player);
			requester.getUrl().set("http://127.0.0.1:" + server.getAddress().getPort());

			// 既定では送らない
			requester.request(List.of("Lily Tayler")).get(5, TimeUnit.SECONDS);
			Assert.assertTrue(bodies.isEmpty());

			requester.getEnabled().set(true);
			requester.request(List.of("Lily Tayler", "Lily Tayler", "../evil", "Shiori Kamiya")).get(5, TimeUnit.SECONDS);
			Assert.assertEquals(bodies.size(), 1);
			Assert.assertTrue(bodies.get(0).contains("\"pack\":\"test\""), bodies.get(0));
			Assert.assertTrue(bodies.get(0).contains("Lily Tayler") && bodies.get(0).contains("Shiori Kamiya"), bodies.get(0));
			Assert.assertFalse(bodies.get(0).contains("evil"), bodies.get(0));

			// 一度頼んだ名前は頼み直さない
			requester.request(List.of("Lily Tayler")).get(5, TimeUnit.SECONDS);
			Assert.assertEquals(bodies.size(), 1);
		}
		finally {
			server.stop(0);
			VoicePackTest.delete(dir);
		}
	}

	@Test
	public void failedRequestsAreRetried() throws Exception {
		Path dir = VoicePackTest.createPack();
		try {
			InMemoryMapPersistenceProvider pers = new InMemoryMapPersistenceProvider();
			VoicePackPlayer player = new VoicePackPlayer(pers);
			player.getFolder().set(dir.toString());
			player.getEnabled().set(true);
			NameVoiceRequester requester = new NameVoiceRequester(pers, player);
			requester.getEnabled().set(true);
			// 何も待ち受けていないポート
			requester.getUrl().set("http://127.0.0.1:1");
			requester.request(List.of("Lily Tayler")).get(10, TimeUnit.SECONDS);
			Assert.assertNotNull(requester.getLastError());
			requester.getUrl().set("http://example.com");
			requester.request(List.of("Lily Tayler")).get(5, TimeUnit.SECONDS);
			Assert.assertTrue(requester.getLastError().contains("this PC"), requester.getLastError());
		}
		finally {
			VoicePackTest.delete(dir);
		}
	}
}
