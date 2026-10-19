package gg.xp.xivsupport.gui.i18n;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class UiTranslatorTest {

	@Test
	public void translatesKnownStrings() {
		// 設定ファイルの無いテスト環境では日本語版の既定として日本語になる
		Assert.assertEquals(UiTranslator.translate("Plugin Settings"), "プラグイン設定");
		Assert.assertEquals(UiTranslator.translate("Events"), "イベント");
		// 後ろに値が続くラベルは、元の空白を残したまま置き換える
		Assert.assertEquals(UiTranslator.translate("Error: "), "エラー: ");
		Assert.assertEquals(UiTranslator.translate("  Events"), "  イベント");
		// 一部だけ一致する文字列は置き換えない
		Assert.assertEquals(UiTranslator.translate("Events of the day"), "Events of the day");
	}

	@Test
	public void leavesUnknownStringsAsIs() {
		Assert.assertEquals(UiTranslator.translate("Some Unknown Label"), "Some Unknown Label");
		Assert.assertNull(UiTranslator.translate(null));
	}

	@Test
	public void translatedTextIsNotTranslatedAgain() {
		// 翻訳後の文字列が再び辞書に引っかかると、表示の変更が繰り返されてしまう
		String once = UiTranslator.translate("Settings");
		Assert.assertEquals(UiTranslator.translate(once), once);
	}

	@Test
	public void unescapesNewlines() {
		Assert.assertEquals(UiTranslator.unescape("a\\nb\\tc"), "a\nb\tc");
	}

	@Test
	public void loadsEachLanguage() {
		Assert.assertEquals(UiTranslator.load("ko").get("Plugin Settings"), "플러그인 설정");
		Assert.assertEquals(UiTranslator.load("zh").get("Plugin Settings"), "插件设置");
		Assert.assertTrue(UiTranslator.load("en").isEmpty());
	}

	@Test
	public void normalizesLanguageCodes() {
		Assert.assertEquals(HubSettings.normalize("JA"), "ja");
		Assert.assertEquals(HubSettings.normalize("zh-Hans"), "zh");
		Assert.assertEquals(HubSettings.normalize(" ko "), "ko");
		Assert.assertNull(HubSettings.normalize("fr"));
		Assert.assertNull(HubSettings.normalize(null));
	}

	@DataProvider
	public Object[][] languages() {
		return new Object[][]{{"ja"}, {"ko"}, {"zh"}};
	}

	@Test(dataProvider = "languages")
	public void dictionaryHasNoDuplicateOrMalformedKeys(String language) throws IOException {
		Set<String> keys = readKeys(language);
		Map<String, String> loaded = UiTranslator.load(language);
		// 日本語は自動生成の辞書（コンテンツ名・技名）も合わせて読むので、手で書いた分より多くなる
		if ("ja".equals(language)) {
			Assert.assertTrue(loaded.size() > keys.size(), language);
		}
		else {
			Assert.assertEquals(loaded.size(), keys.size());
		}
	}

	@Test
	public void loadsGeneratedCalloutDictionaryForJapanese() {
		Map<String, String> ja = UiTranslator.load("ja");
		Assert.assertEquals(ja.get("Icicle Impact"), "アイシクルインパクト");
		Assert.assertNull(UiTranslator.load("ko").get("Icicle Impact"));
	}

	@Test
	public void allLanguagesCoverTheSameStrings() throws IOException {
		// 言語によって英語のまま残る箇所が出ないよう、日本語辞書と同じ項目をそろえる
		Set<String> ja = readKeys("ja");
		Assert.assertEquals(readKeys("ko"), ja);
		Assert.assertEquals(readKeys("zh"), ja);
	}

	private static Set<String> readKeys(String language) throws IOException {
		Set<String> keys = new LinkedHashSet<>();
		try (InputStream stream = UiTranslator.class.getResourceAsStream("/i18n/" + language + ".tsv")) {
			Assert.assertNotNull(stream, language);
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split("\t", -1);
				Assert.assertEquals(parts.length, 2, language + " line " + lineNumber + " must have exactly one tab: " + line);
				Assert.assertFalse(parts[1].isBlank(), language + " line " + lineNumber + " has no translation");
				// 前後の空白は無視して引くため、空白だけが違うキーも重複とみなす
				Assert.assertTrue(keys.add(parts[0].strip()), language + " duplicate key at line " + lineNumber + ": " + parts[0]);
			}
		}
		return keys;
	}
}
