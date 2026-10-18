package gg.xp.xivsupport.triggers.cactbot;

import gg.xp.reevent.events.EventDistributor;
import gg.xp.reevent.events.InitEvent;
import gg.xp.xivsupport.callouts.CalloutGroup;
import gg.xp.xivsupport.callouts.ModifiedCalloutRepository;
import gg.xp.xivsupport.sys.XivMain;
import org.picocontainer.MutablePicoContainer;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * cactbot から移植したクラスは戦闘ログでの確認ができないので、
 * 少なくとも全部が起動時に読み込まれ、設定画面にコールアウトが出ることを確かめる。
 */
public class CactbotPortsLoadTest {

	// 移植したクラスの数。増減したときにテストで気付けるようにする
	private static final int EXPECTED_CLASSES = 247;

	@Test
	public void allPortsAreRegistered() {
		MutablePicoContainer pico = XivMain.testingMasterInit();
		pico.getComponent(EventDistributor.class).acceptEvent(new InitEvent());
		List<CalloutGroup> groups = pico.getComponent(ModifiedCalloutRepository.class).getAllCallouts().stream()
				.filter(g -> g.getCallClass().getPackageName().startsWith("gg.xp.xivsupport.triggers.cactbot"))
				.toList();
		Assert.assertEquals(groups.size(), EXPECTED_CLASSES);
		for (CalloutGroup group : groups) {
			String name = group.getCallClass().getSimpleName();
			Assert.assertNotNull(group.getDuty(), name);
			Assert.assertFalse(group.getCallouts().isEmpty(), name);
			Assert.assertNotNull(pico.getComponent(group.getCallClass()), name);
		}
	}
}
