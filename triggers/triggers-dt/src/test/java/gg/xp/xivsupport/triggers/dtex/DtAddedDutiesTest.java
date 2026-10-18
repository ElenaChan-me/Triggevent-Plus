package gg.xp.xivsupport.triggers.dtex;

import gg.xp.reevent.events.EventDistributor;
import gg.xp.reevent.events.InitEvent;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutGroup;
import gg.xp.xivsupport.callouts.ModifiedCalloutRepository;
import gg.xp.xivsupport.sys.XivMain;
import gg.xp.xivsupport.triggers.dtun.ByakkoUn;
import gg.xp.xivsupport.triggers.dtun.SeiryuUn;
import gg.xp.xivsupport.triggers.dtun.ShinryuUn;
import gg.xp.xivsupport.triggers.dtun.SuzakuUn;
import gg.xp.xivsupport.triggers.dtun.TsukuyomiUn;
import org.picocontainer.MutablePicoContainer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * 戦闘ログが無いボスについて、少なくともクラスが読み込まれ、コールアウトが設定画面に出ることを確かめる。
 */
public class DtAddedDutiesTest {

	@Test
	public void addedDutiesAreRegistered() {
		MutablePicoContainer pico = XivMain.testingMasterInit();
		// 実際の起動と同じく、初期化イベントを流してからコールアウトの一覧を取る
		pico.getComponent(EventDistributor.class).acceptEvent(new InitEvent());
		List<CalloutGroup> groups = pico.getComponent(ModifiedCalloutRepository.class).getAllCallouts();
		Map<Class<?>, KnownDuty> expected = Map.ofEntries(
				Map.entry(DTEx3.class, KnownDuty.DtEx3),
				Map.entry(DTEx4.class, KnownDuty.DtEx4),
				Map.entry(DTEx5.class, KnownDuty.DtEx5),
				Map.entry(DTEx6.class, KnownDuty.DtEx6),
				Map.entry(DTEx7.class, KnownDuty.DtEx7),
				Map.entry(ByakkoUn.class, KnownDuty.ByakkoUn),
				Map.entry(SuzakuUn.class, KnownDuty.SuzakuUn),
				Map.entry(SeiryuUn.class, KnownDuty.SeiryuUn),
				Map.entry(TsukuyomiUn.class, KnownDuty.TsukuyomiUn),
				Map.entry(ShinryuUn.class, KnownDuty.ShinryuUn));
		expected.forEach((clazz, duty) -> {
			CalloutGroup group = groups.stream()
					.filter(g -> g.getCallClass().equals(clazz))
					.findFirst()
					.orElseThrow(() -> new AssertionError("Not registered: " + clazz.getSimpleName()));
			Assert.assertEquals(group.getDuty(), duty, clazz.getSimpleName());
			Assert.assertFalse(group.getCallouts().isEmpty(), clazz.getSimpleName());
			Assert.assertNotNull(pico.getComponent(clazz), clazz.getSimpleName());
		});
	}
}
