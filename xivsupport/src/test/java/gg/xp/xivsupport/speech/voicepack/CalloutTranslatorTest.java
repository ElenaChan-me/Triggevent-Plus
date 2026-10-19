package gg.xp.xivsupport.speech.voicepack;

import gg.xp.xivsupport.persistence.InMemoryMapPersistenceProvider;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CalloutTranslatorTest {

	@Test
	public void translatesDisplayKeepingCountdown() {
		CalloutTranslator t = new CalloutTranslator(new InMemoryMapPersistenceProvider(), true);
		Assert.assertEquals(t.translateVisual("Raidwide"), "全体攻撃");
		// 残り時間の表示は訳さずに残す
		Assert.assertEquals(t.translateVisual("Raidwide (4.7)"), "全体攻撃 (4.7)");
		Assert.assertEquals(t.translateVisual("Buster on YOU"), "自分にタンク強攻撃");
		Assert.assertEquals(t.translateVisual("Buster on Lily Tayler (3.0)"), "Lily Taylerにタンク強攻撃 (3.0)");
		// 台本に無い文はそのまま
		Assert.assertEquals(t.translateVisual("My custom callout"), "My custom callout");
		Assert.assertEquals(t.translateSpeech("Spread"), "散開");
		Assert.assertNull(t.translateSpeech("My custom callout"));
	}

	@Test
	public void leavesEnglishWhenDisabled() {
		InMemoryMapPersistenceProvider pers = new InMemoryMapPersistenceProvider();
		CalloutTranslator english = new CalloutTranslator(pers, false);
		Assert.assertEquals(english.translateVisual("Raidwide"), "Raidwide");

		CalloutTranslator japanese = new CalloutTranslator(pers, true);
		Assert.assertEquals(japanese.translateVisual("Raidwide"), "全体攻撃");
		japanese.getDisplayEnabled().set(false);
		Assert.assertEquals(japanese.translateVisual("Raidwide"), "Raidwide");
	}
}
