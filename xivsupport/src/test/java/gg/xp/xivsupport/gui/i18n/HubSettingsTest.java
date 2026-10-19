package gg.xp.xivsupport.gui.i18n;

import org.testng.Assert;
import org.testng.annotations.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HubSettingsTest {

	@Test
	public void keepsOnlyExistingVoicePacks() throws IOException {
		Path dir = Files.createTempDirectory("hubpack");
		Files.writeString(dir.resolve("manifest.json"), "{}");
		try {
			ObjectMapper mapper = new ObjectMapper();
			String json = "[" + mapper.writeValueAsString(dir.toString()) + "," + mapper.writeValueAsString(dir.toString())
					+ ",\"C:/no/such/pack\",5,null]";
			// アンインストール済みのパックや重複は一覧に出さない
			Assert.assertEquals(HubSettings.voicePacks(mapper.readTree(json)), List.of(dir.toAbsolutePath().normalize()));
			Assert.assertEquals(HubSettings.voicePacks(null), List.of());
			Assert.assertEquals(HubSettings.voicePacks(mapper.readTree("\"x\"")), List.of());
		}
		finally {
			Files.delete(dir.resolve("manifest.json"));
			Files.delete(dir);
		}
	}
}
